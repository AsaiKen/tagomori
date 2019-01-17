import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.DbSettings;
import org.ethereum.datasource.rocksdb.RocksDbDataSource;
import org.ethereum.db.RepositoryImpl;
import org.ethereum.db.RepositoryRoot;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumFactory;
import org.ethereum.facade.Repository;
import org.ethereum.listener.EthereumListener;
import org.ethereum.solidity.compiler.CompilationResult;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.ethereum.sync.FastSyncManager.FASTSYNC_DB_KEY_PIVOT;
import static org.ethereum.sync.FastSyncManager.FASTSYNC_DB_KEY_SYNC_STAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("ALL")
public class MyTest {


    @Test
    public void testReadStroage() {
        Ethereum ethereum = EthereumFactory.createEthereum();
        System.out.println(ethereum.getBlockchain().getBestBlock().getNumber());
        Repository repository = ethereum.getLastRepositorySnapshot();
        byte[] dao = Hex.decode("BB9bc244D798123fDe783fCc1C72d3Bb8C189413");
        assertEquals("43080498003858225984", repository.getBalance(dao).toString());
        assertEquals("0000000000000000000000000000000000000000000000000000000000000132",
                repository.getStorageValue(dao, DataWord.of(0)).toString());
        assertEquals("0000000000000000000000000000000000000000000000000000000000000280",
                repository.getStorageValue(dao, DataWord.of(1)).toString());
        assertEquals("000000000000000000000000000000000000000000000000000000005b69d284",
                repository.getStorageValue(dao, DataWord.of(2)).toString());
        assertEquals("000000000000000000000000da4a4626d3e16e094de3225a751aab7128e96526",
                repository.getStorageValue(dao, DataWord.of(3)).toString());
        ethereum.close();
    }

    @Test
    public void testExecuteTransaction() throws IOException {
        // ethereum -> blockchain -> bestBlcok時のrepository
        Ethereum ethereum = EthereumFactory.createEthereum();
        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
        // bestBlock時のrepository
        RepositoryImpl repository = (RepositoryImpl) blockchain.getRepository();
        System.out.println(blockchain.getBestBlock().getNumber());

        // このアカウントは自分のアカウントであり、事前にmainchain上に作成しておく、という想定
        ECKey eckey = ECKey.fromPrivate(HashUtil.sha3("test".getBytes()));
        final byte[] senderAddress = eckey.getAddress();
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
                DataWord.of(repository.getBalance(senderAddress).longValue()).toString());
        repository.addBalance(senderAddress, new BigInteger(Integer.toString(0x3333)));
        assertEquals("0000000000000000000000000000000000000000000000000000000000003333",
                DataWord.of(repository.getBalance(senderAddress).longValue()).toString());

        RepositoryImpl repo2 = repository.startTracking();
        String contract =
                "pragma solidity ^0.4.3;" +
                        "contract Test {" +
                        "    uint i;" +
                        "    uint j;" +
                        "    function () payable {" +
                        "    }" +
                        "    function test() public {" +
                        "        i = 0x1111;" +
                        "        j = 0x2222;" +
                        "    }" +
                        "}";
        SolidityCompiler.Result res = SolidityCompiler.compile(
                contract.getBytes(), true, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);
        CompilationResult cres = CompilationResult.parse(res.output);

        // トランザクションでコントラクトを作成
        final CompilationResult.ContractMetadata metadata = cres.getContract("Test");
        Transaction tx = newTransaction(repo2, new byte[0], 0, Hex.decode(metadata.bin), eckey);
        executeTransaction(blockchain, repo2, tx);

        // トランザクションでコントラクトを呼び、ストレージを変更
        byte[] contractAddress = tx.getContractAddress();
        CallTransaction.Contract contract1 = new CallTransaction.Contract(metadata.abi);
        byte[] data2 = contract1.getByName("test").encode();
        Transaction tx2 = newTransaction(repo2, contractAddress, 0, data2, eckey);
        executeTransaction(blockchain, repo2, tx2);

