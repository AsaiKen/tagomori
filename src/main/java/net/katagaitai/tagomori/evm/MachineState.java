package net.katagaitai.tagomori.evm;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.microsoft.z3.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.poc.Position;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import net.katagaitai.tagomori.util.Z3Util;
import org.ethereum.core.AccountState;
import org.ethereum.db.RepositoryRoot;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j(topic = "tagomori")
@EqualsAndHashCode
@AllArgsConstructor
public class MachineState {
    public static final byte[] EMPTY_BYTES = new byte[0];
    public static final int INITIAL_UNUSED_DATA_OFFSET = Constants.DATA_BITS / 8 / 2;

    public void newReentrancyInput() {
        if (globalState.reentrancyInput != null) {
            return;
        }
        globalState.reentrancyInput = new UserInput(
                new Data(Z3Util.mkBVConst(globalState.context, "REENTRANCY_DATA", Constants.DATA_BITS)),
                new Value(Z3Util.mkBVConst(globalState.context, "REENTRANCY_VALUE", 256))
        );
    }

    public Data getReentrancyData() {
        if (globalState.reentrancyInput == null) {
            return null;
        }
        return globalState.reentrancyInput.getData();
    }

    public Value getReentrancyValue() {
        if (globalState.reentrancyInput == null) {
            return null;
        }
        return globalState.reentrancyInput.getValue();
    }

    public String getTargetAddressHex() {
        if (callerStateStack.size() == 0) {
            return currentState.contextAddressHex;
        } else {
            return callerStateStack.getLast().contextAddressHex;
        }
    }

    public boolean callStackContains(String proxyAddressHex) {
        for (CallerState cs : callerStateStack) {
            if (cs.contextAddressHex.equals(proxyAddressHex)) {
                return true;
            }
        }
        return false;
    }

    public void setCallerAddressHex(String addressHex) {
        currentState.callerAddressHex = Util.normalizeAddress(addressHex);
    }


    public int getPc() {
        return currentState.pc;
    }

    public void setPc(int pc) {
        currentState.pc = pc;
    }

    public boolean isVmStopped() {
        return globalState.vmStopped;
    }

    public String getContextAddressHex() {
        return currentState.contextAddressHex;
    }

    public String getCallerAddressHex() {
        return currentState.callerAddressHex;
    }

    public Value getValue() {
        return currentState.value;
    }

    public ReturnData getReturnData() {
        return currentState.returnData;
    }

    public Context getContext() {
        return globalState.context;
    }

    public Solver getSolver() {
        return globalState.solver;
    }

    public void setVmStopped(boolean b) {
        globalState.vmStopped = b;
    }

    public int getMemoryAccessMaxIndex() {
        return currentState.memoryAccessMaxIndex;
    }

    public void invalid() {
        revert(null);
    }


    public void revert(BitVecExpr expr) {
        if (callerStateStack.size() > 0) {
            final CallerState prevState = callerStateStack.peek();
            // 戻り値をpush
            // CALLもCREATEも0をpushする
            prevState.stack.push(Z3Util.mkBV(globalState.context, 0, 256));
        }
        if (callerStateStack.size() == 0) {
            setVmStopped(true);
            // 次のtxは現在のtxと同一の結果になるので、実施不要
            setTxsStopped(true);
            return;
        } else {
            CallerState tmp = currentState;
            // 今のaccountsは消えるので、前の状態に戻すだけでよい
            currentState = callerStateStack.pop();
            // returnDataにexprを保存
            currentState.returnData = new ReturnData(expr);
            if (tmp.calling != null) {
                // memoryにexprを保存
                putMemoryChunk(tmp.calling.outFrom, tmp.calling.outSize, expr);
            }
            return;
        }
    }

    public void stop() {
        if (callerStateStack.size() > 0) {
            final CallerState prevState = callerStateStack.peek();
            if (currentState.calling != null) {
                // CALLの場合
                // 戻り値をpush
                prevState.stack.push(Z3Util.mkBV(globalState.context, 1, 256));
            } else if (currentState.creating != null) {
                // CREATEの場合
                String contractAddressHex = currentState.creating.addressHex;
                createAccountIfNotExist(contractAddressHex);
                // コードは空
                // 戻り値をpush
                prevState.stack.push(Z3Util.mkBV(globalState.context, Util.hexToDecimal(contractAddressHex), 256));
            }
        }
        if (callerStateStack.size() == 0) {
            // currentStateはそのまま残す
            setVmStopped(true);
            return;
        } else {
            // 今のaccountsは残るので、前の状態にセットする
            CallerState prevState = callerStateStack.pop();
            prevState.accounts = currentState.accounts;
            currentState = prevState;
            return;
        }
    }

