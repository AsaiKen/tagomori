package net.katagaitai.tagomori.evm;

import com.google.common.collect.Lists;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import net.katagaitai.tagomori.util.Z3Util;

import java.util.LinkedList;
import java.util.Objects;

public class Stack {
    private final LinkedList<BitVecExpr> stack = Lists.newLinkedList();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stack stack1 = (Stack) o;
        return Objects.equals(stack.toString(), stack1.stack.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack.toString());
    }

    public Stack copy(Context newContext) {
        Stack copy = new Stack();
        for (BitVecExpr expr : stack) {
            copy.stack.addLast(Z3Util.translate(expr, newContext));
        }
        return copy;
    }

    public BitVecExpr pop() {
        return stack.pop();
    }

    public void push(BitVecExpr expr) {
        if (expr == null) {
            throw new RuntimeException("expr null");
        }
        stack.push(expr);
    }

    public int size() {
        return stack.size();
    }

    public BitVecExpr get(int i) {
        return stack.get(i);
    }

    public void set(int i, BitVecExpr b) {
        stack.set(i, b);
    }
}
