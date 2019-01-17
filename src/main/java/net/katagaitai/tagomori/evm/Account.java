package net.katagaitai.tagomori.evm;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.Objects;

@Data
@AllArgsConstructor
public class Account {
    private BigInteger balance;
    private BigInteger nonce;
    private Storage storage;
    private Code code;

    public Account copy(Context newContext) {
        return new Account(balance, nonce, storage.copy(newContext), code);
    }

    public Instruction getInstruction(int pc) {
        return code.getInstruction(pc);
    }

    public void putStroageValue(BitVecExpr key, BitVecExpr value) {
        storage.put(key, value);
    }

    public BitVecExpr getStorageValue(BitVecExpr key) {
        return storage.get(key);
    }

    public byte[] getCodeBytes() {
        return ByteUtil.hexStringToBytes(code.getHex());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(balance, account.balance) &&
                Objects.equals(nonce, account.nonce) &&
                Objects.equals(storage, account.storage) &&
                Objects.equals(code, account.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(balance, nonce, storage, code);
    }
}
