package net.katagaitai.tagomori.evm;

import com.google.common.collect.Maps;
import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.Context;
import net.katagaitai.tagomori.util.Z3Util;

import java.util.Map;
import java.util.Objects;

public class Storage {
    private final Map<String, BitVecExpr> map = Maps.newHashMap();

    private String getKey(BitVecExpr key) {
        return key.simplify().toString();
    }

    public Storage copy(Context newContext) {
        Storage copy = new Storage();
        for (Map.Entry<String, BitVecExpr> entry : map.entrySet()) {
            copy.map.put(entry.getKey(), Z3Util.translate(entry.getValue(), newContext));
        }
        return copy;
    }

    public void put(BitVecExpr key, BitVecExpr value) {
        map.put(getKey(key), value);
    }

    public BitVecExpr get(BitVecExpr key) {
        return map.get(getKey(key));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Storage storage = (Storage) o;
        return Objects.equals(map.toString(), storage.map.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(map.toString());
    }
}