    public void return_(BitVecExpr expr) {
        if (callerStateStack.size() > 0) {
            final CallerState prevState = callerStateStack.peek();
            if (currentState.calling != null) {
                // CALLの場合
                // 戻り値をpush
                prevState.stack.push(Z3Util.mkBV(globalState.context, 1, 256));
            } else if (currentState.creating != null) {
                // CREATEの場合
                String contractAddressHex = currentState.creating.addressHex;
                createAccountIfNotExist(contractAddressHex);
                if (Z3Util.isBVNum(expr)) {
                    // コードをセット
                    currentState.accounts.get(contractAddressHex)
                            .setCode(new Code(Util.bitvecnumToBytes(Z3Util.getBVNum(expr))));
                } else {
                    log.debug("不明なコード: {}", expr);
                }
                // 戻り値をpush
                prevState.stack.push(Z3Util.mkBV(globalState.context, Util.hexToDecimal(contractAddressHex), 256));
            }
        }
        if (callerStateStack.size() == 0) {
            // currentStateは残す
            setVmStopped(true);
            return;
        } else {
            CallerState tmp = currentState;
            // 今のaccountsは残るので、前の状態にセットする
            CallerState prevState = callerStateStack.pop();
            prevState.accounts = tmp.accounts;
            // returnDataにexprを保存
            prevState.returnData = new ReturnData(expr);
            currentState = prevState;
            if (tmp.calling != null) {
                // memoryにexprを保存
                putMemoryChunk(tmp.calling.outFrom, tmp.calling.outSize, expr);
            }
            return;
        }

    }

    public String getCodeAddressHex() {
        return currentState.codeAddressHex;
    }

    public void setRepository(RepositoryRoot repository) {
        globalState.repository = repository;
    }

    public String getOriginAddressHex() {
        return globalState.originAddressHex;
    }

    public UserInputHistory getUserInputHistory() {
        return globalState.userInputHistory;
    }

    public void setTxsStopped(boolean b) {
        globalState.txsStopped = b;
    }

    public boolean isTxsStopped() {
        return globalState.txsStopped;
    }

    public byte[] getNonce() {
        final String addressHex = currentState.contextAddressHex;
        final Account account = currentState.accounts.get(addressHex);
        BigInteger nonceBi;
        if (account == null) {
            final byte[] address = ByteUtil.hexStringToBytes(addressHex);
            nonceBi = globalState.repository.getNonce(address);
        } else {
            nonceBi = account.getNonce();
        }
        return ByteUtil.bigIntegerToBytes(nonceBi);
    }

    public void incrementCallerNonce() {
        String addressHex = currentState.callerAddressHex;
        createAccountIfNotExist(addressHex);
        final Account account = currentState.accounts.get(addressHex);
        account.setNonce(account.getNonce().add(BigInteger.ONE));
    }

    public void create(byte[] contractAddress, byte[] creationCodeBytes, BitVecExpr value) {
        String contractAddressHex = Util.normalizeAddress(ByteUtil.toHexString(contractAddress));
        CallerState newState = CallerState.builder()
                .contextAddressHex(contractAddressHex)
                .codeAddressHex(contractAddressHex)
                .callerAddressHex(currentState.contextAddressHex)
                .returnData(new ReturnData(null))
                .stack(new Stack())
                .memory(new Memory(globalState.context))
                .data(new Data(null))
                .value(new Value(value))
                .edgeVisitedCounts(Maps.newHashMap())
                .accounts(copyAccounts(currentState.accounts, globalState.context))
                .creating(new Creating(contractAddressHex, new Code(creationCodeBytes)))
                .staticcalling(currentState.staticcalling)
                .build();
        // PCをCREATEの次にしておく
        currentState.pc += 1;
        shutdownIfMaxCallStackSize();
        callerStateStack.push(currentState);
        currentState = newState;
        // nonceを+1する
        incrementCallerNonce();
    }

    private void shutdownIfMaxCallStackSize() {
        if (callerStateStack.size() >= Constants.MAX_CALLSTACK_SIZE) {
            log.debug("コールスタック上限");
            globalState.vmStopped = true;
            globalState.txsStopped = true;
        }
    }

    public void call(BitVecNum to, BitVecExpr value, BitVecExpr data, BitVecExpr outFrom, BitVecNum outSize) {
        String toHex = Util.normalizeAddress(Util.bitvecnumToHex(to));
        CallerState newState = CallerState.builder()
                .contextAddressHex(toHex)
                .codeAddressHex(toHex)
                .callerAddressHex(currentState.contextAddressHex)
                .returnData(new ReturnData(null))
                .stack(new Stack())
                .memory(new Memory(globalState.context))
                .data(new Data(data))
                .value(new Value(value))
                .edgeVisitedCounts(Maps.newHashMap())
                .accounts(copyAccounts(currentState.accounts, globalState.context))
                .calling(new Calling(outFrom, outSize))
                .staticcalling(currentState.staticcalling)
                .build();
        // PCをCALLの次にしておく
        currentState.pc += 1;
        shutdownIfMaxCallStackSize();
        callerStateStack.push(currentState);
        currentState = newState;
    }