        // トランザクションでコントラクトを呼び、残高を変更
        byte[] data3 = contract1.getByName("").encode();
        Transaction tx3 = newTransaction(repo2, contractAddress, 0x3333, data3, eckey);
        executeTransaction(blockchain, repo2, tx3);

        assertEquals("0000000000000000000000000000000000000000000000000000000000001111",
                repo2.getStorageValue(contractAddress, DataWord.of(0)).toString());
        assertEquals("0000000000000000000000000000000000000000000000000000000000002222",
                repo2.getStorageValue(contractAddress, DataWord.of(1)).toString());
        assertEquals("0000000000000000000000000000000000000000000000000000000000003333",
                DataWord.of(repo2.getBalance(contractAddress).longValue()).toString());
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
                DataWord.of(repo2.getBalance(senderAddress).longValue()).toString());
        repo2.rollback();

        // 残高とストレージが変更されていないことを確認
        assertNull(repository.getAccountState(contractAddress));
        assertEquals("0000000000000000000000000000000000000000000000000000000000003333",
                DataWord.of(repository.getBalance(senderAddress).longValue()).toString());

        ethereum.close();
    }

    private ProgramResult executeTransaction(BlockchainImpl blockchain, RepositoryImpl repo, Transaction tx) {
        RepositoryImpl track = repo.startTracking();
        TransactionExecutor executor = new TransactionExecutor(
                tx,
                new byte[32], // txを含むブロックのcoinbase
                repo,
                blockchain.getBlockStore(),
                blockchain.getProgramInvokeFactory(),
                new DummyBlock(blockchain.getBestBlock()) // txを含むブロック
        );
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
        track.commit();
        return executor.getResult();
    }

    private Transaction newTransaction(RepositoryImpl repo, byte[] receiverAddress, long value, byte[] data,
                                       ECKey eckey) {
        BigInteger nonce = repo.getNonce(eckey.getAddress());
        System.out.println(nonce);
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(5000000), // gas limit
                ByteUtil.longToBytesNoLeadZeroes(1_100_000_000), // gas price
                receiverAddress,
                ByteUtil.longToBytesNoLeadZeroes(value),
                data
        );
        tx.sign(eckey);
        return tx;
    }

    private class DummyBlock extends Block {
        public DummyBlock(Block block) {
            super(block.getEncoded());
        }

        public BlockHeader getHeader() {
            byte[] parentHash = getHash();
            long number = getNumber() + 1;
            long timestamp = System.currentTimeMillis() / 1000;
            return new BlockHeader(parentHash, getUnclesHash(), getCoinbase(),
                    getLogBloom(), getDifficulty(), number,
                    getGasLimit(), getGasUsed(), timestamp,
                    getExtraData(), getMixHash(), getNonce());
        }
    }

    @Test
    public void testSimpleToken() {
        byte[] address = HashUtil.calcNewAddr(Hex.decode("DC230B89A9201CFa17CDD07070CFf1c5bC51Ec36"), new byte[]{1});
        System.out.println(Hex.toHexString(address));
    }

