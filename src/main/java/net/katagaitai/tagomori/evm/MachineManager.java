package net.katagaitai.tagomori.evm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.Simulator;
import net.katagaitai.tagomori.poc.Payload;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.poc.PoCCategory;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.BlockchainImpl;
import org.ethereum.db.RepositoryRoot;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j(topic = "tagomori")
public class MachineManager {
    private final EthereumImpl ethereum;
    private final ExecutorService executor;
    private final Simulator simulator;
    private final List<Future<?>> futures = Lists.newCopyOnWriteArrayList();
    private final Set<Integer> visitedMachineStates = Sets.newConcurrentHashSet();
    private final Set<PoC> pocs = Sets.newConcurrentHashSet();
    private final Set<String> dones = Sets.newConcurrentHashSet();

    @Setter
    private boolean skipSimulation = Constants.SKIP_SIMULATION;
    @Setter
    @Getter
    private int maxTransactionCount = Constants.MAX_TRANSACTION_COUNT;
    @Setter
    @Getter
    private int maxEdgeVisitedCount = Constants.MAX_EDGE_VISITED_COUNT;
    @Getter
    @Setter
    private boolean mappingKeyToZero = Constants.MAPPING_KEY_TO_ZERO;
    @Getter
    @Setter
    private boolean andArgToAddress = Constants.AND_ARG_TO_ADDRESS;
    @Getter
    @Setter
    private boolean longSizeDynamicArray = Constants.LONG_SIZE_DYNAMIC_ARRAY;
    @Setter
    private long timeoutMills = Constants.MANAGER_TIMEOUT_MILLS;
    @Getter
    private int exceptionCount;
    @Getter
    private boolean timeout = false;

