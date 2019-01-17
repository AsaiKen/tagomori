package net.katagaitai.tagomori.evm;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecNum;
import net.katagaitai.tagomori.NetworkType;
import net.katagaitai.tagomori.TestUtil;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.OpCode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.*;

public class MachineStateTest {
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

    @Test
    public void test_getChunkでsizeが0() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(0, 32)
        );
        assertNull(chunk);
    }

    @Test
    public void test_getChunk_256bitより小さいターゲット_範囲内の左側を取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(2, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("aaaabbbb"), 32)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("aaaa", chunk2.getBigInteger().toString(16));
    }

    @Test
    public void test_getChunk_256bitより小さいターゲット_範囲内の中央を取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(1, 256),
                mstate.getContext().mkBV(2, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("aaaabbbb"), 32)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("aabb", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_getChunk_256bitより小さいターゲット_範囲内の右側を取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(2, 256),
                mstate.getContext().mkBV(2, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("aaaabbbb"), 32)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("bbbb", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_getChunk_256bitより小さいターゲット_0スタートで範囲外まで取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(6, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11223344"), 32)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals(6 * 8, chunk2.getSortSize());
        assertEquals("112233440000", chunk2.getBigInteger().toString(16));
    }

    @Test
    public void test_getChunk_256bitより小さいターゲット_1スタートで範囲外まで取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(1, 256),
                mstate.getContext().mkBV(6, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11223344"), 32)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals(6 * 8, chunk2.getSortSize());
        assertEquals("223344000000", chunk2.getBigInteger().toString(16));
    }

    @Test
    public void test_getChunk_256bitより小さいターゲット_範囲外スタートで範囲外まで取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(4, 256),
                mstate.getContext().mkBV(6, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11223344"), 32)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals(6 * 8, chunk2.getSortSize());
        assertEquals("0", chunk2.getBigInteger().toString(16));
    }

    @Test
    public void test_getChunk_256bitより小さいターゲット_範囲内を取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(32, 256),
                mstate.getContext().mkBV(4, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11223344"), 288)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("11223344", chunk2.getBigInteger().toString(16));
    }

    @Test
    public void test_getChunk_256bitより小さいターゲット_範囲内スタートで範囲外を取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(33, 256),
                mstate.getContext().mkBV(4, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11223344"), 288)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("22334400", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_getChunk_256bitより小さいターゲット_範囲外スタートで範囲外を取る() {
        MachineState mstate = new MachineState(dummyAddressHex);
        BitVecExpr chunk = mstate.getChunk(
                mstate.getContext().mkBV(36, 256),
                mstate.getContext().mkBV(4, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11223344"), 288)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("0", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_putMemoryChunkでsizeが0() {
        MachineState mstate = new MachineState(dummyAddressHex);
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11223344"), 256)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(1, 256)
        );
        assertTrue(chunk.simplify() instanceof BitVecExpr);
    }

    @Test
    public void test_putMemoryChunk_memoryより小さいデータ_範囲内の左側にput() {
        MachineState mstate = new MachineState(dummyAddressHex);
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(1, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11"), 8)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(1, 256)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("11", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_putMemoryChunk_memoryより小さいデータ_範囲内の中央にput() {
        MachineState mstate = new MachineState(dummyAddressHex);
        final int index = Constants.MEMORY_BITS / 8 / 2;
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(1, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11"), 8)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(1, 256)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("11", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_putMemoryChunk_memoryより小さいデータ_範囲内の右側にput() {
        MachineState mstate = new MachineState(dummyAddressHex);
        final int index = Constants.MEMORY_BITS / 8 - 1;
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(1, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11"), 8)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(1, 256)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("11", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_putMemoryChunk_memoryより小さいデータ_範囲内スタートで範囲外にput() {
        MachineState mstate = new MachineState(dummyAddressHex);
        final int index = Constants.MEMORY_BITS / 8 - 1;
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(2, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("1122"), 2 * 8)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(2, 256)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("1100", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_putMemoryChunk_memoryより小さいデータ_範囲外スタートで範囲外にput() {
        MachineState mstate = new MachineState(dummyAddressHex);
        final int index = Constants.MEMORY_BITS / 8;
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(1, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("11"), 8)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(1, 256)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("0", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_putMemoryChunk_memoryより大きいデータ_範囲内スタートで範囲外にput() {
        MachineState mstate = new MachineState(dummyAddressHex);
        final int size = Constants.MEMORY_BITS / 8 + 1;
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(0, 256),
                mstate.getContext().mkBV(size, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("1122"), size * 8)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(Constants.MEMORY_BITS / 8 - 1, 256),
                mstate.getContext().mkBV(2, 256)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("1100", chunk2.getBigInteger().toString(16));
    }


    @Test
    public void test_putMemoryChunk_memoryより大きいデータ_範囲外スタートで範囲外にput() {
        MachineState mstate = new MachineState(dummyAddressHex);
        final int index = Constants.MEMORY_BITS / 8;
        final int size = Constants.MEMORY_BITS / 8 + 1;
        mstate.putMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(size, 256),
                mstate.getContext().mkBV(Util.hexToDecimal("1122"), size * 8)
        );
        BitVecExpr chunk = mstate.getMemoryChunk(
                mstate.getContext().mkBV(index, 256),
                mstate.getContext().mkBV(size, 256)
        );
        BitVecNum chunk2 = (BitVecNum) chunk.simplify();
        assertEquals("0", chunk2.getBigInteger().toString(16));
    }

    @Test
    public void tet_getCodeBytes_ropsten_address() {
        byte[] codeBytes = ByteUtil.hexStringToBytes("602a60005260206000f3");
        String addressHex = "A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        assertTrue(Arrays.equals(codeBytes, machine.getState().getCodeBytes()));
    }


    @Test
    public void tet_getCodeBytes_ropsten_longAddress() {
        byte[] codeBytes = ByteUtil.hexStringToBytes("602a60005260206000f3");
        String addressHex = "000000000000000000000000A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        assertTrue(Arrays.equals(codeBytes, machine.getState().getCodeBytes()));
    }

    @Test
    public void test_putStorageValue_ropsten_address() {
        String addressHex = "A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256)).toString(),
                Z3Util.mkBV(state.getContext(), 0, 256).toString());
        state.putStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256),
                Z3Util.mkBV(state.getContext(), 0x4567, 256));
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256)).toString(),
                Z3Util.mkBV(state.getContext(), 0x4567, 256).toString());
    }

    @Test
    public void test_putStorageValue_ropsten_longAddress() {
        String addressHex = "000000000000000000000000A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256)).toString(),
                Z3Util.mkBV(state.getContext(), 0, 256).toString());
        state.putStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256),
                Z3Util.mkBV(state.getContext(), 0x4567, 256));
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256)).toString(),
                Z3Util.mkBV(state.getContext(), 0x4567, 256).toString());
    }


    @Test
    public void test_getStorageValue_ropsten_address() {
        String addressHex = "06c7c9d5725ea48ea15d987b528426966a0f893f";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 1, 256)).toString(),
                Z3Util.mkBV(state.getContext(),
                        Util.hexToDecimal("412076657279207374726f6e67207365637265742070617373776f7264203a29"), 256)
                        .toString());
    }

    @Test
    public void test_getStorageValue_ropsten_longAddress() {
        String addressHex = "00000000000000000000000006c7c9d5725ea48ea15d987b528426966a0f893f";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 1, 256)).toString(),
                Z3Util.mkBV(state.getContext(),
                        Util.hexToDecimal("412076657279207374726f6e67207365637265742070617373776f7264203a29"), 256)
                        .toString());
    }

    @Test
    public void test_getStorageValue_ropsten_address_BVConstKey() {
        String addressHex = "06c7c9d5725ea48ea15d987b528426966a0f893f";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
//            assertEquals(state.getStorageValue(Z3Util.mkBVConst(state.getContext(), "TEST", 256)).toString(),
//                    Z3Util.mkBV(state.getContext(), 0, 256).toString());
        assertNull(state.getStorageValue(Z3Util.mkBVConst(state.getContext(), "TEST", 256)));
        state.putStorageValue(Z3Util.mkBVConst(state.getContext(), "TEST", 256),
                Z3Util.mkBV(state.getContext(), 0x4567, 256));
        assertEquals(state.getStorageValue(Z3Util.mkBVConst(state.getContext(), "TEST", 256)).toString(),
                Z3Util.mkBV(state.getContext(), 0x4567, 256).toString());
    }

    @Test
    public void test_getStorageValue_ropsten_address_BVConst() {
        String addressHex = "06c7c9d5725ea48ea15d987b528426966a0f893f";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256)).toString(),
                Z3Util.mkBV(state.getContext(), 0, 256).toString());
        state.putStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256),
                Z3Util.mkBVConst(state.getContext(), "TEST", 256));
        assertEquals(state.getStorageValue(Z3Util.mkBV(state.getContext(), 0x1234, 256)).toString(),
                Z3Util.mkBVConst(state.getContext(), "TEST", 256).toString());
    }

    @Test
    public void test_getInstruction_ropsten_address() {
        String addressHex = "00000000000000000000000006c7c9d5725ea48ea15d987b528426966a0f893f";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        assertEquals(OpCode.PUSH1, state.getInstruction(0).getOpCode()); // PUSH1 0x60
        assertEquals(OpCode.PUSH1, state.getInstruction(2).getOpCode()); // PUSH1 0x40
    }

    @Test
    public void test_getExtCodeBytes_ropsten_address() {
        byte[] codeBytes = ByteUtil.hexStringToBytes("602a60005260206000f3");
        String addressHex = "A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        byte[] bytes = state.getExtCodeBytes(
                Z3Util.mkBV(state.getContext(), Util.hexToDecimal(state.getCodeAddressHex()), 256));
        assertTrue(Arrays.equals(codeBytes, bytes));
    }

    @Test
    public void test_getExtCodeBytes_ropsten_longAddress() {
        byte[] codeBytes = ByteUtil.hexStringToBytes("602a60005260206000f3");
        String addressHex = "000000000000000000000000A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();
        byte[] bytes = state.getExtCodeBytes(
                Z3Util.mkBV(state.getContext(), Util.hexToDecimal(state.getCodeAddressHex()), 256));
        assertTrue(Arrays.equals(codeBytes, bytes));
    }

    @Test
    public void test_putAccount_ropsten_address() {
        byte[] codeBytes = ByteUtil.hexStringToBytes("602a60005260206000f3");
        String addressHex = "A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();

        byte[] bytes = state.getCodeBytes(state.getCodeAddressHex(), false);
        assertTrue(Arrays.equals(codeBytes, bytes));

        byte[] newCodeBytes = new byte[]{0x60, 0x60, 0x60, 0x40};
        Code code = new Code(newCodeBytes);
        Storage storage = new Storage();
        Account account = new Account(BigInteger.ZERO, BigInteger.ONE, storage, code);
        state.putAccount(addressHex, account);
        byte[] bytes2 = state.getCodeBytes(state.getCodeAddressHex(), false);
        assertTrue(Arrays.equals(newCodeBytes, bytes2));
    }

    @Test
    public void test_putAccount_ropsten_longAddress() {
        byte[] originalCodeBytes = ByteUtil.hexStringToBytes("602a60005260206000f3");
        String addressHex = "000000000000000000000000A53514927D1a6a71f8075Ba3d04eb7379B04C588";
        MachineManager manager = new MachineManager(ethereum);
        Machine machine = manager.newMachine(addressHex);
        MachineState state = machine.getState();

        byte[] bytes = state.getExtCodeBytes(
                Z3Util.mkBV(state.getContext(), Util.hexToDecimal(state.getCodeAddressHex()), 256));
        assertTrue(Arrays.equals(originalCodeBytes, bytes));

        byte[] newCodeBytes = new byte[]{0x60, 0x60, 0x60, 0x40};
        Code code = new Code(newCodeBytes);
        Storage storage = new Storage();
        Account account = new Account(BigInteger.ZERO, BigInteger.ONE, storage, code);
        state.putAccount(addressHex, account);
        byte[] bytes2 = state.getExtCodeBytes(
                Z3Util.mkBV(state.getContext(), Util.hexToDecimal(state.getCodeAddressHex()), 256));
        assertTrue(Arrays.equals(newCodeBytes, bytes2));
    }
}
