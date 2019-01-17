package net.katagaitai.tagomori.evm;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;

import java.util.Objects;

@RequiredArgsConstructor
public class Value {
    @Getter
    private final BitVecExpr expr;

    public Value(Context context, int count) {
        expr = Z3Util.mkBVConst(context, "VALUE" + count, 256);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Value value = (Value) o;
        return Objects.equals(Util.exprToString(expr), Util.exprToString(value.expr));
    }

    @Override
    public int hashCode() {
        return Objects.hash(Util.exprToString(expr));
    }

    public Value copy(Context newContext) {
        return new Value(Z3Util.translate(expr, newContext));
    }

    @Override
    public String toString() {
        return "Value{" +
                "expr=" + Util.exprToString(expr) +
                '}';
    }
}
