# tagomori

tagomoriはEthereumスマートコントラクトのセキュリティ解析ツールです。

以下の特徴があります。

* ソースコードがなくても、スマートコントラクトのアドレスを知っていれば検査可能
* ETHの不正送金だけでなく、ERC20トークンの不正送金の脆弱性にも対応
* 複数のトランザクションを経由する脆弱性に対応
* 複数のスマートコントラクトを経由する脆弱性に対応
* リアルタイムのブロックチェーン上のデータを参照可能
* 検出した脆弱性をリアルタイムのブロックチェーン上でシミュレーションして誤検知を回避
* 脆弱性を検証するためのエクスプロイトを自動生成
* シンボリック実行技術を使用して誤検知を回避
* マルチコアを活用して高速に検査を実施

動作環境
-----

* Ubuntu 18.04 LTS
* openjdk 1.8系

ビルド
-----

以下のコマンドを実行してください。

```
$ sh build.sh
```

tagomori-1.0-SNAPSHOTディレクトリに実行ファイル群が作成されます。

実行
-----

tagomoriはコマンドラインツールです。以下のコマンドラインオプションがあります。

```
--help                -- このメッセージを表示します。
-sync                 -- ネットワークと同期した後に検査します。初めての場合、同期するまでに約1日がかかります。
-ropsten              -- ropstenネットワークを想定して検査します。
-mainnet              -- mainnetネットワークを想定して検査します。
-database <path>      -- ブロックチェーンのデータベースディレクトリのパスを指定します。存在しない場合、作成します。
-address <address>    -- このアドレスを検査します。 <address>には16進数文字列を指定してください。
-guardian             -- ネットワークと同期した後に、ネットワークとの同期と更新されたすべてのコントラクトへの検査を実行し続けます。
```

※オープンソース版では悪用を防ぐために-mainnetの機能を無効化しています。

例えば、

* Ropstenネットワークの
* アドレスA53514927D1a6a71f8075Ba3d04eb7379B04C588のコントラクトを
* リアルタイムのブロックチェーンを参照して
* ブロックチェーンのデータベースをdatabaseディレクトリに保存して

検査する場合は、以下のコマンドを実行してください。

```
$ LD_LIBRARY_PATH=z3/lib/ tagomori-1.0-SNAPSHOT/bin/tagomori -ropsten -address A53514927D1a6a71f8075Ba3d04eb7379B04C588 -sync -database database
```

また、例えば、

* Ropstenネットワークの
* すべての更新されたコントラクトを継続して
* ブロックチェーンのデータベースをdatabaseディレクトリに保存して

検査する場合は以下のコマンドを実行してください。

```
$ LD_LIBRARY_PATH=z3/lib/ tagomori-1.0-SNAPSHOT/bin/tagomori -ropsten -guardian -database database
```

脆弱性が存在する場合、resultディレクトリにjson形式でエクスプロイトが作成されます。

対応している脆弱性
---

* CALLによる不正送金
    * Reentrancyにも対応
* CALLCODE、DELEGATECALLによる、任意のコード実行を経由した不正送金
* SELFDESTRUCTによる不正送金
* ERC20トークンの不正送金

注意事項
---

* 自動生成されるエクスプロイトは、「攻撃者は、アドレス73E83E2Ab2ca3967db126F9534808C92320cbb90であり、残高が1ETH以上である」と仮定して作成されます。現時点では、このアドレスはハードコーディングされているため、このアドレスに異常が発生するとtagomoriが動作しない可能性があります。
* [z3](https://github.com/Z3Prover/z3)ライブラリの動作が不安定であるためjavaプロセスが異常終了する場合があります。その場合は、再度コマンドを実行してください。
* -guardianオプションは残高が1ETH以上のコントラクトを対象に検査を行います。

LICENSE
-----

tagomori is released under the LGPL-V3 license.
