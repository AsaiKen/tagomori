package net.katagaitai.tagomori.evm;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;

import java.util.Objects;

@RequiredArgsConstructor
public class Memory {
    @Getter
    private final BitVecExpr expr;

    public Memory(Context context) {
        expr = Z3Util.mkBV(context, 0, Constants.MEMORY_BITS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Memory memory1 = (Memory) o;
        return Objects.equals(Util.exprToString(expr), Util.exprToString(memory1.expr));
    }

    @Override
    public int hashCode() {
        return Objects.hash(Util.exprToString(expr));
    }

    public Memory copy(Context newContext) {
        return new Memory(Z3Util.translate(expr, newContext));
    }
}
