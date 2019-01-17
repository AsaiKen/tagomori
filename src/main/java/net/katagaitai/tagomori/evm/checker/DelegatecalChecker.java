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
import org.ethereum.vm.OpCode;

@Slf4j(topic = "tagomori")
public class DelegatecalChecker {
    public static void check(Machine machine, OpCode opCode) {
        MachineState state = machine.getState();
        final Context context = state.getContext();
        final Solver solver = state.getSolver();

        BitVecExpr toExpr = state.stackGet(1);
        BitVecExpr transferAddressExpr =
                Z3Util.mkBV(context, Util.hexToDecimal(machine.getManager().getTransferlAddressHex()), 256);

        for (boolean zeroValue : new boolean[]{true, false}) {
            solver.push();
            try {
                Z3Util.add(solver, Z3Util.mkEq(context, toExpr, transferAddressExpr));
                if (zeroValue) {
                    Util.addZeroValueConstraints(machine);
                }
                if (Z3Util.isSAT(solver)) {
                    log.debug("sat");
                    PoCCategory category =
                            opCode == OpCode.DELEGATECALL ? PoCCategory.DELEGATECALL : PoCCategory.CALLCODE;
                    PoC poc = Util.newPoc(category, machine);
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
