# tagomori

Tagomori is a security analysis tool for Ethereum smart-contracts.

Tagomori has following features.

* not need the source-code, only need the address of the contract.
* detect unintended transfer of not only ETH but also ERC20 tokens.
* detect vulnerabilities that fired via multiple transactions.
* detect vulnerabilities that fired via multiple contracts.
* refer data of real-time blockchain.
* avoid false-positive by simulating detected vulnerabilities on real-time blockchain.
* generate exploits automatically to proof vulnerabilities.
* avoid false-positive by using "symbolic execution" technology.
* utilize multi-cores to speed up scans.

Environments
-----

* Ubuntu 18.04 LTS
* openjdk 1.8

Build
-----

Run the following command.

```
$ sh build.sh
```

This generates executable files in the "./tagomori-1.0-SNAPSHOT" diractory.

Execute
-----

Tagomori is a command-line tool, which has following command-line options.

```
--help                -- print this message
-sync                 -- if specified, start a scan after synchronize with Ethereum network. if first time, it takes about a day to finish synchronization.
-ropsten              -- if specified, scan for Ropsten network.
-mainnet              -- if specified, scan for Mainnet network.
-database <path>      -- specify the path of directory of EthereumJ blockchain database. if the directory does not exist, creates the directory.
-address <address>    -- specify the target contract address. <address> must be a hexadecimal string.
-guardian             -- if specified, keep synchronizing and scanning all the updated contracts in Ethereum network.
```

NOTICE: In open-source edition, "-mainnet" option is disabled to prevent misuses.

Examples
---

If you want to scan in the following conditions,

* scan for Ropsten network
* target contract address is A53514927D1a6a71f8075Ba3d04eb7379B04C588
* refer real-time blockchain
* use the "./database" directory for blockchain database

run the following command.

```
$ LD_LIBRARY_PATH=z3/lib/ tagomori-1.0-SNAPSHOT/bin/tagomori -ropsten -address A53514927D1a6a71f8075Ba3d04eb7379B04C588 -sync -database database
```

If you scan in the following conditions,

* scan for Ropsten network
* keep scanning all the updated contracts
* use the "./database" directory for blockchain database

run the following command.

```
$ LD_LIBRARY_PATH=z3/lib/ tagomori-1.0-SNAPSHOT/bin/tagomori -ropsten -guardian -database database
```

If vulnerabilities exist, tagomori generates exploits in the JSON format in the "./result" directory.

Supported Vulnerabilities
---

* unintended transfer of ETH by CALL instruction
    * includes "Reentrancy"
* unintended transfer of ETH via arbitrary code execution by both instructions of CALLCODE and DELEGATECALL
* unintended transfer of ETH by SELFDESTRUCT instruction
* unintended transfer of ERC20 token

NOTICE: Tagomori checks whether the balance of the attacker account can increase or not in simulation phase, and then concludes whether a vulnerability exists or not. So if the balance of the target contract is 0, tagomori does not detect any vulnerabilities. I recommend to set more than 1 ETH to the target contract.

Others
---

* At this time, tagomori generates Japanese text logs in most cases. Sorry.
* Auto-getnerated exploits assumes that the attacker account address is 73E83E2Ab2ca3967db126F9534808C92320cbb90 and the balance of this account is more than 1 ETH. At this time, this attacker address is hard-coded. So if this account has been modified abnormally, tagomori can not work well.
* Java binding of [z3](https://github.com/Z3Prover/z3) library is unstable and sometimes halt abnormally. In such case, run the same command again.
* When "-guardian" specified, tagomori only scan the contracts which balance is more than 1 ETH.

LICENSE
-----

tagomori is released under the LGPL-V3 license.
