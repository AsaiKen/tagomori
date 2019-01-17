package net.katagaitai.tagomori.evm;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;

import java.util.Objects;

@RequiredArgsConstructor
public class ReturnData {
    @Getter
    private final BitVecExpr expr;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReturnData that = (ReturnData) o;
        return Objects.equals(Util.exprToString(expr), Util.exprToString(that.expr));
    }

    @Override
    public int hashCode() {
        return Objects.hash(Util.exprToString(expr));
    }

    public ReturnData copy(Context newContext) {
        return new ReturnData(Z3Util.translate(expr, newContext));
    }

    @Override
    public String toString() {
        return "ReturnData{" +
                "expr=" + Util.exprToString(expr) +
                '}';
    }
}
