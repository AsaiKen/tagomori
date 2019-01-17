package net.katagaitai.tagomori.poc;

import lombok.*;

import java.math.BigInteger;
import java.util.List;

@RequiredArgsConstructor
@EqualsAndHashCode(of = {"sink"})
@ToString
public class PoC {
    @Getter
    private final PoCCategory category;
    @Getter
    private final Position sink;
    @Getter
    private final List<Payload> payloads;
    @Getter
    @Setter
    private BigInteger gain;
}
