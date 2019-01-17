package net.katagaitai.tagomori.evm;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import net.katagaitai.tagomori.NetworkType;
import net.katagaitai.tagomori.TestUtil;
import net.katagaitai.tagomori.util.Util;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.OpCode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

import static junit.framework.TestCase.*;

public class MachineTest {
    private static final String dummyAddressHex = ByteUtil.toHexString(new byte[20]);
    private static EthereumImpl ethereum;

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        ethereum = Util.getEthereumImpl(NetworkType.Ropsten, false, TestUtil.ROPSTEN_DATABASE);
    }

    @AfterClass
    public static void tearDown() {
        Util.closeEthereum(ethereum);
    }

    private MachineState newMachineState(Instruction instruction) {
        Code code = new Code(instruction == null ? new byte[1] : new byte[]{(byte) instruction.getOpValue()});
        Storage storage = new Storage();
        Account account = new Account(BigInteger.ZERO, BigInteger.ONE, storage, code);
        MachineState machineState = new MachineState(dummyAddressHex);
        machineState.putAccount(machineState.getCodeAddressHex(), account);
        return machineState;
    }

    @Test
    public void test_Null() {
        MachineManager manager = new MachineManager(ethereum);
        MachineState state = newMachineState(null);
        Machine machine = new Machine(manager, state);
        machine.executeOne(null);
        assertTrue(state.isVmStopped());
    }


    @Test
    public void test_INVALID() {
        MachineManager manager = new MachineManager(ethereum);
        final Instruction instruction = new Instruction(0x00, null, null);
        MachineState state = newMachineState(instruction);
        Machine machine = new Machine(manager, state);
        machine.executeOne(instruction);
        assertTrue(state.isVmStopped());
    }

    @Test
    public void test_STOP() {
        MachineManager manager = new MachineManager(ethereum);
        final Instruction instruction = new Instruction(OpCode.STOP.val(), OpCode.STOP, "");
        MachineState state = newMachineState(instruction);
        Machine machine = new Machine(manager, state);
        machine.executeOne(instruction);
        assertTrue(state.isVmStopped());
    }

    @Test
    public void test_ADD() {
        MachineManager manager = new MachineManager(ethereum);
        final Instruction instruction = new Instruction(OpCode.ADD.val(), OpCode.ADD, "");
        MachineState state = newMachineState(instruction);
        Machine machine = new Machine(manager, state);
        final Context context = state.getContext();
        state.stackPush(context.mkBV("1", 256));
        state.stackPush(context.mkBV("2", 256));
        machine.executeOne(instruction);
        BitVecExpr result = state.stackPop();
        assertEquals("3", result.simplify().toString());
        assertFalse(state.isVmStopped());
        assertEquals(1, state.getPc());
    }

    @Test
    public void test_REVERT() {
        MachineManager manager = new MachineManager(ethereum);
        final Instruction instruction = new Instruction(OpCode.REVERT.val(), OpCode.REVERT, "");
        MachineState state = newMachineState(instruction);
        Machine machine = new Machine(manager, state);
        final Context context = state.getContext();
        state.stackPush(context.mkBV("0", 256));
        state.stackPush(context.mkBV("0", 256));
        machine.executeOne(instruction);
        assertTrue(state.isVmStopped());
    }


    @Test
    public void test_SUICIDE() {
        MachineManager manager = new MachineManager(ethereum);
        final Instruction instruction = new Instruction(OpCode.SUICIDE.val(), OpCode.SUICIDE, "");
        MachineState state = newMachineState(instruction);
        Machine machine = new Machine(manager, state);
        final Context context = state.getContext();
        state.stackPush(context.mkBV("0", 256));
        machine.executeOne(instruction);
        assertTrue(state.isVmStopped());
    }

}
