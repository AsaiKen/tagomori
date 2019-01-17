package net.katagaitai.tagomori;

import net.katagaitai.tagomori.evm.MachineManagerTest;
import net.katagaitai.tagomori.evm.MachineStateTest;
import net.katagaitai.tagomori.evm.MachineTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        MachineManagerTest.class,
//        MachineManagerTestOOG.OOG.class,
//        MachineManagerTestOOG.NotOOG.class,
        MachineStateTest.class,
        MachineTest.class,
        StartTest.class,
})
public class AllTests {
}