//    @Test
//    public void testListContractsOrderByBalanceDesc() {
////        Ethereum ethereum = EthereumFactory.createEthereum();
////        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
////        RepositoryRoot repository = (RepositoryRoot) blockchain.getRepository();
////        System.out.println(blockchain.getBestBlock().getNumber());
////        byte[] root = repository.getRoot();
////        Source ss = repository.stateDS;
////        ethereum.close();
//        DbSettings settings = DbSettings.newInstance()
//                .withMaxOpenFiles(SystemProperties.getDefault().getConfig().getInt("database.maxOpenFiles"))
//                .withMaxThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
//        RocksDbDataSource dbSource = new RocksDbDataSource();
//        dbSource.setName("blockchain");
//        dbSource.init(settings);
//        Source ss = new XorDataSource<>(dbSource, HashUtil.sha3("state".getBytes()));
//        final byte[] root = Hex.decode("b71858e7d584e6e815f1e57b42a4a2ba3157775afc313ef231e34cbe702ff608");
//        final byte[] r = (byte[]) ss.get(root);
//        RLPList list = RLP.decode2(r);
//        Map<String, AccountState> map = Maps.newHashMap();
//        parse(list, map, ss, "");
//        dbSource.close();
//
//
//    }
//
//    private void parse(RLPList list, Map<String, AccountState> map, Source ss, String hex) {
//        if (list.get(0) instanceof RLPList) {
//            RLPList items = (RLPList) list.get(0);
//            if (items.size() == 17) {
//                for (int i = 0; i < 16; i++) {
//                    RLPItem item = (RLPItem) items.get(i);
//                    final byte[] rlpData = item.getRLPData();
//                    if (rlpData == null) {
//                        continue;
//                    }
//                    final String hex2 = String.format("%s%x", hex, i);
////                    System.out.println(String.format("%s %s", hex2, Hex.toHexString(rlpData)));
//                    byte[] b = (byte[]) ss.get(rlpData);
//                    RLPList list2 = RLP.decode2(b);
//                    parse(list2, map, ss, hex2);
//                }
//                RLPItem item = (RLPItem) items.get(16);
//                final byte[] rlpData = item.getRLPData();
//                if (rlpData != null) {
//                    throw new RuntimeException(String.format("%s %s", hex, list.toString()));
//                }
//            } else if (items.size() == 2) {
//                RLPItem item0 = (RLPItem) items.get(0);
//                final byte[] rlpData0 = item0.getRLPData();
//                final String key;
//                if ((rlpData0[0] >> 4 & 0x1) > 0) { // ODD LENGTH
//                    key = Hex.toHexString(rlpData0).substring(1);
//                } else {
//                    key = Hex.toHexString(rlpData0).substring(2);
//                }
//                final String hex2 = String.format("%s%s", hex, key);
//
//                RLPItem item1 = (RLPItem) items.get(1);
//                final byte[] rlpData1 = item1.getRLPData();
//                if ((rlpData0[0] >> 4 & 0x2) > 0) { // TERMINAL
//                    AccountState ac = new AccountState(rlpData1);
//                    System.out.println(String.format("%s %s", hex2, ac.getBalance()));
//                    assertEquals(64, hex2.length());
//                } else {
//                    byte[] b = (byte[]) ss.get(rlpData1);
//                    RLPList list2 = RLP.decode2(b);
//                    parse(list2, map, ss, hex2);
//                }
//            }
//        } else {
//            throw new RuntimeException(String.format("%s %s", hex, list.toString()));
//        }
//    }

    @Test
    public void testDownloadHistory() {
//        Ethereum ethereum = EthereumFactory.createEthereum();
//        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
//        RepositoryRoot repository = (RepositoryRoot) blockchain.getRepository();
//        System.out.println(blockchain.getBestBlock().getNumber());
//        System.out.println(Hex.toHexString(blockchain.getBestBlock().getHeader().getEncoded()));
//        ethereum.close();
        DbSettings settings = DbSettings.newInstance()
                .withMaxOpenFiles(SystemProperties.getDefault().getConfig().getInt("database.maxOpenFiles"))
                .withMaxThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        RocksDbDataSource dbSource = new RocksDbDataSource();
        dbSource.setName("blockchain");
        dbSource.init(settings);
        dbSource.put(FASTSYNC_DB_KEY_PIVOT,
                Hex.decode(
                        "f90215a073e26438f41f4af8da6c1261473f5beb288807bd14b0e7a7d4760ff37b0f18e0a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d4934794002e08000acbbae2155fab7ac01929564949070da0b71858e7d584e6e815f1e57b42a4a2ba3157775afc313ef231e34cbe702ff608a0c17107a422d119b5f65711c447b5042dad8d496784bb3f8cce23db714abdd7dba052d23876bcc3fa8010929d112a3e714e248f6ebd1fc5823e6137b233f3e4973eb90100080004421000186301010080700401000e204044000400000a111683448140080000204408a408620060200401280000c208000108080000350010005004140060000b00802000043250d00800809022000120000030800108090025044002604010000002c0500410300400802148001400044200c021864420183111000000030282020160070048c0048000100000000080600020211004000202000100040800003022040101180860000100080004080008420080101002c84800c000010091000a108e001420100005200080140a08240443105a210b404405420020000001210023400c00020418008402062800021810040030040a500119018044a0870ba14abae171808362b1d4837a121d8379f051845bb9807494457468657265756d534f4c4f2f326d696e657273a0b5943da0a098ee24d49caa02e1f0be959f04677ad962fdd99a6266185f64c93a88bd23c940039d2b44"));
        dbSource.put(FASTSYNC_DB_KEY_SYNC_STAGE, new byte[]{(byte) EthereumListener.SyncState.COMPLETE.ordinal()});
        dbSource.close();
    }

    // すべてのコントラクトを残高順に並べる。

    // 可能だが時間がかかりすぎるので妥協案を使う。
    // Blockのcoinbase、Transactionのto、Transactionでcreateされたcontract
    // codeの長さ>0
    // balanceが>0

    // 以下を作る
    // address -> balance
    // address -> lastBlocknumber
    @Test
    public void testListContractsOrderByBalanceDesc() throws InterruptedException {
        Ethereum ethereum = EthereumFactory.createEthereum();
        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
        RepositoryRoot repository = (RepositoryRoot) blockchain.getRepository();
        long n = blockchain.getBestBlock().getNumber();
        Map<String, Long> blocknumbers = Maps.newHashMap();
        long start = n - 1024;
        for (long i = start; i <= n; i++) {
            if (i % 1000 == 0) {
                System.out.println(String.format("%d/%d", i, n));
            }
            Block block = blockchain.getBlockByNumber(i);
            if (block == null) {
                System.out.println(String.format("%d is null", i));
                continue;
            }
            byte[] coinbase = block.getCoinbase();
            // coinbase
            blocknumbers.put(Hex.toHexString(coinbase), i);
            for (Transaction tx : block.getTransactionsList()) {
                if (tx.isContractCreation()) {
                    // createされたcontract
                    blocknumbers.put(Hex.toHexString(tx.getContractAddress()), i);
                } else {
                    // toのcontract
                    blocknumbers.put(Hex.toHexString(tx.getReceiveAddress()), i);
                }
            }
        }
        BigInteger zero = new BigInteger("0");
        BigInteger ether = new BigInteger("1000000000000000000");
        Map<String, BigInteger> balances = Maps.newHashMap();
        for (String hex : blocknumbers.keySet()) {
            try {
                final byte[] address = Hex.decode(hex);
                byte[] code = repository.getCode(address);
                if (code.length == 0) {
                    continue;
                }
                BigInteger balance = repository.getBalance(address);
                if (balance.divide(ether).compareTo(zero) == 0) {
                    continue;
                }
                balances.put(hex, balance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        SortedSet<Map.Entry<String, BigInteger>> set = getSortedByBalanceDesc(balances);
        int i = 1;
        for (Map.Entry<String, BigInteger> entry : set) {
            if (i > 100) {
                break;
            }
            System.out.println(String.format("%s,%s", entry.getKey(), entry.getValue().divide(ether).toString()));
            i++;
        }

        ethereum.close();
    }

    private SortedSet<Map.Entry<String, BigInteger>> getSortedByBalanceDesc(Map<String, BigInteger> balances) {
        SortedSet<Map.Entry<String, BigInteger>> set = new TreeSet<>(
                (e1, e2) -> e2.getValue().compareTo(e1.getValue())
        );
        set.addAll(balances.entrySet());
        return set;
    }

    @Test
    public void testTxDataSize() throws InterruptedException {
        Ethereum ethereum = EthereumFactory.createEthereum();
        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
        RepositoryRoot repository = (RepositoryRoot) blockchain.getRepository();
        long n = blockchain.getBestBlock().getNumber();
        List<Integer> sizes = Lists.newArrayList();
        long start = n - 10240;
        for (long i = start; i <= n; i++) {
            if (i % 1000 == 0) {
                System.out.println(String.format("%d/%d", i, n));
            }
            Block block = blockchain.getBlockByNumber(i);
            if (block == null) {
                System.out.println(String.format("%d is null", i));
                continue;
            }
            for (Transaction tx : block.getTransactionsList()) {
                if (tx.isContractCreation()) {
                    continue;
                }
                final byte[] data = tx.getData();
                int size;
                if (data == null) {
                    size = 0;
                } else {
                    size = data.length;
                }
                if (size >= 1000) {
                    continue;
                }
//                System.out.println(String.format("%4d: %s", size, Hex.toHexString(tx.getHash())));
                sizes.add(size);
            }
        }
        long sum = 0;
        for (int size : sizes) {
            sum += size;
        }
        long mean = sum / sizes.size();
        long mean_square_sum = 0;
        for (int size : sizes) {
            mean_square_sum += Math.pow((size - mean), 2);
        }
        double std = Math.sqrt(mean_square_sum / (sizes.size() - 1));
        System.out.println(mean); // 56
        System.out.println(std); // 104.25929215182693
        ethereum.close();
    }


    @Test
    public void testTxMemSize() throws InterruptedException {
        Ethereum ethereum = EthereumFactory.createEthereum();
        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
        RepositoryRoot repository = (RepositoryRoot) blockchain.getRepository();

        long n = blockchain.getBestBlock().getNumber();
        List<Integer> sizes = Lists.newArrayList();
        long start = n - 100;
        RepositoryImpl track =
                (RepositoryImpl) repository.getSnapshotTo(blockchain.getBlockByNumber(start).getStateRoot());
        for (long i = start; i <= n; i++) {
            if (i % 1000 == 0) {
                System.out.println(String.format("%d/%d", i, n));
            }
            Block block = blockchain.getBlockByNumber(i);
            if (block == null) {
                System.out.println(String.format("%d is null", i));
                continue;
            }
            for (Transaction tx : block.getTransactionsList()) {
                int size = executeTransactionAndReturnMemSize(blockchain, track, tx, block);
                if (size >= 0) {
                    if (size >= 1000) {
                        continue;
                    }
                    System.out.println(String.format("%4d: %s", size, Hex.toHexString(tx.getHash())));
                    sizes.add(size);
                }
            }
        }
        track.rollback();
        long sum = 0;
        for (int size : sizes) {
            sum += size;
        }
        long mean = sum / sizes.size();
        long mean_square_sum = 0;
        for (int size : sizes) {
            mean_square_sum += Math.pow((size - mean), 2);
        }
        double std = Math.sqrt(mean_square_sum / (sizes.size() - 1));
        System.out.println(mean); // 244
        System.out.println(std); // 195.74217736604444
        ethereum.close();
    }

    private int executeTransactionAndReturnMemSize(BlockchainImpl blockchain, RepositoryImpl repo, Transaction tx,
                                                   Block block) {
        RepositoryImpl track = repo.startTracking();
        TransactionExecutor executor = new TransactionExecutor(
                tx,
                new byte[32], // txを含むブロックのcoinbase
                repo,
                blockchain.getBlockStore(),
                blockchain.getProgramInvokeFactory(),
                block // txを含むブロック
        );
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
        track.commit();
//        if (executor.program != null) {
//            return executor.program.getMemSize();
//        }
        return -1;
    }

}
