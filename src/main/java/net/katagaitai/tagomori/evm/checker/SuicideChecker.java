package net.katagaitai.tagomori.evm.checker;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.evm.Machine;
import net.katagaitai.tagomori.evm.MachineState;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.poc.PoCCategory;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;

@Slf4j(topic = "tagomori")
public class SuicideChecker {
    public static void check(Machine machine) {
        MachineState state = machine.getState();
        final Context context = state.getContext();
        final Solver solver = state.getSolver();

        BitVecExpr toExpr = state.stackGet(0);

        for (boolean zeroValue : new boolean[]{true, false}) {
            solver.push();
            try {
                BitVecExpr originAddressExpr =
                        Z3Util.mkBV(context, Util.hexToDecimal(state.getOriginAddressHex()), 256);
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
                if (zeroValue) {
                    Util.addZeroValueConstraints(machine);
                }
                if (Z3Util.isSAT(solver)) {
                    log.debug("sat");
                    PoC poc = Util.newPoc(PoCCategory.SUICIDE, machine);
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
