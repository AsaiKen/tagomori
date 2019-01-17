package net.katagaitai.tagomori;

import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.evm.MachineManager;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.util.Util;
import org.ethereum.core.BlockchainImpl;
import org.ethereum.db.RepositoryImpl;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;

import java.io.File;
import java.util.List;

@Slf4j(topic = "tagomori")
public class Start {

    public static void main(String[] args) {
        NetworkType type = null;
        boolean sync = false;
        File database = null;
        String addressHex = null;
        boolean guardian = false;

        // コマンドライン引数のチェック
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--help")) {
                printHelp();
                System.exit(1);
            } else if (arg.equals("-sync")) {
                sync = true;
            } else if (arg.equals("-ropsten")) {
                type = NetworkType.Ropsten;
            } else if (arg.equals("-mainnet")) {
//                type = NetworkType.Mainnet;
                System.out.println("オープンソース版では-mainnetの利用を禁止しています。");
                System.exit(1);
            } else if (arg.equals("-database")) {
                if (i == args.length - 1) {
                    System.out.println("-databaseの後にパスを指定してください。");
                    System.exit(1);
                } else {
                    database = new File(args[i + 1]);
                    i++;
                }
            } else if (arg.equals("-address")) {
                if (i == args.length - 1) {
                    System.out.println("-addressの後にアドレスを指定してください。");
                    System.exit(1);
                } else if (!args[i + 1].matches("[0-9a-fA-f]{40}")) {
                    System.out.println("\"" + args[i + 1] + "\"は不正なアドレスです。正しいアドレスを指定してください。");
                    System.exit(1);
                } else {
                    addressHex = args[i + 1];
                    i++;
                }
            } else if (arg.equals("-guardian")) {
                guardian = true;
            }
        }

        if (type == null) {
            System.out.println("-ropstenと-mainnetのどちらか一方を指定してください。");
            System.exit(1);
        }
        if (database == null) {
            System.out.println("-databaseを指定してください。");
            System.exit(1);
        }
        if (!guardian && addressHex == null) {
            System.out.println("-addressを指定してください。");
            System.exit(1);
        }
        if (!database.exists()) {
            if (database.mkdirs()) {
                System.out.println(database + "を作成しました。");
            } else {
                System.out.println(database + "を作成できません。正しいパスを指定してください。");
                System.exit(1);
            }
        }
        if (!database.canWrite()) {
            System.out.println(database + "に書き込むことができません。正しいパスを指定してください。");
            System.exit(1);
        }

        try {
            if (guardian) {
                final Guardian guardian1 = new Guardian(type, database);
                try {
                    guardian1.start();
                } finally {
                    guardian1.close();
                }
            } else {
                EthereumImpl ethereum = Util.getEthereumImpl(type, sync, database);
                try {
                    BlockchainImpl blockchain = (BlockchainImpl) ethereum.getBlockchain();
                    RepositoryImpl repository = (RepositoryImpl) blockchain.getRepository();
                    final byte[] address = ByteUtil.hexStringToBytes(addressHex);
                    if (repository.getCode(address).length == 0) {
                        final long blocknumber = blockchain.getBestBlock().getNumber();
                        System.out.println(
                                String.format(
                                        "\"%s\"はバイトコードが存在しません。正しいアドレスを指定する、または-syncを実行してください。（同期済みのブロック番号: %d）",
                                        addressHex, blocknumber));
                        System.exit(1);
                    }

                    MachineManager manager = new MachineManager(ethereum);
                    List<PoC> pocs = manager.execute(addressHex);
                    System.out.println(pocs.size() + "件の問題が見つかりました。");
                    for (PoC poc : pocs) {
                        System.out.println(Util.getPoCFile(poc));
                    }
                } finally {
                    Util.closeEthereum(ethereum);
                }
            }
        } catch (Exception e) {
            log.error("", e);
            System.exit(1);
        } finally {
            System.exit(0);
        }
    }

    static void printHelp() {
        System.out.println("--help                -- このメッセージを表示します。");
        System.out.println("-ropsten              -- ropstenネットワークを想定して検査します。");
        System.out.println("-mainnet              -- mainnetネットワークを想定して検査します。");
        System.out.println("-sync                 -- ネットワークと同期した後に検査します。初めての場合、同期するまでに約1日がかかります。");
        System.out.println("-database <path>      -- ブロックチェーンのデータベースディレクトリのパスを指定します。存在しない場合、作成します。");
        System.out.println("-address <address>    -- このアドレスを検査します。 <address>には16進数文字列を指定してください。");
        System.out.println("-guardian             -- リアルタイムのブロックチェーンを監視して、更新されたコントラクトをすべて検査します。");
        System.out.println("e.g: cli -ropsten -sync -database database -address A53514927D1a6a71f8075Ba3d04eb7379B04C588");
        System.out.println();
    }

}
