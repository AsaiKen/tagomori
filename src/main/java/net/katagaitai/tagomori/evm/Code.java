package net.katagaitai.tagomori.evm;

import com.google.common.collect.Maps;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.OpCode;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j(topic = "tagomori")
@EqualsAndHashCode(of = {"hex"})
public class Code {
    @Getter
    private final String hex;
    private final Map<Integer, Instruction> offsetToInstruction = Maps.newHashMap();

    public Code(byte[] bytes) {
        this.hex = ByteUtil.toHexString(bytes);
        parse(bytes);
    }

    private void parse(byte[] bytes) {
        int offset = 0;
        while (offset < bytes.length) {
            offset = parseOne(bytes, offset);
        }
    }

    private int parseOne(byte[] bytes, int offset) {
        final byte b = bytes[offset];
        OpCode opCode = OpCode.code(b);
        if (opCode == null) {
            final Instruction instruction = new Instruction(b & 0xff, null, null);
            offsetToInstruction.put(offset, instruction);
            log.trace("{} {}", offset, instruction);
            return offset + 1;
        }
        byte[] arg = new byte[0];
        int i = b & 0xff;
        if (0x60 <= i && i <= 0x7f) {
            int size = i - 0x5f;
            if (size > bytes.length - (offset + 1)) {
                final Instruction instruction = new Instruction(b & 0xff, null, null);
                offsetToInstruction.put(offset, instruction);
                log.trace("{} {}", offset, instruction);
                return bytes.length;
            }
            byte[] a = new byte[size];
            System.arraycopy(bytes, offset + 1, a, 0, size);
            arg = a;
        }
        final Instruction instruction = new Instruction(b & 0xff, opCode, ByteUtil.toHexString(arg));
        offsetToInstruction.put(offset, instruction);
        log.trace("{} {}", offset, instruction);
        return offset + 1 + arg.length;
    }

    public Instruction getInstruction(int pc) {
        return offsetToInstruction.get(pc);
    }

    public Set<OpCode> getOpCodes() {
        return offsetToInstruction.values().stream().map(Instruction::getOpCode).collect(Collectors.toSet());
    }
}
