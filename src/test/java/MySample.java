/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */

import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumFactory;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.net.eth.message.StatusMessage;
import org.ethereum.net.message.Message;
import org.ethereum.net.p2p.HelloMessage;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.Channel;
import org.ethereum.util.ByteUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.ethereum.util.ByteUtil.toHexString;

/**
 * Created by Anton Nashatyrev on 03.03.2016.
 */
public class MySample {
    protected Logger logger = LoggerFactory.getLogger("mysample");

    // ethereumをルートとしたツリーが出来上がる。
    protected Ethereum ethereum = EthereumFactory.createEthereum();
    protected SystemProperties config = SystemProperties.getDefault();

    private volatile AtomicLong txCount = new AtomicLong(0);
    private volatile AtomicLong gasSpent = new AtomicLong(0);

    /**
     * Use that sender key to sign transactions
     */
    private final byte[] senderPrivateKey = Hex.decode("116a13830233f30d35464166566dd6fc3906bfe62878efe20c60ca73e6ff0ca5");
    // sender address is derived from the private key
    private final byte[] senderAddress = ECKey.fromPrivate(senderPrivateKey).getAddress();

    private Map<ByteArrayWrapper, TransactionReceipt> txWaiters =
            Collections.synchronizedMap(new HashMap<>());


    private TransactionReceipt sendTxAndWait(byte[] receiveAddress, byte[] data) throws InterruptedException {
        BigInteger nonce = ethereum.getRepository().getNonce(senderAddress);
        @SuppressWarnings("deprecation") Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(ethereum.getGasPrice()),
                ByteUtil.longToBytesNoLeadZeroes(3_000_000),
                receiveAddress,
                ByteUtil.longToBytesNoLeadZeroes(0),
                data,
                ethereum.getChainIdForNextBlock());
        tx.sign(ECKey.fromPrivate(senderPrivateKey));
        logger.info("<=== Sending transaction: " + tx);
        ethereum.submitTransaction(tx);

        return waitForTx(tx.getHash());
    }

    private TransactionReceipt waitForTx(byte[] txHash) throws InterruptedException {
        ByteArrayWrapper txHashW = new ByteArrayWrapper(txHash);
        txWaiters.put(txHashW, null);
        long startBlock = ethereum.getBlockchain().getBestBlock().getNumber();
        while (true) {
            TransactionReceipt receipt = txWaiters.get(txHashW);
            if (receipt != null) {
                return receipt;
            } else {
                long curBlock = ethereum.getBlockchain().getBestBlock().getNumber();
                if (curBlock > startBlock + 16) {
                    throw new RuntimeException("The transaction was not included during last 16 blocks: " + txHashW.toString().substring(0, 8));
                } else {
                    logger.info("Waiting for block with transaction 0x" + txHashW.toString().substring(0, 8) +
                            " included (" + (curBlock - startBlock) + " blocks received so far) ...");
                }
            }
            synchronized (this) {
                this.wait(20000); // waitリストに入る。そしてmonitorを返す。
                // 実行可能状態になるとmonitorを取得する。そして"}"でmonitorを返す。
            }
        }
    }