    static {
        System.setProperty("java.library.path", "z3/lib");
        try {
            Field sys_paths = ClassLoader.class.getDeclaredField("sys_paths");
            sys_paths.setAccessible(true);
            sys_paths.set(null, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public MachineManager(EthereumImpl ethereum) {
        this.ethereum = ethereum;
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("machine-%d").build();
        executor = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE, namedThreadFactory);
        simulator = new Simulator(ethereum);
    }

    public void executeMachine(Machine machine) {
        try {
            Future<?> f = executor.submit(machine);
            futures.add(f);
        } catch (RejectedExecutionException e) {
            log.debug("タイムアウト済み", e);
        }
    }

    public void join() {
        ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> cancellerFuture = canceller.schedule(() -> {
            log.info("タイムアウト");
            executor.shutdownNow();
            futures.parallelStream().forEach(f -> f.cancel(true));
            timeout = true;
        }, timeoutMills, TimeUnit.MILLISECONDS);

        for (int i = 0; i < futures.size(); i++) {
            Future<?> f = futures.get(i);
            if (Thread.interrupted()) {
                log.debug("割り込み");
                f.cancel(true);
                continue;
            }
            try {
                f.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // 末尾に追加
                futures.add(f);
            } catch (CancellationException e) {
                log.debug("タイムアウト済み", e);
            } catch (InterruptedException e) {
                log.debug("割り込み", e);
            } catch (ExecutionException e) {
                log.error("", e);
                exceptionCount++;
            }
        }

        cancellerFuture.cancel(true);
        canceller.shutdownNow();
        executor.shutdownNow();
        try {
            canceller.awaitTermination(1, TimeUnit.MINUTES);
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.error("", e);
            exceptionCount++;
        }
    }

    public void addVisitedMachineState(MachineState state) {
        // 偽陽性を許容
        visitedMachineStates.add(state.hashCode());
    }

    public boolean isVisitedMachineState(MachineState state) {
        return visitedMachineStates.contains(state.hashCode());
    }

    public void addPoC(PoC poc) {
        if (pocs.contains(poc)) {
            log.debug("検知済みのPoC: {}", poc);
            return;
        }
        if (skipSimulation) {
            log.debug("シミュレーション省略: {}", poc);
        } else {
            // シミュレーションでpocをフィルタする
            simulate(poc);
            if (poc.getGain().compareTo(BigInteger.ZERO) > 0) {
                log.debug("シミュレーション成功: {}", poc);
                poc = tryShorten(poc);
                poc = tryRepeat(poc);
            } else {
                log.debug("シミュレーション失敗: {}", poc);
                return;
            }
        }
        if (pocs.add(poc)) {
            log.debug("検知: {}", poc);
            save(poc);
            dones.add(Util.addHexPrefix(poc.getSink().getTargetAddress()));
        }
    }

    private PoC tryRepeat(PoC poc) {
        if (poc.getCategory() == PoCCategory.ERC20) {
            // ガス代と引き換えに無価値のERC20を取得してしまう危険性があるため、1回に限定しておく
            return poc;
        }
        PoC bestPoc = poc;
        int i = 2;
        while (true) {
            PoC repeatPoc = repeat(poc, i++);
            simulate(repeatPoc);
            if (repeatPoc.getGain().compareTo(bestPoc.getGain()) > 0) {
                log.debug("繰り返し成功: {} {}", i, repeatPoc);
                bestPoc = repeatPoc;
            } else {
                break;
            }
        }
        return bestPoc;
    }

    private PoC repeat(PoC poc, int count) {
        List<Payload> newPayloads = Lists.newArrayList();
        for (int i = 0; i < count; i++) {
            newPayloads.addAll(poc.getPayloads());
        }
        return new PoC(poc.getCategory(), poc.getSink(), newPayloads);
    }

    private PoC tryShorten(PoC poc) {
        PoC shortPoc = shorten(poc);
        if (!poc.getPayloads().equals(shortPoc.getPayloads())) {
            simulate(shortPoc);
            if (shortPoc.getGain().compareTo(poc.getGain()) >= 0) {
                log.debug("短縮成功: {}", shortPoc);
                return shortPoc;
            }
        }
        return poc;
    }

    private PoC shorten(PoC poc) {
        List<Payload> newPayloads = Lists.newArrayList();
        for (Payload payload : poc.getPayloads()) {
            newPayloads.add(shorten(payload));
        }
        return new PoC(poc.getCategory(), poc.getSink(), newPayloads);
    }

    private Payload shorten(Payload payload) {
        String newData = payload.getData().replaceAll("(00)+$", "");
        return new Payload(payload.getTo(), payload.getValue(), newData, payload.getChainId(),
                payload.getGas());
    }

    private void simulate(PoC poc) {
        BigInteger gain = simulator.simulate(poc);
        poc.setGain(gain);
    }

    private void save(PoC poc) {
        ObjectMapper mapper = new ObjectMapper();
        String json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(poc);
        } catch (JsonProcessingException e) {
            log.error("", e);
            exceptionCount++;
            return;
        }
        File file = Util.getPoCFile(poc);
        final File parent = file.getParentFile();
        if (!parent.exists()) {
            boolean success = parent.mkdirs();
            if (!success) {
                log.error("ディレクトリの作成失敗: {}", parent);
                return;
            }
        }
        try {
            Files.write(file.toPath(), json.getBytes());
            log.info("PoCファイル: {}", file);
        } catch (IOException e) {
            log.error("", e);
            exceptionCount++;
            return;
        }
    }

    public Integer getChainId() {
        final long blockNumber = getBestBlockNumber();
        SystemProperties config = SystemProperties.getDefault();
        return config.getBlockchainConfig().getConfigForBlock(blockNumber).getChainId();
    }

    public long getBestBlockNumber() {
        return ethereum.getBlockchain().getBestBlock().getNumber();
    }

    public String getProxyAddressHex() {
        Integer chainId = getChainId();
        String addressHex;
        if (chainId == 1) {
            addressHex = Constants.MAINNET_PROXY_ADDRESS_HEX;
        } else if (chainId == 3) {
            addressHex = Constants.ROPSTEN_PROXY_ADDRESS_HEX;
        } else {
            throw new RuntimeException("proxy addressHex null");
        }
        if (getRepository().getCode(ByteUtil.hexStringToBytes(addressHex)).length == 0) {
            throw new RuntimeException("proxy code empty");
        }
        return addressHex;
    }

    public String getTransferlAddressHex() {
        Integer chainId = getChainId();
        String addressHex;
        if (chainId == 1) {
            addressHex = Constants.MAINNET_TRANSFER_ADDRESS_HEX;
        } else if (chainId == 3) {
            addressHex = Constants.ROPSTEN_TRANSFER_ADDRESS_HEX;
        } else {
            throw new RuntimeException("transfer addressHex null");
        }
        if (getRepository().getCode(ByteUtil.hexStringToBytes(addressHex)).length == 0) {
            throw new RuntimeException("transfer code empty");
        }
        return addressHex;
    }

    public boolean isDone(String targetAddressHex) {
        return dones.contains(Util.addHexPrefix(targetAddressHex));
    }

    public List<PoC> getPocs() {
        return Lists.newArrayList(pocs);
    }

    public List<PoC> execute(String addressHex) {
        if (Constants.EXCLUDE_CONTRACTS.contains(addressHex.toLowerCase())) {
            log.debug("除外コントラクト: {}", addressHex);
            return getPocs();
        }
        log.info("開始: {}", addressHex);
//        // 直接呼び出す条件での検査
//        Machine machine = newMachine(addressHex);
//        executeMachine(machine);
        // コントラクト経由で呼び出す条件での検査
        Machine machineUsingProxy = newMachineUsingProxy(addressHex);
        executeMachine(machineUsingProxy);
        join();
        log.info("終了: {}", addressHex);
        return getPocs();
    }

    Machine newMachine(String addressHex) {
        MachineState state = new MachineState(addressHex);
        state.setRepository(getRepository());
        return new Machine(this, state);
    }

    private Machine newMachineUsingProxy(String addressHex) {
        MachineState state = new MachineState(addressHex);
        state.setRepository(getRepository());
        String proxyAddressHex = getProxyAddressHex();
        state.setCallerAddressHex(proxyAddressHex);
        return new Machine(this, state);
    }

    private RepositoryRoot getRepository() {
        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
        return (RepositoryRoot) blockchain.getRepository();
    }

    public boolean isSuccess() {
        return exceptionCount == 0;
    }
}
