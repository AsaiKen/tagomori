package net.katagaitai.tagomori;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.util.Util;
import org.ethereum.core.Block;
import org.ethereum.core.BlockchainImpl;
import org.ethereum.core.Transaction;
import org.ethereum.db.RepositoryRoot;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.math.BigInteger;
import java.util.Arrays;

@Slf4j(topic = "tagomori")
@RequiredArgsConstructor
public class ContractCollector {
    private static final BigInteger MIN_ETHER = BigInteger.valueOf(10).pow(18);
    static final byte[] BLOCKNUMBER_KEY = {};

    private final EthereumImpl ethereum;
    private final RocksDB contractsDb;
    private final RocksDB blocknumberDb;

    public void collect() {
        log.debug("start");
        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
        RepositoryRoot repository = (RepositoryRoot) blockchain.getRepository();
        long latest = blockchain.getBestBlock().getNumber();
        long start = getBlockNumber();
        for (long number = start; number <= latest; number++) {
            if (Thread.interrupted()) {
                break;
            }
            Block block = blockchain.getBlockByNumber(number);
            if (block == null) {
                log.warn("no Block: {}/{}", number, latest);
                continue;
            }
            if (number == start || number % 10000 == 0) {
                log.info("Block: {}/{}", number, latest);
            }
            // coinbase
            checkAndPut(repository, block.getCoinbase(), number);
            for (Transaction tx : block.getTransactionsList()) {
                if (tx.isContractCreation()) {
                    // createされたcontract
                    checkAndPut(repository, tx.getContractAddress(), number);
                } else {
                    // toのcontract
                    checkAndPut(repository, tx.getReceiveAddress(), number);
                }
            }
            updateBlockNumber(number);
        }
        log.debug("end");
    }

    void updateBlockNumber(long n) {
        try {
            blocknumberDb.put(BLOCKNUMBER_KEY, ByteUtil.longToBytes(n));
        } catch (RocksDBException e) {
            log.error("", e);
        }
    }

    private long getBlockNumber() {
        try {
            byte[] b = blocknumberDb.get(BLOCKNUMBER_KEY);
            if (b == null) {
                return 0;
            }
            return ByteUtil.byteArrayToLong(b) + 1;
        } catch (RocksDBException e) {
            log.error("", e);
            return 0;
        }
    }

    private void checkAndPut(RepositoryRoot repository, byte[] address, long number) {
        if (check(repository, address)) {
            put(address, number);
        }
    }

    private boolean check(RepositoryRoot repository, byte[] address) {
        try {
            byte[] code = repository.getCode(address);
            if (code.length == 0) {
                return false;
            }
            BigInteger balance = repository.getBalance(address);
            if (balance.compareTo(MIN_ETHER) < 0) {
                return false;
            }
        } catch (Exception e) {
            log.debug("", e);
            return false;
        }
        return true;
    }

    private void put(byte[] address, long lastAccessed) {
        try {
            byte[] value = contractsDb.get(address);
            if (value == null) {
                value = new byte[Guardian.CONTRACT_VALUE_SIZE];
            }
            long oldLastAccessed = ByteUtil.byteArrayToLong(Arrays.copyOfRange(value, 0, 8));
            log.debug("{}, {}", oldLastAccessed, lastAccessed);
            if (oldLastAccessed < lastAccessed) {
                log.debug("put");
                System.arraycopy(ByteUtil.longToBytes(lastAccessed), 0, value, 0, 8);
                contractsDb.put(address, value);
            } else {
                log.debug("skip put");
            }
        } catch (RocksDBException e) {
            log.error("", e);
        }
    }
}
