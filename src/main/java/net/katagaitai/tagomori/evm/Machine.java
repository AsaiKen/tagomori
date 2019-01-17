package net.katagaitai.tagomori.evm;

import com.microsoft.z3.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.evm.checker.*;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.PrecompiledContracts;

import java.math.BigInteger;
import java.util.Set;

import static java.lang.String.format;

@Slf4j(topic = "tagomori")
@RequiredArgsConstructor
public class Machine implements Runnable {
    private static final BigInteger _BI_2_256_ = BigInteger.valueOf(2).pow(256);
    private static final String logString = "{}    Op: [{}]  Gas: [{}] Deep: [{}]  Hint: [{}]";
    private static final OpCode[] SUSPICIOUS_OPCODES =
            {OpCode.CALL, OpCode.CALLCODE, OpCode.DELEGATECALL, OpCode.SUICIDE, OpCode.CREATE, OpCode.CREATE2};
    private static final PrecompiledContracts.PrecompiledContract[] PRECOMPILED_CONTRACTS =
            new PrecompiledContracts.PrecompiledContract[]{new PrecompiledContracts.ECRecover(),
                    new PrecompiledContracts.Sha256(), new PrecompiledContracts.Ripempd160(),
                    new PrecompiledContracts.Identity(), new PrecompiledContracts.ModExp(),
                    new PrecompiledContracts.BN128Addition(), new PrecompiledContracts.BN128Multiplication(),
                    new PrecompiledContracts.BN128Pairing()};
    public static final String ADDRESS_MASK = "ffffffffffffffffffffffffffffffffffffffff";
    public static final String LONG_ARRAY_SIZE = "f000000000000000000000000000000000000000000000000000000000000000";

    @Getter
    private final MachineManager manager;
    @Getter
    private final MachineState state;

    @Override
    public void run() {
        try {
            _run();
        } catch (Exception e) {
            shutdown();
            throw e;
        } finally {
            if (state.isTxsStopped()) {
                // z3はcloseしないとメモリリークする
                getContext().close();
            } else {
                if (Util.isERC20(state.getCode().getHex())) {
                    newThreadForERC20Check(state.getOriginAddressHex());
                    if (state.isUsingProxy()) {
                        newThreadForERC20Check(manager.getProxyAddressHex());
                    }
                }
                // contextのcopy中にcontextを触ってしまうのを避けるために、
                // stateを再利用するケースは最後に置く
                if (state.getUserInputHistory().size() + 1 < manager.getMaxTransactionCount()) {
                    state.updateForNextTransaction();
                    manager.executeMachine(new Machine(manager, state));
                }
            }
        }
    }

    private void newThreadForERC20Check(String addressHex) {
        if (state.isERC20Checking()) {
            // 終了
        } else {
            MachineState stateCopy = state.copy();
            stateCopy.updateForERC20Checking(addressHex);
            manager.executeMachine(new Machine(manager, stateCopy));
        }
    }

    private void _run() {
        if (isTxCall()) {
            if (isEmptyCode()) {
                throw new RuntimeException("空のコード");
            }
            if (isSafeCode()) {
                log.info("安全なコード");
                shutdown();
                return;
            }
        }
        log.debug("開始 TX:{} PC:{} TARGET:{}",
                state.getUserInputHistory().size() + 1, Integer.toHexString(state.getPc()),
                state.getTargetAddressHex());
        if (state.isERC20Checking()) {
            log.debug("ERC20検査");
        }
        while (true) {
            if (Thread.interrupted()) {
                log.debug("割り込み");
                shutdown();
                break;
            }
            if (manager.isDone(state.getTargetAddressHex())) {
                log.debug("他スレッドで検知したため終了");
                shutdown();
                break;
            }
            Instruction instruction = state.getInstruction(state.getPc());
            log.debug(getDisasmLine(instruction));

            if (state.isDoVisitedCheck()) {
                if (manager.isVisitedMachineState(state)) {
                    log.debug("実行済みの状態");
                    shutdown();
                    break;
                }
                manager.addVisitedMachineState(state);
                state.setDoVisitedCheck(false);
            }

            // 実行
            executeOne(instruction);
            if (state.isVmStopped()) {
                break;
            }
            logTrace(instruction, state.getPc());
        }
        log.debug("終了 TX:{} PC:{} TARGET:{}",
                state.getUserInputHistory().size() + 1, Integer.toHexString(state.getPc()),
                state.getTargetAddressHex());
    }

    private boolean isTxCall() {
        return state.getCallStackSize() == 0;
    }

    private boolean isEmptyCode() {
        return state.getCode().getHex().length() == 0;
    }

    private boolean isSafeCode() {
        if (Util.isERC20(state.getCode().getHex())) {
            return false;
        }
        Code code = state.getCode();
        Set<OpCode> ops = code.getOpCodes();
        for (OpCode suspicious : SUSPICIOUS_OPCODES) {
            if (ops.contains(suspicious)) {
                return false;
            }
        }
        return true;
    }

