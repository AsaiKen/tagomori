package net.katagaitai.tagomori.evm.checker;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.evm.Machine;
import net.katagaitai.tagomori.evm.MachineState;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.poc.PoCCategory;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;

@Slf4j(topic = "tagomori")
public class CallChecker {
    private static final BigInteger MIN_VALUE = Constants.MAX_TX_VALUE;

    public static void check(Machine machine) {
        MachineState state = machine.getState();
        final Context context = state.getContext();
        final Solver solver = state.getSolver();
        // CALLのvalueはコンテキストの残高が使われる
        BigInteger balance = state.getBalance(ByteUtil.hexStringToBytes(machine.getState().getContextAddressHex()));
        BitVecExpr toExpr = state.stackGet(1);
        BitVecExpr valueExpr = state.stackGet(2);

        solver.push();
        try {
            BitVecExpr originAddressExpr = Z3Util.mkBV(context, Util.hexToDecimal(state.getOriginAddressHex()), 256);
            if (state.isUsingProxy()) {
                BitVecExpr proxyAddressExpr =
                        Z3Util.mkBV(context, Util.hexToDecimal(machine.getManager().getProxyAddressHex()), 256);
                Z3Util.add(solver,
                        Z3Util.mkOr(
                                context,
                                Z3Util.mkEq(context, toExpr, originAddressExpr),
                                Z3Util.mkEq(context, toExpr, proxyAddressExpr)
                        )
                );
            } else {
                Z3Util.add(solver, Z3Util.mkEq(context, toExpr, originAddressExpr));
            }
            if (Z3Util.isSAT(solver)) {
                for (boolean zeroValue : new boolean[]{true, false}) {
                    if (checkEqValue(machine, context, solver, valueExpr, balance.toString(), zeroValue)) {
                        log.debug("全額");
                        break;
                    } else if (checkEqValue(machine, context, solver, valueExpr,
                            balance.divide(BigInteger.valueOf(2)).toString(), zeroValue)) {
                        log.debug("半額");
                        break;
                    } else {
                        solver.push();
                        try {
                            Z3Util.add(solver, Z3Util.mkBVUGT(context, valueExpr,
                                    Z3Util.mkBV(context, MIN_VALUE.toString(), 256)));
                            Z3Util.add(solver, Z3Util.mkBVULT(context, valueExpr,
                                    Z3Util.mkBV(context, balance.add(BigInteger.ONE).toString(), 256)));
                            if (zeroValue) {
                                Util.addZeroValueConstraints(machine);
                            }
                            if (Z3Util.isSAT(solver)) {
                                log.debug("msg.value < value <= 全額");
                                log.debug("sat");
                                PoC poc = Util.newPoc(PoCCategory.CALL, machine);
                                machine.getManager().addPoC(poc);
                                break;
                            } else {
                                log.debug("unsat");
                            }
                        } finally {
                            solver.pop();
                        }
                    }
                }
            }
        } finally {
            solver.pop();
        }
    }

    private static boolean checkEqValue(Machine machine, Context context, Solver solver, BitVecExpr valueExpr,
                                        String targetValue, boolean zeroValue) {
        solver.push();
        try {
            Z3Util.add(solver,
                    Z3Util.mkBVUGT(context, valueExpr, Z3Util.mkBV(context, MIN_VALUE.toString(), 256)));
            Z3Util.add(solver, Z3Util.mkEq(context, valueExpr, Z3Util.mkBV(context, targetValue, 256)));
            if (zeroValue) {
                Util.addZeroValueConstraints(machine);
            }
            if (Z3Util.isSAT(solver)) {
                log.debug("sat");
                PoC poc = Util.newPoc(PoCCategory.CALL, machine);
                machine.getManager().addPoC(poc);
                return true;
            } else {
                log.debug("unsat");
            }
        } finally {
            solver.pop();
        }
        return false;
    }
}
