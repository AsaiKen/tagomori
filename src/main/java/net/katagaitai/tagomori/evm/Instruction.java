package net.katagaitai.tagomori.evm;

import lombok.Value;
import org.ethereum.vm.OpCode;

@Value
public class Instruction {
    private int opValue;
    private OpCode opCode;
    private String argHex;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (opCode == null) {
            sb.append(String.format("INVALID(%02x)", opValue));
        } else {
            sb.append(opCode);
        }
        if (argHex != null && argHex.length() > 0) {
            sb.append(" 0x").append(argHex);
        }
        return sb.toString();
    }
}
