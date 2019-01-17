package net.katagaitai.tagomori.poc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.katagaitai.tagomori.util.Util;

@EqualsAndHashCode
@ToString
public class Position {
    @Getter
    private final String targetAddress;
    @Getter
    private final String contextAddress;
    @Getter
    private final String codeAddress;
    @Getter
    private final int pc;

    public Position(String targetAddressHex, String contextAddressHex, String codeAddressHex, int pc) {
        // web3でそのまま使えるように0xをつける
        this.targetAddress = Util.addHexPrefix(targetAddressHex);
        this.contextAddress = Util.addHexPrefix(contextAddressHex);
        this.codeAddress = Util.addHexPrefix(codeAddressHex);
        this.pc = pc;
    }

}