//    public static void main(String[] args) throws Exception {
//        sLogger.info("Starting EthereumJ!");
//
//        class Config {
//            @Bean
//            public BasicSample sampleBean() {
//                return new MySample();
//            }
//        }
//
//        // Based on Config class the BasicSample would be created by Spring
//        // and its springInit() method would be called as an entry point
//        EthereumFactory.createEthereum(Config.class);
//    }

    private EthereumListener listener = new EthereumListenerAdapter() {
        @Override
        public void onSyncDone(SyncState state) {
            synced = true;
        }

        @Override
        public void onNodeDiscovered(Node node) {
            if (nodesDiscovered.size() < 1000) {
                nodesDiscovered.add(node);
            }
        }

        @Override
        public void onEthStatusUpdated(Channel channel, StatusMessage statusMessage) {
            ethNodes.put(channel.getNode(), statusMessage);
        }

        @Override
        public void onPeerAddedToSyncPool(Channel peer) {
            syncPeers.add(peer.getNode());
        }

        @Override
        public void onBlock(Block block, List<TransactionReceipt> receipts) {
            bestBlock = block;
            txCount.addAndGet(receipts.size());
            for (TransactionReceipt receipt : receipts) {
                gasSpent.addAndGet(ByteUtil.byteArrayToLong(receipt.getGasUsed()));
            }
            if (syncComplete) {
                logger.info("New block: " + block.getShortDescr());
            }
        }

        @Override
        public void onRecvMessage(Channel channel, Message message) {
        }

        @Override
        public void onSendMessage(Channel channel, Message message) {
        }

        @Override
        public void onPeerDisconnect(String host, long port) {
        }

        @Override
        public void onPendingTransactionsReceived(List<Transaction> transactions) {
        }

        @Override
        public void onPendingStateChanged(PendingState pendingState) {
        }

        @Override
        public void onHandShakePeer(Channel channel, HelloMessage helloMessage) {
        }

        @Override
        public void onNoConnections() {
        }

        @Override
        public void onVMTraceCreated(String transactionHash, String trace) {
        }

        @Override
        public void onTransactionExecuted(TransactionExecutionSummary summary) {
        }
    };

    private List<Node> nodesDiscovered = new Vector<>();

    /**
     * Waits until any new nodes are discovered by the UDP discovery protocol
     */
    private void waitForDiscovery() throws Exception {
        logger.info("Waiting for nodes discovery...");

        int bootNodes = config.peerDiscoveryIPList().size();
        int cnt = 0;
        while (true) {
            Thread.sleep(cnt < 30 ? 300 : 5000);

            if (nodesDiscovered.size() > bootNodes) {
                logger.info("[v] Discovery works, new nodes started being discovered.");
                return;
            }

            if (cnt >= 30) logger.warn("Discovery keeps silence. Waiting more...");
            if (cnt > 50) {
                logger.error("Looks like discovery failed, no nodes were found.\n" +
                        "Please check your Firewall/NAT UDP protocol settings.\n" +
                        "Your IP interface was detected as " + config.bindIp() + ", please check " +
                        "if this interface is correct, otherwise set it manually via 'peer.discovery.bind.ip' option.");
                throw new RuntimeException("Discovery failed.");
            }
            cnt++;
        }
    }

    private Map<Node, StatusMessage> ethNodes = new Hashtable<>();

    /**
     * Discovering nodes is only the first step. No we need to find among discovered nodes
     * those ones which are live, accepting inbound connections, and has compatible subprotocol versions
     */
    private void waitForAvailablePeers() throws Exception {
        logger.info("Waiting for available Eth capable nodes...");
        int cnt = 0;
        while (true) {
            Thread.sleep(cnt < 30 ? 1000 : 5000);

            if (ethNodes.size() > 0) {
                logger.info("[v] Available Eth nodes found.");
                return;
            }

            if (cnt >= 30) logger.info("No Eth nodes found so far. Keep searching...");
            if (cnt > 60) {
                logger.error("No eth capable nodes found. Logs need to be investigated.");
//                throw new RuntimeException("Eth nodes failed.");
            }
            cnt++;
        }
    }

    private List<Node> syncPeers = new Vector<>();

    /**
     * When live nodes found SyncManager should select from them the most
     * suitable and add them as peers for syncing the blocks
     */
    private void waitForSyncPeers() throws Exception {
        logger.info("Searching for peers to sync with...");
        int cnt = 0;
        while (true) {
            Thread.sleep(cnt < 30 ? 1000 : 5000);

            if (syncPeers.size() > 0) {
                logger.info("[v] At least one sync peer found.");
                return;
            }

            if (cnt >= 30) logger.info("No sync peers found so far. Keep searching...");
            if (cnt > 60) {
                logger.error("No sync peers found. Logs need to be investigated.");
//                throw new RuntimeException("Sync peers failed.");
            }
            cnt++;
        }
    }

    protected Block bestBlock = null;

    /**
     * Waits until blocks import started
     */
    private void waitForFirstBlock() throws Exception {
        Block currentBest = ethereum.getBlockchain().getBestBlock();
        logger.info("Current BEST block: " + currentBest.getShortDescr());
        logger.info("Waiting for blocks start importing (may take a while)...");
        int cnt = 0;
        while (true) {
            Thread.sleep(cnt < 300 ? 1000 : 60000);

            if (bestBlock != null && bestBlock.getNumber() > currentBest.getNumber()) {
                logger.info("[v] Blocks import started.");
                return;
            }

            if (cnt >= 300) logger.info("Still no blocks. Be patient...");
            if (cnt > 330) {
                logger.error("No blocks imported during a long period. Must be a problem, logs need to be investigated.");
//                throw new RuntimeException("Block import failed.");
            }
            cnt++;
        }
    }

    private boolean synced = false;
    private boolean syncComplete = false;

    /**
     * Waits until the whole blockchain sync is complete
     */
    private void waitForSync() throws Exception {
        logger.info("Waiting for the whole blockchain sync (will take up to several hours for the whole chain)...");
        while (true) {
            Thread.sleep(10000);

            if (synced) {
                logger.info("[v] Sync complete! The best block: " + bestBlock.getShortDescr());
                syncComplete = true;
                return;
            }

            logger.info("Blockchain sync in progress. Last imported block: " + bestBlock.getShortDescr() +
                    " (Total: txs: " + txCount.get() + ", gas: " + (gasSpent.get() / 1000) + "k)");
            txCount.set(0);
            gasSpent.set(0);
        }
    }

    private void onSyncDone() throws Exception {
        ethereum.addListener(new EthereumListenerAdapter() {
            // when block arrives look for our included transactions
            @Override
            public void onBlock(Block block, List<TransactionReceipt> receipts) {
                // 別スレッドで実行される。
                // txWaitersにputしてthis.notifyAll
                MySample.this.onBlock(block, receipts);
            }
        });

        logger.info("Sending contract to net and waiting for inclusion");
        // トランザクションを送信してthis.wait
        TransactionReceipt receipt = sendTxAndWait(new byte[0],
                Hex.decode("7f604260005260326000f300000000000000000000000000000000000000000000600052600a6000f3"));

        if (!receipt.isSuccessful()) {
            logger.error("Some troubles creating a contract: " + receipt.getError());
            return;
        }

        byte[] contractAddress = receipt.getTransaction().getContractAddress();
        logger.info("Contract created: " + toHexString(contractAddress));
    }


    private void onBlock(Block block, List<TransactionReceipt> receipts) {
        for (TransactionReceipt receipt : receipts) {
            ByteArrayWrapper txHashW = new ByteArrayWrapper(receipt.getTransaction().getHash());
            if (txWaiters.containsKey(txHashW)) {
                txWaiters.put(txHashW, receipt);
                synchronized (this) {
                    this.notifyAll(); // thisのwaitリストを実行可能状態にする。すぐに抜けて"}"でmonitorを返す。
                }
            }
        }
    }


    @Test
    public void test() {
        ethereum.addListener(listener);

        try {
            if (config.peerDiscovery()) {
                waitForDiscovery();
            } else {
                logger.info("Peer discovery disabled. We should actively connect to another peers or wait for incoming connections");
            }

            waitForAvailablePeers();
            waitForSyncPeers();
            waitForFirstBlock();
            waitForSync();
            // syncしたらトランザクションを送信
            onSyncDone();

        } catch (Exception e) {
            logger.error("Error occurred in Sample: ", e);
        }
    }

}
