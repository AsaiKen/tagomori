package net.katagaitai.tagomori;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.util.Util;
import org.apache.commons.io.IOUtils;
import org.ethereum.core.BlockSummary;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.facade.SyncStatus;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.EthereumListenerAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j(topic = "tagomori")
@RequiredArgsConstructor
public class EthereumImplFactory {
    private final NetworkType type;
    private final boolean sync;
    private final File database;

    private boolean syncStarted;
    private boolean syncDone;

    public EthereumImpl getEthereumImpl() throws IOException, InterruptedException {
        // configディレクトリを作成
        File configDir = new File(System.getProperty("user.dir"), "config");
        if (!configDir.exists()) {
            boolean success = configDir.mkdir();
            if (!success) {
                throw new RuntimeException("ディレクトリの作成失敗 :" + configDir);
            }
        }

        // ethereumj.confを削除
        File confFile = new File(configDir, "ethereumj.conf");
        if (confFile.exists()) {
            boolean success = confFile.delete();
            if (!success) {
                throw new RuntimeException("ファイルの削除失敗: " + confFile);
            }
        }

        // ethereumj.confを作成
        String name;
        if (type == NetworkType.Ropsten) {
            name = sync ? "ropsten_sync.conf" : "ropsten.conf";
        } else if (type == NetworkType.Mainnet) {
            name = sync ? "mainnet_sync.conf" : "mainnet.conf";
        } else {
            throw new RuntimeException("不明なtype: " + type);
        }
        byte[] bytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream(name));
        String confStr = new String(bytes);
        confStr = confStr.replace("${DATABASE}", database.getAbsolutePath());
        Files.write(confFile.toPath(), confStr.getBytes());

        // ethereumを起動
        Ethereum ethereum = org.ethereum.facade.EthereumFactory.createEthereum();
        if (sync) {
            log.info("同期開始");
            EthereumListener listener = new EthereumListenerAdapter() {
                @Override
                public void onBlock(BlockSummary summary) {
                    log.info("ブロック番号: {}", summary.getBlock().getNumber());
                    syncStarted = true;
                }

                @Override
                public void onSyncDone(SyncState state) {
                    syncDone = true;
                }
            };
            ethereum.addListener(listener);
            // 同期するまで待つ
            long lastUpdateBlocknumber = 0;
            while (!(syncDone && ethereum.getSyncStatus().getStage() == SyncStatus.SyncStage.Complete)) {
                if (syncStarted) {
                    // ethereumjの死活監視
                    final long curBlocknumber = ethereum.getBlockchain().getBestBlock().getNumber();
                    if (lastUpdateBlocknumber == curBlocknumber) {
                        // syncが止まっている場合
                        throw new RuntimeException("ethereumj異常停止");
                    } else {
                        lastUpdateBlocknumber = curBlocknumber;
                    }
                }
                Thread.sleep(180_000);
            }
            log.info("同期完了");
        }
        return (EthereumImpl) ethereum;
    }

}
