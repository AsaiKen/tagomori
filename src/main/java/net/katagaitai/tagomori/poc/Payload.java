package net.katagaitai.tagomori.poc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.katagaitai.tagomori.util.Util;

import java.math.BigInteger;

@EqualsAndHashCode
@ToString
public class Payload {
    @Getter
    private final String to;
    @Getter
    private final BigInteger value;
    @Getter
    private final String data;
    @Getter
    private final Integer chainId;
    @Getter
    @Setter
    private BigInteger gas;

    public Payload(String to, BigInteger value, String data, Integer chainId, BigInteger gas) {
        // web3でそのまま使えるように0xをつける
        this.to = Util.addHexPrefix(to);
        this.value = value;
        this.data = Util.addHexPrefix(data);
        this.chainId = chainId;
        this.gas = gas;
    }
}
