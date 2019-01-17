package net.katagaitai.tagomori;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.evm.MachineManager;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "tagomori")
public class Guardian {
    // last_accessed + last_executed + timeout
    public static final int CONTRACT_VALUE_SIZE = 8 + 8 + 1;

    private final EthereumImpl ethereum;
    private final RocksDB contractDb;
    private final RocksDB blocknumberDb;
    @Getter
    private final ContractCollector collector;


    public Guardian(NetworkType type, File database) throws IOException, InterruptedException {
        ethereum = Util.getEthereumImpl(type, true, database);
        contractDb = Util.getDb(Constants.CONTRACT_DB);
        blocknumberDb = Util.getDb(Constants.BLOCKNUMBER_DB);
        collector = new ContractCollector(ethereum, contractDb, blocknumberDb);
    }

    public void start() throws InterruptedException {
        final ExecutorService collectExecutor = Executors.newSingleThreadExecutor();
        collectExecutor.submit(() -> {
            while (!Thread.interrupted()) {
                collector.collect();
                try {
                    if (!Thread.interrupted()) {
                        Thread.sleep(60_000);
                    }
                } catch (InterruptedException e) {
                    log.error("", e);
                }
            }
        });

        final ExecutorService executeExecutor = Executors.newSingleThreadExecutor();
        executeExecutor.submit(() -> {
            while (!Thread.interrupted()) {
                final List<String> addressHexs = getAddressHexs();
                if (addressHexs.size() > 0) {
                    for (String addressHex : addressHexs) {
                        if (Thread.interrupted()) {
                            break;
                        }
                        execute(addressHex);
                    }
                } else {
                    try {
                        if (!Thread.interrupted()) {
                            Thread.sleep(60_000);
                        }
                    } catch (InterruptedException e) {
                        log.error("", e);
                    }
                }
            }
        });

        try {
            // ethereumjの死活監視
            long lastUpdateBlocknumber = 0;
            while (!Thread.interrupted()) {
                final long curBlocknumber = ethereum.getBlockchain().getBestBlock().getNumber();
                if (lastUpdateBlocknumber == curBlocknumber) {
                    // syncが止まっている場合
                    throw new RuntimeException("ethereumj異常停止");
                } else {
                    lastUpdateBlocknumber = curBlocknumber;
                }
                if (!Thread.interrupted()) {
                    Thread.sleep(180_000);
                }
            }
        } finally {
            collectExecutor.shutdownNow();
            executeExecutor.shutdownNow();
            collectExecutor.awaitTermination(1, TimeUnit.MINUTES);
            executeExecutor.awaitTermination(1, TimeUnit.MINUTES);
        }
    }

    List<String> getAddressHexs() {
        List<String> result = Lists.newArrayList();
        try (RocksIterator iterator = contractDb.newIterator()) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                final byte[] address = iterator.key();
                try {
                    byte[] value = contractDb.get(address);
                    long lastAccessed = ByteUtil.byteArrayToLong(Arrays.copyOfRange(value, 0, 8));
                    long lastExecuted = ByteUtil.byteArrayToLong(Arrays.copyOfRange(value, 8, 16));
                    byte timeout = value[16];
                    log.debug("{}, {}, {}", lastAccessed, lastExecuted, timeout);
                    if (lastExecuted < lastAccessed) {
                        log.debug("add");
                        result.add(ByteUtil.toHexString(address));
                    } else {
                        log.debug("skip add");
                    }
                } catch (RocksDBException e) {
                    log.error("", e);
                }
            }
        }
        Collections.sort(result);
//        result.sort(Comparator.comparing(s -> s.substring(40 - 2)));
        return result;
    }

    public MachineManager execute(String addressHex) {
        MachineManager manager = new MachineManager(ethereum);
        final long blockNumber = manager.getBestBlockNumber();
        manager.execute(addressHex);
        if (manager.isSuccess()) {
            byte[] address = ByteUtil.hexStringToBytes(addressHex);
            try {
                byte[] value = contractDb.get(address);
                final byte[] lastExecuted = ByteUtil.longToBytes(blockNumber);
                System.arraycopy(lastExecuted, 0, value, 8, 8);
                final byte timeout = (byte) (manager.isTimeout() ? 1 : 0);
                value[16] = timeout;
                contractDb.put(address, value);
            } catch (RocksDBException e) {
                log.error("", e);
            }
        }
        return manager;
    }

    public void close() {
        ethereum.close();
        blocknumberDb.close();
        contractDb.close();
    }
}
