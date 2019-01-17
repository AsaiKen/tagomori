package net.katagaitai.tagomori;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.poc.Payload;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.poc.PoCCategory;
import net.katagaitai.tagomori.util.Constants;
import org.ethereum.core.*;
import org.ethereum.db.RepositoryImpl;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.ProgramResult;

import java.math.BigInteger;
import java.util.List;

@RequiredArgsConstructor
@Slf4j(topic = "tagomori")
public class Simulator {
    private final EthereumImpl ethereum;

    public BigInteger simulate(PoC poc) {
        log.debug("シミュレーション開始");
        // ethereum -> blockchain -> bestBlcok時のrepository
        BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
        RepositoryImpl repository = (RepositoryImpl) blockchain.getRepository();

        RepositoryImpl track = repository.startTracking();
        BigInteger beforeSenderBalance = track.getBalance(Constants.SENDER_ADDRESS);
        List<ProgramResult> results = Lists.newArrayList();
        for (Payload payload : poc.getPayloads()) {
            // ブロック毎に増減するので少なめに見積もっておく
            final BigInteger blockGasLimit = ByteUtil.bytesToBigInteger(blockchain.getBestBlock().getGasLimit())
                    .multiply(BigInteger.valueOf(9)).divide(BigInteger.valueOf(10));
            ProgramResult result = execute(blockchain, track, payload.getTo(), payload.getValue(), payload.getData(),
                    payload.getChainId(),
                    blockGasLimit);
            results.add(result);
            payload.setGas(blockGasLimit);
        }
        BigInteger afterSenderBalance = track.getBalance(Constants.SENDER_ADDRESS);
        track.rollback();

        if (poc.getCategory() == PoCCategory.ERC20) {
            BigInteger balance = ByteUtil.bytesToBigInteger(results.get(results.size() - 1).getHReturn());
            log.debug("ERC20残高: {}", balance);
            return balance.subtract(Constants.ERC20_MIN_BALANCE);
        } else {
            log.debug("事前ETH残高: {}", beforeSenderBalance);
            log.debug("事後ETH残高: {}", afterSenderBalance);
            return afterSenderBalance.subtract(beforeSenderBalance);
        }
    }

    private ProgramResult execute(BlockchainImpl blockchain, RepositoryImpl repository, String to, BigInteger value,
                                  String data, Integer chainId, BigInteger gasLimit) {
        final byte[] toBytes = ByteUtil.hexStringToBytes(to);
        final byte[] dataBytes = ByteUtil.hexStringToBytes(data);
        Transaction tx = newTransaction(
                repository,
                toBytes,
                value,
                dataBytes,
                chainId,
                gasLimit
        );
        return executeTransaction(blockchain, repository, tx);
    }


    private ProgramResult executeTransaction(BlockchainImpl blockchain, RepositoryImpl repo, Transaction tx) {
        RepositoryImpl track = repo.startTracking();
        final DummyBlock block = new DummyBlock(blockchain.getBestBlock());
        TransactionExecutor executor = new TransactionExecutor(
                tx,
                block.getCoinbase(), // txを含むブロックのcoinbase
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
        return executor.getResult();
    }

    private Transaction newTransaction(RepositoryImpl repo,
                                       byte[] to,
                                       BigInteger value,
                                       byte[] data,
                                       Integer chainId,
                                       BigInteger gasLimit) {
        BigInteger nonce = repo.getNonce(Constants.SENDER_ADDRESS);
        final long gasPrice = ethereum.getGasPrice();
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(gasPrice),
                ByteUtil.bigIntegerToBytes(gasLimit),
                to,
                ByteUtil.bigIntegerToBytes(value),
                data,
                chainId
        );
        tx.sign(Constants.SENDER_EC_KEY);
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
}
