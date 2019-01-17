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
public class Data {
    @Getter
    private final BitVecExpr expr;

    public Data(Context context, int count) {
        expr = Z3Util.mkBVConst(context, "DATA" + count, Constants.DATA_BITS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Data data1 = (Data) o;
        return Objects.equals(Util.exprToString(expr), Util.exprToString(data1.expr));
    }

    @Override
    public int hashCode() {
        return Objects.hash(Util.exprToString(expr));
    }

    public Data copy(Context newContext) {
        return new Data(Z3Util.translate(expr, newContext));
    }

    @Override
    public String toString() {
        return "Data{" +
                "expr=" + Util.exprToString(expr) +
                '}';
    }
}