    public void callcode(BitVecNum to, BitVecExpr value, BitVecExpr data, BitVecExpr outFrom, BitVecNum outSize) {
        String toHex = Util.normalizeAddress(Util.bitvecnumToHex(to));
        CallerState newState = CallerState.builder()
                // contextAddressHexは現在のまま
                .contextAddressHex(currentState.contextAddressHex)
                .codeAddressHex(toHex)
                // callerAddressHex(==msg.sender)は変わる
                .callerAddressHex(currentState.contextAddressHex)
                .returnData(new ReturnData(null))
                .stack(new Stack())
                .memory(new Memory(globalState.context))
                .data(new Data(data))
                .value(new Value(value))
                .edgeVisitedCounts(Maps.newHashMap())
                .accounts(copyAccounts(currentState.accounts, globalState.context))
                .calling(new Calling(outFrom, outSize))
                .staticcalling(currentState.staticcalling)
                .build();
        // PCをCALLの次にしておく
        currentState.pc += 1;
        shutdownIfMaxCallStackSize();
        callerStateStack.push(currentState);
        currentState = newState;
    }

    public void delegatecall(BitVecNum to, BitVecExpr data, BitVecExpr outFrom, BitVecNum outSize) {
        String toHex = Util.normalizeAddress(Util.bitvecnumToHex(to));
        CallerState newState = CallerState.builder()
                // contextAddressHexは現在のまま
                .contextAddressHex(currentState.contextAddressHex)
                .codeAddressHex(toHex)
                // callerAddressHex(==msg.sender)は現在のまま
                .callerAddressHex(currentState.callerAddressHex)
                .returnData(new ReturnData(null))
                .stack(new Stack())
                .memory(new Memory(globalState.context))
                .data(new Data(data))
                // valueは現在のまま
                .value(currentState.value)
                .edgeVisitedCounts(Maps.newHashMap())
                .accounts(copyAccounts(currentState.accounts, globalState.context))
                .calling(new Calling(outFrom, outSize))
                .staticcalling(currentState.staticcalling)
                .build();
        // PCをCALLの次にしておく
        currentState.pc += 1;
        shutdownIfMaxCallStackSize();
        callerStateStack.push(currentState);
        currentState = newState;
    }

    public void staticcall(BitVecNum to, BitVecExpr data, BitVecExpr outFrom, BitVecNum outSize) {
        String toHex = Util.normalizeAddress(Util.bitvecnumToHex(to));
        CallerState newState = CallerState.builder()
                .contextAddressHex(toHex)
                .codeAddressHex(toHex)
                .callerAddressHex(currentState.contextAddressHex)
                .returnData(new ReturnData(null))
                .stack(new Stack())
                .memory(new Memory(globalState.context))
                .data(new Data(data))
                .value(new Value(null))
                .edgeVisitedCounts(Maps.newHashMap())
                .accounts(copyAccounts(currentState.accounts, globalState.context))
                .calling(new Calling(outFrom, outSize))
                .staticcalling(true)
                .build();
        // PCをCALLの次にしておく
        currentState.pc += 1;
        shutdownIfMaxCallStackSize();
        callerStateStack.push(currentState);
        currentState = newState;
    }

    public boolean isStaticcalling() {
        return currentState.staticcalling;
    }


    public Data getTargetData() {
        if (callerStateStack.size() == 0) {
            return currentState.data;
        } else {
            return callerStateStack.getLast().data;
        }
    }

    public Value getTargetValue() {
        if (callerStateStack.size() == 0) {
            return currentState.value;
        } else {
            return callerStateStack.getLast().value;
        }
    }

    public Code getCode() {
        final String addressHex = currentState.codeAddressHex;
        final Account account = currentState.accounts.get(addressHex);
        if (account == null) {
            if (!globalState.repoCodeCache.containsKey(addressHex)) {
                final byte[] address = ByteUtil.hexStringToBytes(addressHex);
                final byte[] codeBytes = globalState.repository.getCode(address);
                globalState.repoCodeCache.put(addressHex, new Code(codeBytes));
            }
            return globalState.repoCodeCache.get(addressHex);
        } else {
            return account.getCode();
        }
    }

    public boolean isUsingProxy() {
        if (callerStateStack.size() == 0) {
            return !globalState.originAddressHex.equals(currentState.callerAddressHex);
        } else {
            CallerState cs = callerStateStack.getLast();
            return !globalState.originAddressHex.equals(cs.callerAddressHex);
        }
    }

    public BigInteger getBalance(byte[] addresss) {
        return globalState.repository.getBalance(addresss);
    }

    public List<Value> getTxValues() {
        List<Value> result = Lists.newArrayList();
        for (int i = 0; i < globalState.userInputHistory.size(); i++) {
            result.add(globalState.userInputHistory.get(i).getValue());
        }
        if (callerStateStack.size() == 0) {
            result.add(currentState.value);
        } else {
            result.add(callerStateStack.getLast().value);
        }
        final Value reentrancyValue = getReentrancyValue();
        if (reentrancyValue != null) {
            result.add(reentrancyValue);
        }
        return result;
    }

