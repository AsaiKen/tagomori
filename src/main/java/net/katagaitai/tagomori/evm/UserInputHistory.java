package net.katagaitai.tagomori.evm;

import com.google.common.collect.Lists;
import com.microsoft.z3.Context;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode
public class UserInputHistory {
    private List<UserInput> list = Lists.newArrayList();

    public void add(UserInput ui) {
        list.add(ui);
    }

    public UserInput get(int i) {
        return list.get(i);
    }

    public UserInputHistory copy(Context newContext) {
        UserInputHistory copy = new UserInputHistory();
        for (UserInput ui : list) {
            copy.list.add(ui.copy(newContext));
        }
        return copy;
    }

    public int size() {
        return list.size();
    }
}
