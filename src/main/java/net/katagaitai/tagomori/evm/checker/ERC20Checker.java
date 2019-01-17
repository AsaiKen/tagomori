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

@Slf4j(topic = "tagomori")
public class ERC20Checker {

    public static void check(Machine machine, BitVecExpr balance) {
        MachineState state = machine.getState();
        if (!state.isERC20Checking()) {
            return;
        }
        final Context context = state.getContext();
        final Solver solver = state.getSolver();
        // 0etherでコインを取得できればOK
        Util.addZeroValueConstraints(machine);
        Z3Util.add(solver, Z3Util.mkBVUGT(context, balance,
                Z3Util.mkBV(context, Constants.ERC20_MIN_BALANCE.toString(), balance.getSortSize())));
        if (Z3Util.isSAT(solver)) {
            log.debug("sat");
            PoC poc = Util.newPoc(PoCCategory.ERC20, machine);
            machine.getManager().addPoC(poc);
        } else {
            log.debug("unsat");
        }
    }
}
