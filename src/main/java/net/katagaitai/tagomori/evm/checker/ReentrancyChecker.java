package net.katagaitai.tagomori.evm.checker;

import com.microsoft.z3.*;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.evm.Machine;
import net.katagaitai.tagomori.evm.MachineState;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.poc.PoCCategory;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;

import java.math.BigInteger;

@Slf4j(topic = "tagomori")
public class ReentrancyChecker {
    public static final String MIN_VALUE_DECIMAL = Constants.MAX_TX_VALUE.divide(BigInteger.valueOf(2)).toString();
    public static final String MAX_VALUE_DECIMAL = Constants.MAX_TX_VALUE.add(BigInteger.ONE).toString();
    // Proxy.solで指定した最低ガスの2倍を確保しておく
    public static final int MIN_GAS = 2000000;

    public static void check(Machine machine) {
        if (!machine.getState().isUsingProxy()) {
            return;
        }

        MachineState state = machine.getState();
        final Context context = state.getContext();
        final Solver solver = state.getSolver();
        String proxyAddressHex = machine.getManager().getProxyAddressHex();

        BitVecExpr gasExpr = state.stackGet(0);
        BitVecExpr toExpr = state.stackGet(1);
        BitVecExpr valueExpr = state.stackGet(2);
        BitVecExpr proxyAddressExpr = Z3Util.mkBV(context, Util.hexToDecimal(proxyAddressHex), 256);

        // gas > min_gas
        final BoolExpr gasConstraint = Z3Util.mkBVUGT(context, gasExpr, Z3Util.mkBV(context, MIN_GAS, 256));
        // arg to == proxy
        final BoolExpr toConstraint = Z3Util.mkEq(context, toExpr, proxyAddressExpr);
        // msg.value/2 < value <= msg.value
        // 二重検知を避けるために、CallCheckerのvalueの範囲と重ならない範囲を使う
        final BitVecNum minValueExpr = Z3Util.mkBV(context, MIN_VALUE_DECIMAL, 256);
        final BoolExpr valueConstraint = Z3Util.mkBVULT(context, minValueExpr, valueExpr);
        final BitVecNum maxValueExpr = Z3Util.mkBV(context, MAX_VALUE_DECIMAL, 256);
        final BoolExpr valueConstraint2 = Z3Util.mkBVULT(context, valueExpr, maxValueExpr);

        boolean ok = false;
        solver.push();
        try {
            addConstraints(solver, gasConstraint, toConstraint, valueConstraint, valueConstraint2);
            if (Z3Util.isSAT(solver)) {
                log.debug("sat");
                if (state.callStackContains(proxyAddressHex)) {
                    log.debug("リエントランス発生");
                    PoC poc = Util.newPoc(PoCCategory.REENTRANCY, machine);
                    machine.getManager().addPoC(poc);
                } else {
                    ok = true;
                }
            } else {
                log.debug("unsat");
            }
        } finally {
            solver.pop();
        }
        if (ok) {
            log.debug("制約追加");
            addConstraints(solver, gasConstraint, toConstraint, valueConstraint, valueConstraint2);
            state.stackSet(1, proxyAddressExpr);
        }
        if (state.callStackContains(proxyAddressHex)
                && Z3Util.isBVNum(toExpr)
                && Util.normalizeAddress(Util.bitvecnumToHex(Z3Util.getBVNum(toExpr))).equals(proxyAddressHex)) {
            log.debug("再帰防止");
            state.setVmStopped(true);
            state.setTxsStopped(true);
        }
    }

    private static void addConstraints(Solver solver, BoolExpr... constraints) {
        for (BoolExpr c : constraints) {
            Z3Util.add(solver, c);
        }
    }

    public static boolean callTargetIfProxy(Machine machine, BitVecExpr to) {
        if (!machine.getState().isUsingProxy()) {
            return false;
        }

        MachineState state = machine.getState();
        String proxyAddressHex = machine.getManager().getProxyAddressHex();
        final String targetAddressHex = state.getTargetAddressHex();
        if (state.getContextAddressHex().equals(proxyAddressHex)
                && Z3Util.isBVNum(to)
                && Util.normalizeAddress(Util.bitvecnumToHex(Z3Util.getBVNum(to))).equals(targetAddressHex)) {
            state.newReentrancyInput();
            // proxyからtargetに戻る
            state.call(
                    Z3Util.mkBV(state.getContext(), Util.hexToDecimal(targetAddressHex), 256),
                    state.getReentrancyValue().getExpr(),
                    state.getReentrancyData().getExpr(),
                    Z3Util.mkBV(state.getContext(), 0, 256),
                    Z3Util.mkBV(state.getContext(), 0, 256)
            );
            machine.getState().addZeroValueConstraint(state.getReentrancyValue().getExpr());
            return true;
        }
        return false;
    }
}