    void executeOne(Instruction instruction) {
        if (state.getCodeBytes().length == 0) {
            log.debug("空のコード: {}", state.getCodeAddressHex());
            state.stop();
            return;
        }
        if (instruction == null) {
            state.invalid();
            return;
        }
        OpCode opCode = instruction.getOpCode();
        if (opCode == null) {
            state.invalid();
            return;
        }
        if (state.stackSize() < opCode.require()) {
            state.invalid();
            return;
        }

        int startStackSize = state.stackSize();
        if (opCode.equals(OpCode.STOP)) {
            state.stop();
            return;
        } else if (opCode.equals(OpCode.ADD)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkBVAdd(getContext(), a, b));
        } else if (opCode.equals(OpCode.MUL)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkBVMul(getContext(), a, b));
        } else if (opCode.equals(OpCode.SUB)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkBVSub(getContext(), a, b));
        } else if (opCode.equals(OpCode.DIV)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkITE(getContext(),
                    Z3Util.mkEq(getContext(), b, mkBVNum("0")),
                    mkBVNum("0"),
                    Z3Util.mkBVUDiv(getContext(), a, b))
            );
        } else if (opCode.equals(OpCode.SDIV)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkITE(getContext(),
                    Z3Util.mkEq(getContext(), b, mkBVNum("0")),
                    mkBVNum("0"),
                    Z3Util.mkBVSDiv(getContext(), a, b))
            );
        } else if (opCode.equals(OpCode.MOD)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkITE(getContext(),
                    Z3Util.mkEq(getContext(), b, mkBVNum("0")),
                    mkBVNum("0"),
                    Z3Util.mkBVURem(getContext(), a, b))
            );
        } else if (opCode.equals(OpCode.SMOD)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkITE(getContext(),
                    Z3Util.mkEq(getContext(), b, mkBVNum("0")),
                    mkBVNum("0"),
                    Z3Util.mkBVSMod(getContext(), a, b))
            );
        } else if (opCode.equals(OpCode.ADDMOD)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            BitVecExpr c = pop();
            BitVecExpr add = Z3Util.mkBVAdd(getContext(), a, b);
            push(Z3Util.mkITE(getContext(),
                    Z3Util.mkEq(getContext(), c, mkBVNum("0")),
                    mkBVNum("0"),
                    Z3Util.mkBVURem(getContext(), add, c))
            );
        } else if (opCode.equals(OpCode.MULMOD)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            BitVecExpr c = pop();
            BitVecExpr mul = Z3Util.mkBVMul(getContext(), a, b);
            push(Z3Util.mkITE(getContext(),
                    Z3Util.mkEq(getContext(), c, mkBVNum("0")),
                    mkBVNum("0"),
                    Z3Util.mkBVURem(getContext(), mul, c))
            );
        } else if (opCode.equals(OpCode.EXP)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            if (Z3Util.isBVNum(a) && Z3Util.isBVNum(b)) {
                BigInteger word1 = Z3Util.getBVNum(a).getBigInteger();
                BigInteger word2 = Z3Util.getBVNum(b).getBigInteger();
                BigInteger newData = word1.modPow(word2, _BI_2_256_);
                push(mkBVNum(newData.toString()));
            } else {
                log.warn("不明なEXP");
                push(mkBVConst(String.format("EXP(%s,%s)", Util.exprToString(a), Util.exprToString(b))));
            }
        } else if (opCode.equals(OpCode.SIGNEXTEND)) {
            // aは右から数えたindex
            BitVecExpr index = pop();
            BitVecExpr value = pop();
            BitVecExpr a8_7 = Z3Util.mkBVAdd(getContext(),
                    Z3Util.mkBVMul(getContext(), index, mkBVNum("8")),
                    mkBVNum("7")
            );
            BitVecExpr a8_7_mask = Z3Util.mkBVSHL(getContext(), mkBVNum("1"), a8_7);
            BitVecExpr test = Z3Util.mkBVAND(getContext(), value, a8_7_mask);

            // testbitが0の場合
            BitVecExpr mask0 = Z3Util.mkBVSub(getContext(), a8_7_mask, mkBVNum("1"));
            BitVecExpr result0 = Z3Util.mkBVAND(getContext(), mask0, value);

            // testbitが1の場合
            BitVecExpr a8_8 = Z3Util.mkBVAdd(getContext(), a8_7, mkBVNum("1")
            );
            BitVecExpr mask1 = Z3Util.mkBVNot(getContext(),
                    Z3Util.mkBVSub(getContext(),
                            Z3Util.mkBVSHL(getContext(), mkBVNum("1"), a8_8),
                            mkBVNum("1")
                    )
            );
            BitVecExpr result1 = Z3Util.mkBVOR(getContext(), mask1, value);

            BoolExpr cond = Z3Util.mkEq(getContext(), test, mkBVNum("0"));
            push(mkITE(cond, result0, result1));
        } else if (opCode.equals(OpCode.LT)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            BitVecExpr lt = mkITE(Z3Util.mkBVULT(getContext(), a, b), mkBVNum("1"), mkBVNum("0"));
            push(lt);
        } else if (opCode.equals(OpCode.GT)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            BitVecExpr gt = mkITE(Z3Util.mkBVUGT(getContext(), a, b), mkBVNum("1"), mkBVNum("0"));
            push(gt);
        } else if (opCode.equals(OpCode.SLT)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            BitVecExpr lt = mkITE(Z3Util.mkBVSLT(getContext(), a, b), mkBVNum("1"), mkBVNum("0"));
            push(lt);
        } else if (opCode.equals(OpCode.SGT)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            BitVecExpr gt = mkITE(Z3Util.mkBVSGT(getContext(), a, b), mkBVNum("1"), mkBVNum("0"));
            push(gt);
        } else if (opCode.equals(OpCode.EQ)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            BitVecExpr eq = mkITE(Z3Util.mkEq(getContext(), a, b), mkBVNum("1"), mkBVNum("0"));
            push(eq);
        } else if (opCode.equals(OpCode.ISZERO)) {
            BitVecExpr a = pop();
            BitVecExpr iszero = mkITE(Z3Util.mkEq(getContext(), a, mkBVNum("0")), mkBVNum("1"), mkBVNum("0"));
            push(iszero);
        } else if (opCode.equals(OpCode.AND)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            if (manager.isAndArgToAddress()) {
                if (isAddressMask(a)) {
                    newThreadForAddress(b);
                } else if (isAddressMask(b)) {
                    newThreadForAddress(a);
                }
            }
            push(Z3Util.mkBVAND(getContext(), a, b));
        } else if (opCode.equals(OpCode.OR)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkBVOR(getContext(), a, b));
        } else if (opCode.equals(OpCode.XOR)) {
            BitVecExpr a = pop();
            BitVecExpr b = pop();
            push(Z3Util.mkBVXOR(getContext(), a, b));
        } else if (opCode.equals(OpCode.NOT)) {
            BitVecExpr a = pop();
            push(Z3Util.mkBVNot(getContext(), a));
        } else if (opCode.equals(OpCode.BYTE)) {
            // aは左から数えたindex
            BitVecExpr index = pop();
            BitVecExpr value = pop();
            BitVecExpr count = Z3Util.mkBVMul(getContext(),
                    Z3Util.mkBVSub(getContext(), mkBVNum("31"), index),
                    mkBVNum("8")
            );
            BitVecExpr lshr = Z3Util.mkBVLSHR(getContext(), value, count);
            BitVecExpr result = Z3Util.mkBVAND(getContext(), lshr, mkBVNum("255"));
            push(result);
        } else if (opCode.equals(OpCode.SHA3)) {
            BitVecExpr from = pop();
            BitVecExpr size = pop();

            BitVecNum fromNum = null;
            if (Z3Util.isBVNum(from)) {
                fromNum = Z3Util.getBVNum(from);
            } else {
                int satFrom = Z3Util.getSatMemoryOffset(getContext(), getSolver(), from);
                if (satFrom == -1) {
                    log.warn("不明なSHA3 from");
                } else {
                    log.debug("SAT from: {}", satFrom);
                    final BitVecNum tmpFrom = mkBVNum(Integer.toString(satFrom));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), from, tmpFrom);
                    addToSolver(constraint);
                    fromNum = tmpFrom;
                }
            }

            BitVecNum sizeNum = null;
            if (Z3Util.isBVNum(size)) {
                sizeNum = Z3Util.getBVNum(size);
            } else {
                int satSize = Z3Util.getSatSize(getContext(), getSolver(), size);
                if (satSize == -1) {
                    log.warn("不明なSHA3 size");
                } else {
                    log.debug("SAT size: {}", satSize);
                    final BitVecNum tmpSize = mkBVNum(Integer.toString(satSize));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), size, tmpSize);
                    addToSolver(constraint);
                    sizeNum = tmpSize;
                }
            }

            if (fromNum != null && sizeNum != null) {
                BitVecExpr chunk = state.getMemoryChunk(fromNum, sizeNum);
                if (chunk == null) {
                    push(mkBVNum(Util.bytesToDecimal(HashUtil.EMPTY_DATA_HASH)));
                } else {
                    if (Z3Util.isBVNum(chunk)) {
                        byte[] encoded = HashUtil.sha3(Util.bitvecnumToBytes(Z3Util.getBVNum(chunk)));
                        push(mkBVNum(Util.bytesToDecimal(encoded)));
                    } else if (newThreadForMapping(chunk)) {
                        shutdown();
                        return;
                    } else {
                        log.warn("不明なSHA3");
                        push(mkBVConst(
                                String.format("SHA3(%s,%s)", Util.exprToString(fromNum), Util.exprToString(sizeNum))));
                    }
                }
            } else {
                log.warn("不明なSHA3");
                push(mkBVConst(String.format("SHA3(%s,%s)", Util.exprToString(from), Util.exprToString(size))));
            }
        } else if (opCode.equals(OpCode.ADDRESS)) {
            final String hex = state.getContextAddressHex();
            final String decimal = Util.hexToDecimal(hex);
            push(mkBVNum(decimal));
        } else if (opCode.equals(OpCode.BALANCE)) {
            BitVecExpr address = pop();
            push(mkBVConst(String.format("BALANCE(%s)", Util.exprToString(address))));
        } else if (opCode.equals(OpCode.ORIGIN)) {
            push(mkBVNum(Util.hexToDecimal(state.getOriginAddressHex())));
        } else if (opCode.equals(OpCode.CALLER)) {
            String caller = state.getCallerAddressHex();
            push(mkBVNum(Util.hexToDecimal(caller)));
        } else if (opCode.equals(OpCode.CALLVALUE)) {
            push(state.getValue().getExpr());
        } else if (opCode.equals(OpCode.CALLDATALOAD)) {
            BitVecExpr from = pop();
            final int sizeInt = 32;
            final BitVecNum size = mkBVNum(Integer.toString(sizeInt));

            BitVecNum fromNum = null;
            if (Z3Util.isBVNum(from)) {
                fromNum = Z3Util.getBVNum(from);
            } else {
                int satFrom = Z3Util.getSatDataOffset(getContext(), getSolver(), from, state.getUnusedDataOffset());
                if (satFrom == -1) {
                    log.warn("不明なCALLDATALOAD from");
                } else {
                    log.debug("SAT from: {}", satFrom);
                    final BitVecNum tmpFrom = mkBVNum(Integer.toString(satFrom));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), from, tmpFrom);
                    addToSolver(constraint);
                    fromNum = tmpFrom;
                    state.setUnusedDataOffset(satFrom + sizeInt);
                }
            }

            if (fromNum != null) {
                BitVecExpr data = state.getDataChunk(fromNum, size);
                push(data);
            } else {
                log.warn("不明なCALLDATALOAD");
                push(mkBVConst(String.format("CALLDATALOAD(%s)", Util.exprToString(from))));
            }
        } else if (opCode.equals(OpCode.CALLDATASIZE)) {
            push(mkBVNum(Integer.toString(Constants.DATA_BITS / 8)));
        } else if (opCode.equals(OpCode.CALLDATACOPY)) {
            BitVecExpr to = pop();
            BitVecExpr from = pop();
            BitVecExpr size = pop();

            BitVecNum toNum = null;
            if (Z3Util.isBVNum(to)) {
                toNum = Z3Util.getBVNum(to);
            } else {
                int satTo = Z3Util.getSatMemoryOffset(getContext(), getSolver(), to);
                if (satTo == -1) {
                    log.warn("不明なCALLDATACOPY to");
                } else {
                    log.debug("SAT to: {}", satTo);
                    final BitVecNum tmpTo = mkBVNum(Integer.toString(satTo));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), to, tmpTo);
                    addToSolver(constraint);
                    toNum = tmpTo;
                }
            }

            BitVecNum fromNum = null;
            if (Z3Util.isBVNum(from)) {
                fromNum = Z3Util.getBVNum(from);
            } else {
                int satFrom = Z3Util.getSatDataOffset(getContext(), getSolver(), from, state.getUnusedDataOffset());
                if (satFrom == -1) {
                    log.warn("不明なCALLDATACOPY from");
                } else {
                    log.debug("SAT from: {}", satFrom);
                    final BitVecNum tmpFrom = mkBVNum(Integer.toString(satFrom));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), from, tmpFrom);
                    addToSolver(constraint);
                    fromNum = tmpFrom;
                    state.setUnusedDataOffset(satFrom + Constants.MAX_SAT_SIZE);
                }
            }

            newThreadForDynamicArray(size);

            BitVecNum sizeNum = null;
            if (Z3Util.isBVNum(size)) {
                sizeNum = Z3Util.getBVNum(size);
            } else {
                int satSize = Z3Util.getSatSize(getContext(), getSolver(), size);
                if (satSize == -1) {
                    log.warn("不明なCALLDATACOPY size");
                } else {
                    log.debug("SAT size: {}", satSize);
                    final BitVecNum tmpSize = mkBVNum(Integer.toString(satSize));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), size, tmpSize);
                    addToSolver(constraint);
                    sizeNum = tmpSize;
                }
            }

            if (toNum != null && fromNum != null && sizeNum != null) {
                BitVecExpr chunk = state.getDataChunk(fromNum, sizeNum);
                state.putMemoryChunk(toNum, sizeNum, chunk);
            } else {
                log.warn("不明なCALLDATACOPY");
            }
        } else if (opCode.equals(OpCode.CODESIZE)) {
            final byte[] codeBytes = state.getCodeBytes();
            push(mkBVNum(Integer.toString(codeBytes.length)));
        } else if (opCode.equals(OpCode.CODECOPY)) {
            BitVecExpr to = pop();
            BitVecExpr from = pop();
            BitVecExpr size = pop();
            if (Z3Util.isBVNum(size)) {
                final BitVecNum sizeNum = Z3Util.getBVNum(size);
                BitVecExpr chunk = state.getCodeChunk(from, sizeNum);
                state.putMemoryChunk(to, sizeNum, chunk);
            } else {
                int satSize = Z3Util.getSatSize(getContext(), getSolver(), size);
                if (satSize == -1) {
                    log.warn("不明なCODECOPY サイズ");
                } else {
                    log.debug("SATサイズ: {}", satSize);
                    final BitVecNum tmpSize = mkBVNum(Integer.toString(satSize));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), size, tmpSize);
                    addToSolver(constraint);
                    BitVecExpr chunk = state.getCodeChunk(from, tmpSize);
                    state.putMemoryChunk(to, tmpSize, chunk);
                }
            }
        } else if (opCode.equals(OpCode.RETURNDATASIZE)) {
            BitVecExpr returnData = state.getReturnData().getExpr();
            if (returnData == null) {
                push(mkBVNum("0"));
            } else {
                push(mkBVNum(Integer.toString(returnData.getSortSize() / 8)));
            }
        } else if (opCode.equals(OpCode.RETURNDATACOPY)) {
            BitVecExpr to = pop();
            BitVecExpr from = pop();
            BitVecExpr size = pop();
            if (Z3Util.isBVNum(size)) {
                final BitVecNum sizeNum = Z3Util.getBVNum(size);
                BitVecExpr chunk = state.getReturnDataChunk(from, sizeNum);
                state.putMemoryChunk(to, sizeNum, chunk);
            } else {
                int satSize = Z3Util.getSatSize(getContext(), getSolver(), size);
                if (satSize == -1) {
                    log.warn("不明なRETURNDATACOPY サイズ");
                } else {
                    log.debug("SATサイズ: {}", satSize);
                    final BitVecNum tmpSize = mkBVNum(Integer.toString(satSize));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), size, tmpSize);
                    addToSolver(constraint);
                    BitVecExpr chunk = state.getReturnDataChunk(from, tmpSize);
                    state.putMemoryChunk(to, tmpSize, chunk);
                }
            }
        } else if (opCode.equals(OpCode.GASPRICE)) {
            push(mkBVConst("GASPRICE"));
        } else if (opCode.equals(OpCode.EXTCODESIZE)) {
            BitVecExpr address = pop();
            if (Z3Util.isBVNum(address)) {
                byte[] extCode = state.getExtCodeBytes(Z3Util.getBVNum(address));
                push(mkBVNum(Integer.toString(extCode.length)));
            } else {
                log.warn("不明なEXTCODESIZE");
                push(mkBVConst(String.format("EXTCODESIZE(%s)", Util.exprToString(address))));
            }
        } else if (opCode.equals(OpCode.EXTCODECOPY)) {
            BitVecExpr address = pop();
            BitVecExpr to = pop();
            BitVecExpr from = pop();
            BitVecExpr size = pop();
            if (Z3Util.isBVNum(size) && Z3Util.isBVNum(address)) {
                final BitVecNum sizeNum = Z3Util.getBVNum(size);
                BitVecExpr chunk = state.getExtCodeChunk(from, sizeNum, Z3Util.getBVNum(address));
                state.putMemoryChunk(to, sizeNum, chunk);
            } else if (Z3Util.isBVNum(address)) {
                int satSize = Z3Util.getSatSize(getContext(), getSolver(), size);
                if (satSize == -1) {
                    log.warn("不明なEXTCODECOPY サイズ");
                } else {
                    log.debug("SATサイズ: {}", satSize);
                    final BitVecNum tmpSize = mkBVNum(Integer.toString(satSize));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), size, tmpSize);
                    addToSolver(constraint);
                    BitVecExpr chunk = state.getExtCodeChunk(from, tmpSize, Z3Util.getBVNum(address));
                    state.putMemoryChunk(to, tmpSize, chunk);
                }
            } else {
                log.warn("不明なEXTCODECOPY アドレス");
            }
        } else if (opCode.equals(OpCode.BLOCKHASH)) {
            BitVecExpr blocknumberExpr = pop();
            push(mkBVConst(String.format("BLOCKHASH(%s)", Util.exprToString(blocknumberExpr))));
        } else if (opCode.equals(OpCode.COINBASE)) {
            push(mkBVConst("COINBASE"));
        } else if (opCode.equals(OpCode.TIMESTAMP)) {
            push(mkBVConst("TIMESTAMP"));
        } else if (opCode.equals(OpCode.NUMBER)) {
            push(mkBVConst("NUMBER"));
        } else if (opCode.equals(OpCode.DIFFICULTY)) {
            push(mkBVConst("DIFFICULTY"));
        } else if (opCode.equals(OpCode.GASLIMIT)) {
            push(mkBVConst("GASLIMIT"));
        } else if (opCode.equals(OpCode.POP)) {
            pop();
        } else if (opCode.equals(OpCode.MLOAD)) {
            BitVecExpr from = pop();
            BitVecExpr chunk = state.getMemoryChunk(from, mkBVNum("32"));
            push(chunk);
        } else if (opCode.equals(OpCode.MSTORE)) {
            BitVecExpr to = pop();
            BitVecExpr value = pop();
            state.putMemoryChunk(to, mkBVNum("32"), value);
        } else if (opCode.equals(OpCode.MSTORE8)) {
            BitVecExpr to = pop();
            BitVecExpr value = pop();
            state.putMemoryChunk(to, mkBVNum("1"), Z3Util.mkExtract(getContext(), 7, 0, value));
        } else if (opCode.equals(OpCode.SLOAD)) {
            BitVecExpr key = pop();
            BitVecExpr value = state.getStorageValue(key);
            if (value == null) {
                log.warn("不明なSLOAD");
                push(mkBVNum("0"));
            } else {
                push(value);
            }
        } else if (opCode.equals(OpCode.SSTORE)) {
            BitVecExpr key = pop();
            BitVecExpr value = pop();

            if (state.isStaticcalling()) {
                state.revert(null);
                return;
            }

            newThreadForOverflowKey(key, value);
            state.putStorageValue(key, value);
        } else if (opCode.equals(OpCode.JUMP)) {
            BitVecExpr targetExpr = pop();
            if (Z3Util.isBVNum(targetExpr)) {
                int target = Util.getInt(Z3Util.getBVNum(targetExpr));
                Instruction nextInstruction = state.getInstruction(target);
                if (nextInstruction == null || OpCode.JUMPDEST != nextInstruction.getOpCode()) {
                    log.debug("ジャンプ先がJUMPDESTでない");
                    state.invalid();
                    return;
                } else if (isOverLoopLimit(target)) {
                    log.warn("ループ上限: {} -> {}", state.getPc(), target);
                    shutdown();
                    return;
                } else {
                    state.setPc(target);
                    state.setDoVisitedCheck(true);
                    return;
                }
            } else {
                int satTarget = Z3Util.getSatJumpTarget(state, getContext(), getSolver(), targetExpr);
                if (satTarget == -1) {
                    log.warn("不明なJUMP ジャンプ先");
                    shutdown();
                    return;
                } else if (isOverLoopLimit(satTarget)) {
                    log.warn("ループ上限: {} -> {}", state.getPc(), satTarget);
                    shutdown();
                    return;
                } else {
                    log.debug("SATターゲット: {}", satTarget);
                    final BitVecNum tmpTarget = mkBVNum(Integer.toString(satTarget));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), targetExpr, tmpTarget);
                    addToSolver(constraint);
                    state.setPc(satTarget);
                    state.setDoVisitedCheck(true);
                    return;
                }
            }
        } else if (opCode.equals(OpCode.JUMPI)) {
            BitVecExpr target = pop();
            BitVecExpr condition = pop();

            int trueCaseTarget;
            if (Z3Util.isBVNum(target)) {
                trueCaseTarget = Util.getInt(Z3Util.getBVNum(target));
            } else {
                trueCaseTarget = Z3Util.getSatJumpTarget(state, getContext(), getSolver(), target);
                if (trueCaseTarget >= 0) {
                    log.debug("SATターゲット: {}", trueCaseTarget);
                    final BitVecNum tmpTarget = mkBVNum(Integer.toString(trueCaseTarget));
                    BoolExpr constraint = Z3Util.mkEq(getContext(), target, tmpTarget);
                    addToSolver(constraint);
                }
            }
            final BoolExpr falseCond = Z3Util.mkEq(getContext(), condition, mkBVNum("0"));
            final BoolExpr trueCond = Z3Util.mkNot(getContext(), falseCond);
            // false case
            boolean falseable = Z3Util.isAble(getSolver(), falseCond);
            // true case
            boolean trueable;
            if (falseable) {
                trueable = Z3Util.isAble(getSolver(), trueCond);
            } else {
                trueable = true;
            }
            if (trueable && falseable) {
                log.debug("true/falseの分岐");
                // false caseを別スレッドで実行
                MachineState stateCopy = state.copy();
                stateCopy.setPc(state.getPc() + 1);
                BoolExpr falseCondCopy = Z3Util.translate(falseCond, stateCopy.getContext());
                Z3Util.add(stateCopy.getSolver(), falseCondCopy);
                manager.executeMachine(new Machine(manager, stateCopy));
                // true caseを自スレッドで実行
                if (trueCaseTarget >= 0) {
                    if (isOverLoopLimit(trueCaseTarget)) {
                        // false caseのスレッドが生き残るのでOK
                        shutdown();
                        return;
                    } else {
                        addToSolver(trueCond);
                        state.setPc(trueCaseTarget);
                        state.setDoVisitedCheck(true);
                        return;
                    }
                } else {
                    log.warn("不明なJUMPI ジャンプ先");
                    shutdown();
                    return;
                }
            } else if (trueable) {
                log.debug("trueのみの分岐");
                if (trueCaseTarget >= 0) {
                    if (isOverLoopLimit(trueCaseTarget)) {
                        // 唯一のスレッドが終了するので警告を出す
                        log.warn("ループ上限: {} -> {}", state.getPc(), trueCaseTarget);
                        shutdown();
                        return;
                    } else {
                        addToSolver(trueCond);
                        state.setPc(trueCaseTarget);
                        state.setDoVisitedCheck(true);
                        return;
                    }
                } else {
                    log.warn("不明なJUMPI ジャンプ先");
                    shutdown();
                    return;
                }
            } else {
                log.debug("falseのみの分岐");
                addToSolver(falseCond);
                state.setPc(state.getPc() + 1);
                state.setDoVisitedCheck(true);
                return;
            }
        } else if (opCode.equals(OpCode.PC)) {
            push(mkBVNum(Integer.toString(state.getPc())));
        } else if (opCode.equals(OpCode.MSIZE)) {
            push(mkBVNum(Integer.toString(state.getMemoryAccessMaxIndex() / 8)));
        } else if (opCode.equals(OpCode.GAS)) {
            push(mkBVConst("GAS"));
        } else if (opCode.equals(OpCode.JUMPDEST)) {
            // nop
        } else if (opCode.equals(OpCode.PUSH1) ||
                opCode.equals(OpCode.PUSH2) ||
                opCode.equals(OpCode.PUSH3) ||
                opCode.equals(OpCode.PUSH4) ||
                opCode.equals(OpCode.PUSH5) ||
                opCode.equals(OpCode.PUSH6) ||
                opCode.equals(OpCode.PUSH7) ||
                opCode.equals(OpCode.PUSH8) ||
                opCode.equals(OpCode.PUSH9) ||
                opCode.equals(OpCode.PUSH10) ||
                opCode.equals(OpCode.PUSH11) ||
                opCode.equals(OpCode.PUSH12) ||
                opCode.equals(OpCode.PUSH13) ||
                opCode.equals(OpCode.PUSH14) ||
                opCode.equals(OpCode.PUSH15) ||
                opCode.equals(OpCode.PUSH16) ||
                opCode.equals(OpCode.PUSH17) ||
                opCode.equals(OpCode.PUSH18) ||
                opCode.equals(OpCode.PUSH19) ||
                opCode.equals(OpCode.PUSH20) ||
                opCode.equals(OpCode.PUSH21) ||
                opCode.equals(OpCode.PUSH22) ||
                opCode.equals(OpCode.PUSH23) ||
                opCode.equals(OpCode.PUSH24) ||
                opCode.equals(OpCode.PUSH25) ||
                opCode.equals(OpCode.PUSH26) ||
                opCode.equals(OpCode.PUSH27) ||
                opCode.equals(OpCode.PUSH28) ||
                opCode.equals(OpCode.PUSH29) ||
                opCode.equals(OpCode.PUSH30) ||
                opCode.equals(OpCode.PUSH31) ||
                opCode.equals(OpCode.PUSH32)) {
            push(mkBVNum(Util.hexToDecimal(instruction.getArgHex())));
        } else if (opCode.equals(OpCode.DUP1) ||
                opCode.equals(OpCode.DUP2) ||
                opCode.equals(OpCode.DUP3) ||
                opCode.equals(OpCode.DUP4) ||
                opCode.equals(OpCode.DUP5) ||
                opCode.equals(OpCode.DUP6) ||
                opCode.equals(OpCode.DUP7) ||
                opCode.equals(OpCode.DUP8) ||
                opCode.equals(OpCode.DUP9) ||
                opCode.equals(OpCode.DUP10) ||
                opCode.equals(OpCode.DUP11) ||
                opCode.equals(OpCode.DUP12) ||
                opCode.equals(OpCode.DUP13) ||
                opCode.equals(OpCode.DUP14) ||
                opCode.equals(OpCode.DUP15) ||
                opCode.equals(OpCode.DUP16)) {
            int num = (opCode.val() & 0xff) - 0x7f;
            state.stackDup(num - 1);
        } else if (opCode.equals(OpCode.SWAP1) ||
                opCode.equals(OpCode.SWAP2) ||
                opCode.equals(OpCode.SWAP3) ||
                opCode.equals(OpCode.SWAP4) ||
                opCode.equals(OpCode.SWAP5) ||
                opCode.equals(OpCode.SWAP6) ||
                opCode.equals(OpCode.SWAP7) ||
                opCode.equals(OpCode.SWAP8) ||
                opCode.equals(OpCode.SWAP9) ||
                opCode.equals(OpCode.SWAP10) ||
                opCode.equals(OpCode.SWAP11) ||
                opCode.equals(OpCode.SWAP12) ||
                opCode.equals(OpCode.SWAP13) ||
                opCode.equals(OpCode.SWAP14) ||
                opCode.equals(OpCode.SWAP15) ||
                opCode.equals(OpCode.SWAP16)) {
            int num = (opCode.val() & 0xff) - 0x8f;
            state.stackSwap(num);
        } else if (opCode.equals(OpCode.LOG0) ||
                opCode.equals(OpCode.LOG1) ||
                opCode.equals(OpCode.LOG2) ||
                opCode.equals(OpCode.LOG3) ||
                opCode.equals(OpCode.LOG4)) {
            int num = (opCode.val() & 0xff) - 0xa0;
            for (int j = 0; j < num + 2; j++) {
                pop();
            }
            if (state.isStaticcalling()) {
                state.revert(null);
                return;
            }
        } else if (opCode.equals(OpCode.CREATE)) {
            BitVecExpr value = pop();
            BitVecExpr from = pop();
            BitVecExpr size = pop();

            if (state.isStaticcalling()) {
                state.revert(null);
                return;
            }

            byte[] nonce = state.getNonce();
            byte[] senderAddress = ByteUtil.hexStringToBytes(state.getContextAddressHex());
            byte[] contractAddress = HashUtil.calcNewAddr(senderAddress, nonce);
            BitVecExpr chunk = getMemoryChunk(from, size);
            if (Z3Util.isBVNum(chunk)) {
                byte[] creationCodeBytes = Util.bitvecnumToBytes(Z3Util.getBVNum(chunk));
                state.create(contractAddress, creationCodeBytes, value);
                return;
            } else {
                log.warn("不明なCREATE");
                push(mkBVNum(Util.bytesToDecimal(contractAddress)));
                // nonceを+1する
                state.incrementCallerNonce();
            }
        } else if (opCode.equals(OpCode.CALL)) {
            CallChecker.check(this);
            // ReentrancyCheckerはstackを汚染するので最後にcheckする
            ReentrancyChecker.check(this);

            BitVecExpr gas = pop();
            BitVecExpr to = pop();
            BitVecExpr value = pop();
            BitVecExpr inFrom = pop();
            BitVecExpr inSize = pop();
            BitVecExpr outFrom = pop();
            BitVecExpr outSize = pop();

            if (state.isStaticcalling()) {
                addToSolver(Z3Util.mkEq(getContext(), value, mkBVNum("0")));
                if (!Z3Util.isSAT(getSolver())) {
                    state.revert(null);
                    return;
                }
            }

            if (ReentrancyChecker.callTargetIfProxy(this, to)) {
                return;
            }

            BitVecExpr data = getMemoryChunk(inFrom, inSize);
            if (Z3Util.isBVNum(to) && Z3Util.isBVNum(outSize)) {
                final BitVecNum toNum = Z3Util.getBVNum(to);
                final BitVecNum outSizeNum = Z3Util.getBVNum(outSize);

                if (isPrecomiledContract(toNum)) {
                    if (Z3Util.isBVNum(data)) {
                        BitVecNum dataNum = Z3Util.getBVNum(data);
                        // 1-8
                        int toInt = Util.getInt(toNum);
                        PrecompiledContracts.PrecompiledContract contract = PRECOMPILED_CONTRACTS[toInt - 1];
                        Pair<Boolean, byte[]> result = contract.execute(Util.bitvecnumToBytes(dataNum));
                        final byte[] outBytes = result.getRight();
                        if (outBytes != null && outBytes.length > 0) {
                            state.putMemoryChunk(outFrom, outSizeNum,
                                    Z3Util.mkBV(getContext(), Util.hexToDecimal(ByteUtil.toHexString(outBytes)),
                                            outBytes.length * 8));
                        }
                        final Boolean success = result.getLeft();
                        if (success) {
                            push(mkBVNum("1"));
                        } else {
                            push(mkBVNum("0"));
                        }
                    } else {
                        log.warn("不明なCALL");
                        final BitVecExpr expr = mkBVConst(
                                String.format("CALL(%s,%s,%s,%s,%s,%s,%s)", Util.exprToString(gas),
                                        Util.exprToString(to),
                                        Util.exprToString(value), Util.exprToString(inFrom), Util.exprToString(inSize),
                                        Util.exprToString(outFrom), Util.exprToString(outSize)));
                        addToSolver(Z3Util.mkBVULT(getContext(), expr, mkBVNum("2")));
                        push(expr);
                    }
                } else {
                    state.call(toNum, value, data, outFrom, outSizeNum);
                    return;
                }
            } else {
                log.warn("不明なCALL");
                final BitVecExpr expr = mkBVConst(
                        String.format("CALL(%s,%s,%s,%s,%s,%s,%s)", Util.exprToString(gas), Util.exprToString(to),
                                Util.exprToString(value), Util.exprToString(inFrom), Util.exprToString(inSize),
                                Util.exprToString(outFrom), Util.exprToString(outSize)));
                addToSolver(Z3Util.mkBVULT(getContext(), expr, mkBVNum("2")));
                push(expr);
            }
        } else if (opCode.equals(OpCode.CALLCODE)) {
            DelegatecalChecker.check(this, opCode);

            BitVecExpr gas = pop();
            BitVecExpr to = pop();
            // contextAddressからcontextAddressへ送金する
            BitVecExpr value = pop();
            BitVecExpr inFrom = pop();
            BitVecExpr inSize = pop();
            BitVecExpr outFrom = pop();
            BitVecExpr outSize = pop();

            BitVecExpr data = getMemoryChunk(inFrom, inSize);
            if (Z3Util.isBVNum(to) && Z3Util.isBVNum(outSize)) {
                state.callcode(Z3Util.getBVNum(to), value, data, outFrom, Z3Util.getBVNum(outSize));
                return;
            } else {
                log.warn("不明なCALLCODE");
                final BitVecExpr expr = mkBVConst(
                        String.format("CALLCODE(%s,%s,%s,%s,%s,%s,%s)", Util.exprToString(gas), Util.exprToString(to),
                                Util.exprToString(value), Util.exprToString(inFrom), Util.exprToString(inSize),
                                Util.exprToString(outFrom), Util.exprToString(outSize)));
                addToSolver(Z3Util.mkBVULT(getContext(), expr, mkBVNum("2")));
                push(expr);
            }
        } else if (opCode.equals(OpCode.RETURN)) {
            BitVecExpr from = pop();
            BitVecExpr size = pop();
            BitVecExpr chunk = getMemoryChunk(from, size);
            ERC20Checker.check(this, chunk);
            state.return_(chunk);
            return;
        } else if (opCode.equals(OpCode.DELEGATECALL)) {
            DelegatecalChecker.check(this, opCode);

            BitVecExpr gas = pop();
            BitVecExpr to = pop();
            BitVecExpr inFrom = pop();
            BitVecExpr inSize = pop();
            BitVecExpr outFrom = pop();
            BitVecExpr outSize = pop();

            BitVecExpr data = getMemoryChunk(inFrom, inSize);
            if (Z3Util.isBVNum(to) && Z3Util.isBVNum(outSize)) {
                state.delegatecall(Z3Util.getBVNum(to), data, outFrom, Z3Util.getBVNum(outSize));
                return;
            } else {
                log.warn("不明なDELEGATECALL");
                final BitVecExpr expr = mkBVConst(
                        String.format("DELEGATECALL(%s,%s,%s,%s,%s,%s)", Util.exprToString(gas), Util.exprToString(to),
                                Util.exprToString(inFrom), Util.exprToString(inSize),
                                Util.exprToString(outFrom), Util.exprToString(outSize)));
                addToSolver(Z3Util.mkBVULT(getContext(), expr, mkBVNum("2")));
                push(expr);
            }
        } else if (opCode.equals(OpCode.STATICCALL)) {
            BitVecExpr gas = pop();
            BitVecExpr to = pop();
            BitVecExpr inFrom = pop();
            BitVecExpr inSize = pop();
            BitVecExpr outFrom = pop();
            BitVecExpr outSize = pop();

            BitVecExpr data = getMemoryChunk(inFrom, inSize);
            if (Z3Util.isBVNum(to) && Z3Util.isBVNum(outSize)) {
                state.staticcall(Z3Util.getBVNum(to), data, outFrom, Z3Util.getBVNum(outSize));
                return;
            } else {
                log.warn("不明なSTATICCALL");
                final BitVecExpr expr = mkBVConst(
                        String.format("STATICCALL(%s,%s,%s,%s,%s,%s)", Util.exprToString(gas), Util.exprToString(to),
                                Util.exprToString(inFrom), Util.exprToString(inSize),
                                Util.exprToString(outFrom), Util.exprToString(outSize)));
                addToSolver(Z3Util.mkBVULT(getContext(), expr, mkBVNum("2")));
                push(expr);
            }
        } else if (opCode.equals(OpCode.REVERT)) {
            BitVecExpr from = pop();
            BitVecExpr size = pop();
            BitVecExpr chunk = getMemoryChunk(from, size);
            state.revert(chunk);
            return;
        } else if (opCode.equals(OpCode.SUICIDE)) {
            // 検査を実行
            SuicideChecker.check(this);

            BitVecExpr address = pop();

            if (state.isStaticcalling()) {
                state.revert(null);
                return;
            }

            state.stop();
            return;
        } else if (opCode.equals(OpCode.EXTCODEHASH)) {
            BitVecExpr address = pop();
            if (Z3Util.isBVNum(address)) {
                byte[] extCodeBytes = state.getExtCodeBytes(Z3Util.getBVNum(address));
                byte[] encoded = HashUtil.sha3(extCodeBytes);
                push(mkBVNum(Util.bytesToDecimal(encoded)));
            } else {
                log.warn("不明なEXTCODEHASH");
                push(mkBVConst(String.format("EXTCODEHASH(%s)", Util.exprToString(address))));
            }
        } else if (opCode.equals(OpCode.CREATE2)) {
            BitVecExpr value = pop();
            BitVecExpr from = pop();
            BitVecExpr size = pop();
            BitVecExpr salt = pop();

            if (state.isStaticcalling()) {
                state.revert(null);
                return;
            }

            BitVecExpr chunk = getMemoryChunk(from, size);
            if (Z3Util.isBVNum(chunk) && Z3Util.isBVNum(salt)) {
                byte[] senderAddress = ByteUtil.hexStringToBytes(state.getContextAddressHex());
                byte[] creationCodeBytes = Util.bitvecnumToBytes(Z3Util.getBVNum(chunk));
                byte[] saltBytes = Util.bitvecnumToBytes(Z3Util.getBVNum(salt));
                byte[] contractAddress = HashUtil.calcSaltAddr(senderAddress, creationCodeBytes, saltBytes);
                state.create(contractAddress, creationCodeBytes, value);
                return;
            } else {
                log.warn("不明なCREATE2");
                push(mkBVConst(String.format("CREATE2(%s,%s,%s,%s)", Util.exprToString(value), Util.exprToString(from),
                        Util.exprToString(size), Util.exprToString(salt))));
                // nonceを+1する
                state.incrementCallerNonce();
            }
        } else if (opCode.equals(OpCode.SHL)) {
            BitVecExpr word1 = pop();
            BitVecExpr word2 = pop();
            // word2 << word1
            push(Z3Util.mkBVSHL(getContext(), word2, word1));
        } else if (opCode.equals(OpCode.SHR)) {
            BitVecExpr word1 = pop();
            BitVecExpr word2 = pop();
            // word2 >> word1
            push(Z3Util.mkBVLSHR(getContext(), word2, word1));
        } else if (opCode.equals(OpCode.SAR)) {
            BitVecExpr word1 = pop();
            BitVecExpr word2 = pop();
            // word2 >> word1
            push(Z3Util.mkBVASHR(getContext(), word2, word1));
        } else {
            // ここには来ない
            throw new RuntimeException("不明な命令: " + instruction);
        }

        int endStackSize = state.stackSize();
        if (endStackSize - startStackSize != opCode.ret() - opCode.require()) {
            throw new RuntimeException("異常なスタック: " + instruction);
        }

        state.setPc(state.getPc() + 1 + instruction.getArgHex().length() / 2);
    }

    private boolean isOverLoopLimit(int target) {
        // 戻るジャンプを制限する
        return target < state.getPc() &&
                state.checkAndIncrementEdgeVisitedCount(state.getPc(), target, manager.getMaxEdgeVisitedCount());
    }

    private boolean isAddressMask(BitVecExpr a) {
        return Z3Util.isBVNum(a) &&
                ADDRESS_MASK.equals(Util.normalizeAddress(Util.bitvecnumToHex(Z3Util.getBVNum(a))));
    }

    private boolean isPrecomiledContract(BitVecNum toNum) {
        BigInteger toBi = toNum.getBigInteger();
        // 1-8
        return BigInteger.ZERO.compareTo(toBi) < 0 && toBi.compareTo(BigInteger.valueOf(9)) < 0;
    }

    private void shutdown() {
        state.setVmStopped(true);
        state.setTxsStopped(true);
    }

    private void newThreadForDynamicArray(BitVecExpr size) {
        if (Z3Util.isBVNum(size)) {
            return;
        }
        if (manager.isLongSizeDynamicArray()) {
            newThreadForDynamicArray(size, LONG_ARRAY_SIZE);
        }
    }

    private void newThreadForDynamicArray(BitVecExpr size, String hex) {
        final Solver solver = getSolver();
        final BitVecNum tmpSize = mkBVNum(Util.hexToDecimal(hex));
        BoolExpr boolExpr = Z3Util.mkEq(getContext(), size, tmpSize);
        solver.push();
        boolean ok;
        try {
            Z3Util.add(solver, boolExpr);
            ok = Z3Util.isSAT(solver);
        } finally {
            solver.pop();
        }
        if (ok) {
            log.debug("動的配列: 0x{}", hex);
            MachineState stateCopy = state.copy();
            final BoolExpr boolExprCopy = Z3Util.translate(boolExpr, stateCopy.getContext());
            Z3Util.add(stateCopy.getSolver(), boolExprCopy);
            // PC、スタック、メモリ、ストレージがオリジナルと同じであるため、このままでは重複判定で除外されてしまう。
            // これを避けるためにhashを導入して、更新する。
            stateCopy.updateConstraintHash(boolExprCopy);
            stateCopy.setPc(state.getPc() + 1);
            // 別スレッドで実行
            manager.executeMachine(new Machine(manager, stateCopy));
        }
    }

    private void newThreadForOverflowKey(BitVecExpr key, BitVecExpr value) {
        if (Z3Util.isBVNum(key)) {
            return;
        }
        // https://solidity.readthedocs.io/en/v0.4.21/miscellaneous.html
        // Array data is located at keccak256(p)
        for (int i = 0; i < Constants.MAX_OVERFLOW_STORAGE_KEY; i++) {
            boolean ok;
            final BitVecNum iNum = mkBVNum(Integer.toString(i));
            final BoolExpr boolExpr = Z3Util.mkEq(getContext(), key, iNum);
            getSolver().push();
            try {
                Z3Util.add(getSolver(), boolExpr);
                ok = Z3Util.isSAT(getSolver());
            } finally {
                getSolver().pop();
            }
            if (ok) {
                log.debug("操作可能なストレージキー: {}", i);
                MachineState stateCopy = state.copy();
                final BoolExpr boolExprCopy = Z3Util.translate(boolExpr, stateCopy.getContext());
                Z3Util.add(stateCopy.getSolver(), boolExprCopy);
                BitVecExpr iNumCopy = Z3Util.translate(iNum, stateCopy.getContext());
                BitVecExpr valueCopy = Z3Util.translate(value, stateCopy.getContext());
                stateCopy.putStorageValue(iNumCopy, valueCopy);
                stateCopy.setPc(state.getPc() + 1);
                // 別スレッドで実行
                manager.executeMachine(new Machine(manager, stateCopy));
            }
        }
    }

    private boolean newThreadForMapping(BitVecExpr chunk) {
        boolean newThread = false;
        if (Z3Util.isBVNum(chunk)) {
            return newThread;
        }
        if (chunk.getSortSize() / 8 == 64) { // if 64 bytes
            // https://solidity.readthedocs.io/en/v0.4.21/miscellaneous.html
            // value corresponding to a mapping key k is located at keccak256(k . p) where . is concatenation.
            BitVecExpr key = state.getChunk(mkBVNum("0"), mkBVNum("32"), chunk);
            BitVecExpr position = state.getChunk(mkBVNum("32"), mkBVNum("32"), chunk);
            if (!Z3Util.isBVNum(key) && Z3Util.isBVNum(position)) {
                BitVecNum positionNum = Z3Util.getBVNum(position);
                newThread |= newThreadForMapping(key, positionNum, state.getOriginAddressHex());
                if (state.isUsingProxy()) {
                    newThread |= newThreadForMapping(key, positionNum, manager.getProxyAddressHex());
                }
                if (manager.isMappingKeyToZero()) {
                    newThread |= newThreadForMapping(key, positionNum, "0");
                }
            }
        }
        return newThread;
    }

    private boolean newThreadForMapping(BitVecExpr key, BitVecNum position, String targetHex) {
        final BitVecNum targetNum = mkBVNum(Util.hexToDecimal(targetHex));
        final BoolExpr boolExpr = Z3Util.mkEq(getContext(), key, targetNum);
        BitVecNum sha3Num = null;
        getSolver().push();
        try {
            Z3Util.add(getSolver(), boolExpr);
            if (Z3Util.isSAT(getSolver())) {
                String hex = Util.bitvecnumToHex(targetNum) + Util.bitvecnumToHex(position);
                if (hex.length() / 2 != 64) {
                    throw new RuntimeException("異常な長さ: " + hex.length() / 2);
                }
                byte[] encoded = HashUtil.sha3(ByteUtil.hexStringToBytes(hex));
                sha3Num = mkBVNum(Util.bytesToDecimal(encoded));
            }
        } finally {
            getSolver().pop();
        }
        if (sha3Num != null) {
            log.debug("mapping型のキー: {}", targetHex);
            MachineState stateCopy = state.copy();
            final BoolExpr boolExprCopy = Z3Util.translate(boolExpr, stateCopy.getContext());
            Z3Util.add(stateCopy.getSolver(), boolExprCopy);
            // SHA3の結果をpush
            final BitVecExpr sha3NumCopy = Z3Util.translate(sha3Num, stateCopy.getContext());
            stateCopy.stackPush(sha3NumCopy);
            // SHA3の次の命令に進める
            stateCopy.setPc(state.getPc() + 1);
            // 別スレッドで実行
            manager.executeMachine(new Machine(manager, stateCopy));
            return true;
        }
        return false;
    }


    private void newThreadForAddress(BitVecExpr value) {
        if (Z3Util.isBVNum(value)) {
            return;
        }
        String addressHex;
        if (state.isUsingProxy()) {
            addressHex = manager.getProxyAddressHex();
        } else {
            addressHex = state.getOriginAddressHex();
        }
        final BitVecNum addressNum = mkBVNum(Util.hexToDecimal(addressHex));
        final BoolExpr boolExpr = Z3Util.mkEq(getContext(), value, addressNum);

        getSolver().push();
        boolean ok;
        try {
            Z3Util.add(getSolver(), boolExpr);
            ok = Z3Util.isSAT(getSolver());
        } finally {
            getSolver().pop();
        }
        if (ok) {
            log.debug("address型の値");
            MachineState stateCopy = state.copy();
            final BoolExpr boolExprCopy = Z3Util.translate(boolExpr, stateCopy.getContext());
            Z3Util.add(stateCopy.getSolver(), boolExprCopy);
            // ANDの結果をpush
            final BitVecExpr addressNumCopy = Z3Util.translate(addressNum, stateCopy.getContext());
            stateCopy.stackPush(addressNumCopy);
            // ANDの次の命令に進める
            stateCopy.setPc(state.getPc() + 1);
            // 別スレッドで実行
            manager.executeMachine(new Machine(manager, stateCopy));
        }
    }

    private BitVecExpr getMemoryChunk(BitVecExpr from, BitVecExpr size) {
        BitVecExpr chunk = null;
        if (Z3Util.isBVNum(size)) {
            chunk = state.getMemoryChunk(from, Z3Util.getBVNum(size));
        } else {
            int satSize = Z3Util.getSatSize(getContext(), getSolver(), size);
            if (satSize == -1) {
                log.warn("不明なgetMemoryChunk サイズ");
            } else {
                log.debug("SATサイズ: {}", satSize);
                final BitVecNum tmpSize = mkBVNum(Integer.toString(satSize));
                BoolExpr constraint = Z3Util.mkEq(getContext(), size, tmpSize);
                addToSolver(constraint);
                chunk = state.getMemoryChunk(from, tmpSize);
            }
        }
        return chunk;
    }

    private void logTrace(Instruction instruction, int nextPc) {
        if (instruction == null) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace(logString,
                    String.format("%5s", "[" + nextPc + "]"),
                    String.format("%-12s", instruction.getOpCode()),
                    0,
                    state.getCallStackSize(),
                    instruction.getArgHex()
            );
            log.trace(" -- OPS --     \n{}", getOpsLine());
            log.trace(" -- STACK --   \n{}", getStackLines());
//            log.trace(" -- MEMORY --  \n{}", getMemoryLine());
        }
    }

    private String getOpsLine() {
        StringBuilder opsString = new StringBuilder();
        opsString.append(" ");
        final byte[] ops = state.getCodeBytes();
        final int pc = state.getPc();
        for (int i = 0; i < ops.length; ++i) {
            String tmpString = Integer.toString(ops[i] & 0xFF, 16);
            tmpString = tmpString.length() == 1 ? "0" + tmpString : tmpString;
            if (i != pc) {
                opsString.append(tmpString);
            } else {
                opsString.append(" >>").append(tmpString);
            }
        }
        if (pc >= ops.length) opsString.append(" >>");
        return opsString.toString();
    }

    // TODO 遅い
    private String getMemoryLine() {
        StringBuilder memoryData = new StringBuilder();
        StringBuilder oneLine = new StringBuilder();
        final int softSize = state.getMemoryAccessMaxIndex();
        for (int i = 0; i < softSize; ++i) {
            BitVecExpr valueExpr = state.getMemoryChunk(mkBVNum(Integer.toString(i)), mkBVNum("1"));
            byte value = 0;
            if (Z3Util.isBVNum(valueExpr)) {
                value = (byte) Util.getInt(Z3Util.getBVNum(valueExpr));
            }
            oneLine.append(ByteUtil.oneByteToHexString(value)).append(" ");
            if ((i + 1) % 16 == 0) {
                String tmp = format("[%4s]-[%4s]", Integer.toString(i - 15, 16),
                        Integer.toString(i, 16)).replace(" ", "0");
                memoryData.append(tmp).append(" ");
                memoryData.append(oneLine);
                if (i < softSize - 1) {
                    memoryData.append("\n");
                }
                oneLine.setLength(0);
            }
        }
        return memoryData.toString();
    }

    private String getStackLines() {
        StringBuilder sb = new StringBuilder();
        final int size = state.stackSize();
        for (int i = 0; i < size; i++) {
            BitVecExpr expr = state.stackGet(i);
            if (Z3Util.isBVNum(expr)) {
                sb.append(" ").append(Util.bitvecnumToHex(Z3Util.getBVNum(expr)));
            } else {
                String str = expr.toString().replace("\n", "\\n");
                if (str.length() > 64) {
                    str = str.substring(0, 61) + "...";
                }
                sb.append(" ").append(str);
            }
            if (i < size - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String getDisasmLine(Instruction instruction) {
        // デバッグしやすくするためにevmと同じ表示形式にする
        String indent = new String(new char[state.getCallStackSize()]).replace("\0", "| ");
        String addressHex = state.getCodeAddressHex().substring(0, 4);
        return String.format("%s%s %05x: %s", indent, addressHex, state.getPc(), instruction);
    }

    private BitVecExpr pop() {
        return state.stackPop();
    }

    private void push(BitVecExpr e) {
        state.stackPush(e);
    }

    private Solver getSolver() {
        return state.getSolver();
    }


    private Context getContext() {
        return state.getContext();
    }

    private BitVecNum mkBVNum(String i_str) {
        return Z3Util.mkBV(getContext(), i_str, 256);
    }

    private BitVecExpr mkBVConst(String name) {
        return Z3Util.mkBVConst(getContext(), name, 256);
    }

    private BitVecExpr mkITE(BoolExpr condition, BitVecExpr trueExpr, BitVecExpr falseExpr) {
        return Z3Util.mkITE(getContext(), condition, trueExpr, falseExpr);
    }

    private void addToSolver(BoolExpr constraint) {
        Z3Util.add(getSolver(), constraint);
    }

}