    public void updateConstraintHash(BoolExpr expr) {
        globalState.constraintHash ^= expr.toString().hashCode();
    }

    public boolean isERC20Checking() {
        return currentState.erc20Checking;
    }

    public int getUnusedDataOffset() {
        return globalState.unusedDataOffset;
    }

    public void setUnusedDataOffset(int offset) {
        globalState.unusedDataOffset = offset;
    }

    public boolean isDoVisitedCheck() {
        return globalState.doVisitedCheck;
    }

    public void setDoVisitedCheck(boolean b) {
        globalState.doVisitedCheck = b;
    }

    // callやcreateで変化する状態
    @Builder
    @EqualsAndHashCode(exclude = {"data", "value", "memoryAccessMaxIndex", "edgeVisitedCounts"})
    private static class CallerState {
        // 実行コンテキストのアドレス
        private String contextAddressHex;
        // 実行コードのアドレス
        private String codeAddressHex;
        private String callerAddressHex;
        private int pc;
        private ReturnData returnData;
        private Stack stack;
        private Memory memory;
        private Data data;
        private Value value;
        private int memoryAccessMaxIndex;
        private Map<Edge, Integer> edgeVisitedCounts;
        // accountsはrepositoryのwriteキャッシュとして振る舞う
        private Map<String, Account> accounts;
        private Creating creating;
        private Calling calling;
        private boolean staticcalling;
        private boolean erc20Checking;

        public CallerState copy(Context newContext) {
            CallerState copy = CallerState.builder()
                    .contextAddressHex(contextAddressHex)
                    .codeAddressHex(codeAddressHex)
                    .callerAddressHex(callerAddressHex)
                    .pc(pc)
                    .returnData(returnData.copy(newContext))
                    .stack(stack.copy(newContext))
                    .memory(memory.copy(newContext))
                    .data(data.copy(newContext))
                    .value(value.copy(newContext))
                    .memoryAccessMaxIndex(memoryAccessMaxIndex)
                    .edgeVisitedCounts(Maps.newHashMap(edgeVisitedCounts))
                    .accounts(copyAccounts(accounts, newContext))
                    .creating(creating)
                    .calling(calling == null ? null : calling.copy(newContext))
                    .staticcalling(staticcalling)
                    .build();
            return copy;
        }

    }

    @RequiredArgsConstructor
    @EqualsAndHashCode
    private static class Creating {
        private final String addressHex;
        private final Code code;
    }

    @RequiredArgsConstructor
    private static class Calling {
        private final BitVecExpr outFrom;
        private final BitVecNum outSize;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Calling calling = (Calling) o;
            return Objects.equals(Util.exprToString(outFrom), Util.exprToString(calling.outFrom)) &&
                    Objects.equals(Util.exprToString(outSize), Util.exprToString(calling.outSize));
        }

        @Override
        public int hashCode() {
            return Objects.hash(Util.exprToString(outFrom), Util.exprToString(outSize));
        }

