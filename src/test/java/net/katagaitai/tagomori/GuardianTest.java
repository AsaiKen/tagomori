package net.katagaitai.tagomori;

import net.katagaitai.tagomori.evm.MachineManager;
import org.junit.Test;

import java.io.IOException;

public class GuardianTest {
    @Test
    public void testCollect() throws IOException, InterruptedException {
        Guardian guardian = new Guardian(NetworkType.Ropsten, TestUtil.ROPSTEN_DATABASE);
        ContractCollector collector = guardian.getCollector();
        try {
            collector.collect();
        } finally {
            guardian.close();
        }
    }

    @Test
    public void testExecute() throws IOException, InterruptedException {
        Guardian guardian = new Guardian(NetworkType.Ropsten, TestUtil.ROPSTEN_DATABASE);
        try {
            for (String addressHex : guardian.getAddressHexs()) {
                MachineManager manager = guardian.execute(addressHex);
                if (manager.getExceptionCount() > 0) {
                    throw new RuntimeException(addressHex);
                }
            }
        } finally {
            guardian.close();
        }
    }
}