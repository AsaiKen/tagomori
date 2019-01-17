package net.katagaitai.tagomori.util;

import com.microsoft.z3.*;
import net.katagaitai.tagomori.evm.Instruction;
import net.katagaitai.tagomori.evm.MachineState;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.vm.OpCode;

public class Z3Util {
    public static BitVecExpr translate(BitVecExpr value, Context newContext) {
        if (value == null) {
            return null;
        }
        return (BitVecExpr) value.translate(newContext);
    }

    public static Solver translate(Solver solver, Context newContext) {
        Solver newSolver = solver.translate(newContext);
        setParameters(newContext, newSolver);
        return newSolver;
    }

    public static BitVecExpr mkBVConst(Context context, String name, int bits) {
        if (StringUtils.isNumeric(name)) {
            throw new RuntimeException("異常なシンボル: " + name);
        }
        return context.mkBVConst(name, bits);
    }

    public static BitVecExpr mkZeroExt(Context context, int size, BitVecExpr expr) {
        return context.mkZeroExt(size, expr);
    }

    public static BitVecNum mkBV(Context context, int i, int bits) {
        return context.mkBV(i, bits);
    }

    public static BitVecNum mkBV(Context context, String i_str, int bits) {
        if (!StringUtils.isNumeric(i_str)) {
            throw new RuntimeException("異常な数値文字列: " + i_str);
        }
        return context.mkBV(i_str, bits);
    }

    public static BitVecExpr mkBVMul(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVMul(a, b);
    }

    public static BitVecExpr mkBVAdd(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVAdd(a, b);
    }

    public static BitVecExpr mkITE(Context context, BoolExpr condition, BitVecExpr trueExpr,
                                   BitVecExpr falseExpr) {
        return (BitVecExpr) context.mkITE(condition, trueExpr, falseExpr);
    }

    public static BoolExpr mkBVUGT(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVUGT(a, b);
    }

    public static BitVecExpr mkBVSub(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVSub(a, b);
    }

    public static BitVecExpr mkBVSHL(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVSHL(a, b);
    }

    public static BitVecExpr mkBVLSHR(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVLSHR(a, b);
    }

    public static BitVecExpr mkBVASHR(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVASHR(a, b);
    }

    public static BitVecExpr mkBVAND(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVAND(a, b);
    }

    public static BitVecExpr mkExtract(Context context, int endInclusive, int startInclusive,
                                       BitVecExpr expr) {
        return context.mkExtract(endInclusive, startInclusive, expr);
    }

    public static BitVecExpr mkBVNot(Context context, BitVecExpr expr) {
        return context.mkBVNot(expr);
    }

    public static BitVecExpr mkBVOR(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVOR(a, b);
    }

    public static BitVecExpr mkBVUDiv(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVUDiv(a, b);
    }

    public static BitVecExpr mkBVSDiv(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVSDiv(a, b);
    }

    public static BitVecExpr mkBVURem(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVURem(a, b);
    }

    public static BitVecExpr mkBVSMod(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVSMod(a, b);
    }

    public static BoolExpr mkEq(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkEq(a, b);
    }

    public static BoolExpr mkBVULT(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVULT(a, b);
    }

    public static BoolExpr mkBVSLT(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVSLT(a, b);
    }

    public static BoolExpr mkBVSGT(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVSGT(a, b);
    }

    public static BitVecExpr mkBVXOR(Context context, BitVecExpr a, BitVecExpr b) {
        return context.mkBVXOR(a, b);
    }

    public static void add(Solver solver, BoolExpr constraint) {
        solver.add(constraint);
    }

    public static BoolExpr mkNot(Context context, BoolExpr condition) {
        return context.mkNot(condition);
    }

    public static BoolExpr translate(BoolExpr condition, Context context) {
        return (BoolExpr) condition.translate(context);
    }

    public static boolean isSAT(Solver solver) {
        return solver.check() == Status.SATISFIABLE;
    }

    public static int getSatMemoryOffset(Context context, Solver solver, BitVecExpr to) {
        int sat_i = -1;
        for (int i = 0; i < Constants.MEMORY_BITS / 8; i += 32) {
            solver.push();
            try {
                final BitVecNum tmpTo = mkBV(context, i, 256);
                BoolExpr constraint = mkEq(context, to, tmpTo);
                add(solver, constraint);
                if (Z3Util.isSAT(solver)) {
                    sat_i = i;
                    break;
                }
            } finally {
                solver.pop();
            }
        }
        return sat_i;
    }

    public static int getSatDataOffset(Context context, Solver solver, BitVecExpr from, int offset) {
        int sat_i = -1;
        for (int i = offset; i < Constants.DATA_BITS / 8; i += 32) {
            solver.push();
            try {
                final BitVecNum tmpFrom = mkBV(context, i, 256);
                BoolExpr constraint = mkEq(context, from, tmpFrom);
                add(solver, constraint);
                if (Z3Util.isSAT(solver)) {
                    sat_i = i;
                    break;
                }
            } finally {
                solver.pop();
            }
        }
        return sat_i;
    }

    public static int getSatSize(Context context, Solver solver, BitVecExpr size) {
        int sat_i = -1;
        for (int i = Constants.MAX_SAT_SIZE; i > 0; i /= 2) {
            solver.push();
            try {
                final BitVecNum tmpSize = mkBV(context, i, 256);
                BoolExpr constraint = mkEq(context, size, tmpSize);
                add(solver, constraint);
                if (Z3Util.isSAT(solver)) {
                    sat_i = i;
                    break;
                }
            } finally {
                solver.pop();
            }
        }
        return sat_i;
    }

    public static int getSatJumpTarget(MachineState state, Context context, Solver solver, BitVecExpr target) {
        int sat_i = -1;
        for (int i = 0; i < state.getCodeBytes().length; i++) {
            final Instruction instruction = state.getInstruction(i);
            if (instruction == null || OpCode.JUMPDEST != instruction.getOpCode()) {
                continue;
            }
            solver.push();
            try {
                final BitVecNum tmpSize = mkBV(context, i, 256);
                BoolExpr constraint = mkEq(context, target, tmpSize);
                add(solver, constraint);
                if (Z3Util.isSAT(solver)) {
                    sat_i = i;
                    break;
                }
            } finally {
                solver.pop();
            }
        }
        return sat_i;
    }

    public static boolean isAble(Solver solver, BoolExpr condition) {
        solver.push();
        try {
            add(solver, condition);
            return isSAT(solver);
        } finally {
            solver.pop();
        }
    }

    public static Expr eval(Model model, BitVecExpr expr, boolean b) {
        return model.eval(expr, b);
    }

    public static Model getModel(Solver solver) {
        return solver.getModel();
    }

    public static boolean isBVNum(BitVecExpr a) {
        return a != null && a.simplify() instanceof BitVecNum;
    }

    public static BitVecNum getBVNum(BitVecExpr a) {
        return (BitVecNum) a.simplify();
    }

    public static BoolExpr mkOr(Context context, BoolExpr e1, BoolExpr e2) {
        return context.mkOr(e1, e2);
    }

    public static Solver mkSolver(Context context) {
        // https://github.com/klee/klee/issues/653
        final Tactic tactic = context.andThen(context.mkTactic("simplify"), context.mkTactic("qfbv"));
        final Solver solver = context.mkSolver(tactic);
        setParameters(context, solver);
        return solver;
    }

    public static void setParameters(Context context, Solver solver) {
        Params params = context.mkParams();
        params.add("timeout", Constants.SOLVER_TIMEOUT_MILLS);
        solver.setParameters(params);
    }
}