        public Calling copy(Context newContext) {
            Calling copy = new Calling(Z3Util.translate(outFrom, newContext),
                    (BitVecNum) Z3Util.translate(outSize, newContext));
            return copy;
        }
    }


    // callやcreateで変化しない状態
    @Builder
    // TODO solverのAssertionsの違いを考慮する
    @EqualsAndHashCode(exclude = {"context", "solver", "userInputHistory", "repoCodeCache"})
    private static class GlobalState {
        private String originAddressHex;
        private Context context;
        private Solver solver;
        private UserInputHistory userInputHistory;
        private boolean vmStopped;
        private boolean txsStopped;
        private RepositoryRoot repository;
        // リエントランス時のdata/value
        private UserInput reentrancyInput;
        private Map<String, Code> repoCodeCache;
        private int constraintHash;
        private int unusedDataOffset;
        private boolean doVisitedCheck;

        public GlobalState copy(Context newContext) {
            GlobalState copy = GlobalState.builder()
                    .originAddressHex(originAddressHex)
                    .context(newContext)
                    .solver(Z3Util.translate(solver, newContext))
                    .userInputHistory(userInputHistory.copy(newContext))
                    .vmStopped(vmStopped)
                    .txsStopped(txsStopped)
                    .repository(repository)
                    .reentrancyInput(reentrancyInput == null ? null : reentrancyInput.copy(newContext))
                    // 分岐でCREATEすると、同一アドレスに対して異なるコードが作成される。
                    // この時、キャッシュを共有しているとレコードの上書きが発生する。
                    // これを避けるために、共有はせずに、state毎にコピーする。
                    .repoCodeCache(Maps.newHashMap(repoCodeCache))
                    .constraintHash(constraintHash)
                    .unusedDataOffset(unusedDataOffset)
                    .doVisitedCheck(true)
                    .build();
            return copy;
        }
    }

    @Getter
    private GlobalState globalState;
    @Getter
    private CallerState currentState;
    @Getter
    private LinkedList<CallerState> callerStateStack;

    public MachineState(String addressHex) {
        addressHex = Util.normalizeAddress(addressHex);
        final Context context = new Context();
        final Solver solver = Z3Util.mkSolver(context);
        globalState = GlobalState.builder()
                .originAddressHex(Constants.SENDER_ADDRESS_HEX)
                .context(context)
                .solver(solver)
                .userInputHistory(new UserInputHistory())
                .repoCodeCache(Maps.newHashMap())
                .unusedDataOffset(INITIAL_UNUSED_DATA_OFFSET)
                .doVisitedCheck(true)
                .build();
        currentState = CallerState.builder()
                .contextAddressHex(addressHex)
                .codeAddressHex(addressHex)
                .callerAddressHex(Constants.SENDER_ADDRESS_HEX)
                .returnData(new ReturnData(null))
                .stack(new Stack())
                .memory(new Memory(context))
                .data(new Data(globalState.context, 0))
                .value(new Value(globalState.context, 0))
                .edgeVisitedCounts(Maps.newHashMap())
                .accounts(Maps.newHashMap())
                .build();
        callerStateStack = Lists.newLinkedList();
        addValueConstraint(currentState.value.getExpr());
    }

    public void addValueConstraint(BitVecExpr valueExpr) {
        Z3Util.add(globalState.solver, Z3Util.mkBVULT(globalState.context, valueExpr,
                Z3Util.mkBV(globalState.context, Constants.MAX_TX_VALUE.add(BigInteger.ONE).toString(), 256)));
    }

    public void addZeroValueConstraint(BitVecExpr valueExpr) {
        Z3Util.add(globalState.solver, Z3Util.mkEq(globalState.context, valueExpr,
                Z3Util.mkBV(globalState.context, 0, 256)));
    }

    public MachineState copy() {
        final Context newContext = new Context();
        LinkedList<CallerState> newCallerStateStack = Lists.newLinkedList();
        for (CallerState cs : callerStateStack) {
            newCallerStateStack.addLast(cs.copy(newContext));
        }
        CallerState newCurrentState = currentState.copy(newContext);
        GlobalState newGlobalState = globalState.copy(newContext);
        return new MachineState(newGlobalState, newCurrentState, newCallerStateStack);
    }

    public void updateForNextTransaction() {
        globalState.userInputHistory.add(new UserInput(currentState.data, currentState.value));
        globalState.vmStopped = false;
        globalState.txsStopped = false;
        globalState.unusedDataOffset = INITIAL_UNUSED_DATA_OFFSET;
        // repositoryはそのまま

        currentState.pc = 0;
        currentState.returnData = new ReturnData(null);
        currentState.stack = new Stack();
        currentState.memory = new Memory(globalState.context);
        currentState.data = new Data(globalState.context, globalState.userInputHistory.size());
        currentState.value = new Value(globalState.context, globalState.userInputHistory.size());
        currentState.memoryAccessMaxIndex = 0;
        currentState.edgeVisitedCounts = Maps.newHashMap();
        // accountsはそのまま
        currentState.creating = null;
        currentState.calling = null;
        currentState.staticcalling = false;
        addValueConstraint(currentState.value.getExpr());
    }


    public void updateForERC20Checking(String addressHex) {
        addressHex = Util.normalizeAddress(addressHex);
        updateForNextTransaction();
        final String dataHex = Constants.ERC20_BALANCEOF_SIGNATURE + "000000000000000000000000" + addressHex;
        currentState.data = new Data(Z3Util.mkBV(globalState.context, Util.hexToDecimal(dataHex), (4 + 32) * 8));
        currentState.value = new Value(Z3Util.mkBV(globalState.context, 0, 256));
        currentState.erc20Checking = true;
    }

    private static Map<String, Account> copyAccounts(Map<String, Account> accounts_, Context newContext) {
        Map<String, Account> copy = Maps.newHashMap();
        for (Map.Entry<String, Account> entry : accounts_.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy(newContext));
        }
        return copy;
    }

    public BitVecExpr getMemoryChunk(BitVecExpr from, BitVecNum size) {
        if (Z3Util.isBVNum(from)) {
            final BitVecNum fromNum = Z3Util.getBVNum(from);
            updateMemoryAccessMaxIndex(fromNum, size);
        }
        return getChunk(from, size, currentState.memory.getExpr());
    }

    public BitVecExpr getChunk(BitVecExpr from, BitVecNum size, BitVecExpr target) {
        if (from.getSortSize() != 256 || size.getSortSize() != 256) {
            throw new RuntimeException(String.format("異常なsortSize: %d %d", from.getSortSize(), size.getSortSize()));
        }
        final int sizeInt = Util.getInt(size);
        if (sizeInt <= 0) {
            return null;
        }
        if (Constants.MAX_CHUNK_BYTE_SIZE <= sizeInt) {
            log.warn("サイズ超過: {}", sizeInt);
            return null;
        }
        if (target == null) {
            return Z3Util.mkBV(globalState.context, 0, sizeInt * 8);
        }

        int targetBits = target.getSortSize();
        int sizeBits = sizeInt * 8;
        int baseBits = Math.max(256, Math.max(targetBits, sizeBits));
        // sortSizeをbaseBitsにする
        if (baseBits > 256) {
            from = Z3Util.mkZeroExt(globalState.context, baseBits - 256, from);
            size = Z3Util.getBVNum(Z3Util.mkZeroExt(globalState.context, baseBits - 256, size));
        }
        if (baseBits > targetBits) {
            target = Z3Util.mkZeroExt(globalState.context, baseBits - targetBits, target);
        }

        final BitVecNum available = Z3Util.mkBV(globalState.context, targetBits, baseBits);
        final BitVecExpr use =
                Z3Util.mkBVMul(globalState.context, Z3Util.mkBVAdd(globalState.context, from, size),
                        Z3Util.mkBV(globalState.context, 8, baseBits));
        // 隙間のビット数
        BitVecExpr count = Z3Util.mkITE(globalState.context,
                Z3Util.mkBVUGT(globalState.context, available, use),
                Z3Util.mkBVSub(globalState.context, available, use),
                Z3Util.mkBV(globalState.context, 0, baseBits)
        );
        // 溢れのビット数
        BitVecExpr count2 = Z3Util.mkITE(globalState.context,
                Z3Util.mkBVUGT(globalState.context, use, available),
                Z3Util.mkBVSub(globalState.context, use, available),
                Z3Util.mkBV(globalState.context, 0, baseBits)
        );
        BitVecExpr mask = Z3Util.mkBVSub(globalState.context,
                Z3Util.mkBVSHL(globalState.context, Z3Util.mkBV(globalState.context, 1, baseBits),
                        Z3Util.mkBV(globalState.context, sizeBits, baseBits)),
                Z3Util.mkBV(globalState.context, 1, baseBits)
        );
        BitVecExpr result = Z3Util.mkBVAND(globalState.context, Z3Util.mkBVLSHR(globalState.context, target, count),
                Z3Util.mkBVLSHR(globalState.context, mask, count2));
        result = Z3Util.mkBVSHL(globalState.context, result, count2);

        // sortSizeをsizeBitsにする
        if (baseBits > sizeBits) {
            result = Z3Util.mkExtract(globalState.context, sizeBits - 1, 0, result);
        }
        return result;
    }

    public BitVecExpr stackPop() {
        return currentState.stack.pop();
    }

    public void stackPush(BitVecExpr expr) {
        if (expr == null) {
            expr = Z3Util.mkBV(globalState.context, 0, 256);
        }
        currentState.stack.push(expr);
    }

    public int stackSize() {
        return currentState.stack.size();
    }


    public BitVecExpr getDataChunk(BitVecExpr from, BitVecNum size) {
        return getChunk(from, size, currentState.data.getExpr());
    }

    public void putMemoryChunk(BitVecExpr to, BitVecNum size, BitVecExpr data) {
        if (to.getSortSize() != 256 || size.getSortSize() != 256) {
            throw new RuntimeException(String.format("異常なsortSize: %d %d", to.getSortSize(), size.getSortSize()));
        }
        final int sizeInt = Util.getInt(size);
        if (sizeInt <= 0) {
            return;
        }
        if (Constants.MAX_CHUNK_BYTE_SIZE <= sizeInt) {
            log.warn("サイズ超過: {}", sizeInt);
            return;
        }
        if (data == null) {
            log.debug("dataがnull");
            return;
        }
        if (sizeInt * 8 != data.getSortSize()) {
            // return,revertのhReturnではサイズが一致しなくても良い
//            throw new RuntimeException(String.format("不整合なsizeとdata: %d %d", size.getInt() * 8, data.getSortSize()));
        }
        if (Z3Util.isBVNum(to)) {
            final BitVecNum toNum = Z3Util.getBVNum(to);
            updateMemoryAccessMaxIndex(toNum, size);
        }

        BitVecExpr target = currentState.memory.getExpr();
        int targetBits = target.getSortSize();
        int dataBits = data.getSortSize();
        int baseBits = Math.max(targetBits, dataBits);
        // sortSizeをbaseBitsにする
        to = Z3Util.mkZeroExt(globalState.context, baseBits - 256, to);
        size = Z3Util.getBVNum(Z3Util.mkZeroExt(globalState.context, baseBits - 256, size));
        if (baseBits > dataBits) {
            data = Z3Util.mkZeroExt(globalState.context, baseBits - dataBits, data);
        }
        if (baseBits > targetBits) {
            target = Z3Util.mkZeroExt(globalState.context, baseBits - targetBits, target);
        }
        final BitVecNum available = Z3Util.mkBV(globalState.context, targetBits, baseBits);
        final BitVecExpr use =
                Z3Util.mkBVMul(globalState.context, Z3Util.mkBVAdd(globalState.context, to, size),
                        Z3Util.mkBV(globalState.context, 8, baseBits));
        // 隙間のビット数
        BitVecExpr count = Z3Util.mkITE(globalState.context,
                Z3Util.mkBVUGT(globalState.context, available, use),
                Z3Util.mkBVSub(globalState.context, available, use),
                Z3Util.mkBV(globalState.context, 0, baseBits));
        // 溢れのビット数
        BitVecExpr count2 = Z3Util.mkITE(globalState.context,
                Z3Util.mkBVUGT(globalState.context, use, available),
                Z3Util.mkBVSub(globalState.context, use, available),
                Z3Util.mkBV(globalState.context, 0, baseBits));
        BitVecExpr mask = Z3Util.mkBVNot(globalState.context,
                Z3Util.mkBVLSHR(globalState.context,
                        Z3Util.mkBVSHL(globalState.context,
                                Z3Util.mkBVSub(globalState.context,
                                        Z3Util.mkBVSHL(globalState.context,
                                                Z3Util.mkBV(globalState.context, 1, baseBits),
                                                Z3Util.mkBV(globalState.context, dataBits, baseBits)),
                                        Z3Util.mkBV(globalState.context, 1, baseBits)
                                ),
                                count
                        ),
                        count2
                )
        );
        BitVecExpr data_mask =
                Z3Util.mkBVLSHR(globalState.context, Z3Util.mkBVSHL(globalState.context, data, count), count2);
        target = Z3Util.mkBVOR(globalState.context, Z3Util.mkBVAND(globalState.context, target, mask), data_mask);
        // sortSizeをtaretBitsにする
        if (baseBits > targetBits) {
            target = Z3Util.mkExtract(globalState.context, targetBits - 1, 0, target);
        }
        currentState.memory = new Memory(target);
    }

    private void updateMemoryAccessMaxIndex(BitVecNum start, BitVecNum size) {
        final BigInteger indexBi = start.getBigInteger().add(size.getBigInteger());
        try {
            final int index = indexBi.intValueExact();
            currentState.memoryAccessMaxIndex = Math.max(currentState.memoryAccessMaxIndex, index);
        } catch (ArithmeticException e) {
            log.debug("", e);
        }
    }

    public BitVecExpr getCodeChunk(BitVecExpr from, BitVecNum size) {
        final byte[] codeBytes = getCodeBytes();
        final int sizeInt = Util.getInt(size);
        if (sizeInt <= 0) {
            return null;
        } else if (Constants.MAX_CHUNK_BYTE_SIZE <= sizeInt) {
            log.warn("サイズ超過: {}", sizeInt);
            return null;
        } else if (codeBytes.length == 0) {
            return Z3Util.mkBV(globalState.context, 0, sizeInt * 8);
        } else {
            BitVecExpr target = Z3Util.mkBV(globalState.context, Util.bytesToDecimal(codeBytes), codeBytes.length * 8);
            return getChunk(from, size, target);
        }
    }

    public byte[] getCodeBytes() {
        return getCodeBytes(currentState.codeAddressHex, true);
    }

    byte[] getCodeBytes(String addressHex, boolean includeCreating) {
        addressHex = Util.normalizeAddress(addressHex);
        if (includeCreating && currentState.creating != null && currentState.creating.addressHex.equals(addressHex)) {
            return ByteUtil.hexStringToBytes(currentState.creating.code.getHex());
        }
        final Account account = currentState.accounts.get(addressHex);
        if (account == null) {
            if (!globalState.repoCodeCache.containsKey(addressHex)) {
                final byte[] address = ByteUtil.hexStringToBytes(addressHex);
                Code code = new Code(globalState.repository.getCode(address));
                globalState.repoCodeCache.put(addressHex, code);
            }
            return ByteUtil.hexStringToBytes(globalState.repoCodeCache.get(addressHex).getHex());
        } else {
            return account.getCodeBytes();
        }
    }

    public BitVecExpr getReturnDataChunk(BitVecExpr from, BitVecNum size) {
        return getChunk(from, size, currentState.returnData.getExpr());
    }

    public void putStorageValue(BitVecExpr key, BitVecExpr value) {
        createAccountIfNotExist(currentState.contextAddressHex);
        currentState.accounts.get(currentState.contextAddressHex).putStroageValue(key, value);
    }


    public BitVecExpr getStorageValue(BitVecExpr key) {
        final String addressHex = currentState.contextAddressHex;
        BitVecExpr value = null;
        // accountsを探して、無ければrepositoryを探す
        final Account account = currentState.accounts.get(addressHex);
        if (account != null) {
            value = account.getStorageValue(key);
        }
        if (value == null && Z3Util.isBVNum(key)) {
            byte[] address = ByteUtil.hexStringToBytes(addressHex);
            final byte[] keyBytes = Util.bitvecnumToBytes(Z3Util.getBVNum(key));
            DataWord valueWord = globalState.repository.getStorageValue(address, DataWord.of(keyBytes));
            if (valueWord == null) {
                valueWord = DataWord.ZERO;
            }
            final byte[] valueBytes = valueWord.getData();
            value = Z3Util.mkBV(globalState.context, Util.bytesToDecimal(valueBytes), 256);
        }
        return value;
    }

    private void createAccountIfNotExist(String addressHex) {
        addressHex = Util.normalizeAddress(addressHex);
        if (currentState.accounts.containsKey(addressHex)) {
            return;
        }

        final byte[] address = ByteUtil.hexStringToBytes(addressHex);
        AccountState accountState = globalState.repository.getAccountState(address);
        Account account;
        if (accountState == null) {
            account = new Account(BigInteger.ZERO, Constants.INITIAL_NONCE, new Storage(), new Code(EMPTY_BYTES));
        } else {
            account = new Account(accountState.getBalance(), accountState.getNonce(), new Storage(),
                    new Code(globalState.repository.getCode(address)));
        }
        currentState.accounts.put(addressHex, account);
    }

    public Instruction getInstruction(int pc_) {
        final String addressHex = currentState.codeAddressHex;
        if (currentState.creating != null) {
            return currentState.creating.code.getInstruction(pc_);
        }
        final Account account = currentState.accounts.get(addressHex);
        if (account == null) {
            if (!globalState.repoCodeCache.containsKey(addressHex)) {
                final byte[] address = ByteUtil.hexStringToBytes(addressHex);
                Code code = new Code(globalState.repository.getCode(address));
                globalState.repoCodeCache.put(addressHex, code);
            }
            return globalState.repoCodeCache.get(addressHex).getInstruction(pc_);
        } else {
            return account.getInstruction(pc_);
        }
    }

    public void stackDup(int i) {
        currentState.stack.push(currentState.stack.get(i));
    }

    public void stackSwap(int i) {
        BitVecExpr a = currentState.stack.get(0);
        BitVecExpr b = currentState.stack.get(i);
        currentState.stack.set(0, b);
        currentState.stack.set(i, a);
    }

    public void stackSet(int i, BitVecExpr expr) {
        currentState.stack.set(i, expr);
    }

    public BitVecExpr getExtCodeChunk(BitVecExpr from, BitVecNum size, BitVecNum address) {
        final byte[] extCodeBytes = getExtCodeBytes(address);
        final int sizeInt = Util.getInt(size);
        if (sizeInt <= 0) {
            return null;
        } else if (Constants.MAX_CHUNK_BYTE_SIZE <= sizeInt) {
            log.warn("サイズ超過: {}", sizeInt);
            return null;
        } else if (extCodeBytes.length == 0) {
            return Z3Util.mkBV(globalState.context, 0, sizeInt * 8);
        } else {
            BitVecExpr target =
                    Z3Util.mkBV(globalState.context, Util.bytesToDecimal(extCodeBytes), extCodeBytes.length * 8);
            return getChunk(from, size, target);
        }
    }

    public byte[] getExtCodeBytes(BitVecNum address) {
        String addressHex = Util.normalizeAddress(Util.bitvecnumToHex(address));
        return getCodeBytes(addressHex, false);
    }

    public BitVecExpr stackGet(int i) {
        return currentState.stack.get(i);
    }

    public int getCallStackSize() {
        return callerStateStack.size();
    }

    public void putAccount(String addressHex, Account account) {
        addressHex = Util.normalizeAddress(addressHex);
        currentState.accounts.put(addressHex, account);
    }

    public boolean checkAndIncrementEdgeVisitedCount(int from, int to, int maxEdgeVisitedCount) {
        Edge edge = new Edge(from, to);
        if (currentState.edgeVisitedCounts.containsKey(edge)) {
            int visitedCount = currentState.edgeVisitedCounts.get(edge);
            if (visitedCount >= maxEdgeVisitedCount) {
                log.debug("ループ打ち切り: {} -> {}", from, to);
                return true;
            } else {
                currentState.edgeVisitedCounts.put(edge, visitedCount + 1);
            }
        } else {
            currentState.edgeVisitedCounts.put(edge, 1);
        }
        log.debug(String.format("%d回目: %05x -> %05x", currentState.edgeVisitedCounts.get(edge), from, to));
        return false;
    }

    public Position newPosition() {
        String targetAddressHex = getTargetAddressHex();
        return new Position(targetAddressHex, currentState.contextAddressHex,
                currentState.codeAddressHex, currentState.pc);
    }
}
