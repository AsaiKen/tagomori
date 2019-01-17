package net.katagaitai.tagomori.evm;

import com.microsoft.z3.Context;

@lombok.Value
public class UserInput {
    private Data data;
    private Value value;

    public UserInput copy(Context newContext) {
        UserInput copy = new UserInput(data.copy(newContext), value.copy(newContext));
        return copy;
    }
}
