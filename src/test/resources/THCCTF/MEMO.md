compile
---

evm compile thcctf.easm


disassemble
---

evm disasm thcctf.evm

debgger
---

evm --debug --code $(evm compile sum.easm) run
evm --debug --codefile thcctf.evm run
