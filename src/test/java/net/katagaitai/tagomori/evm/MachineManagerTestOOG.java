package net.katagaitai.tagomori.evm;

import net.katagaitai.tagomori.NetworkType;
import net.katagaitai.tagomori.TestUtil;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.poc.PoCCategory;
import net.katagaitai.tagomori.util.Constants;
import net.katagaitai.tagomori.util.Util;
import org.ethereum.facade.EthereumImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class MachineManagerTestOOG {
    private static EthereumImpl ethereum;
    // 0.02ETHを基準にする
    private static final BigInteger newMaxTxValue = BigInteger.valueOf(10).pow(18 - 2);
    private static final BigInteger savedMaxTxValue = Constants.MAX_TX_VALUE;
    private static final String savedProxy = Constants.ROPSTEN_PROXY_ADDRESS_HEX;

    public static class OOG {
        @BeforeClass
        public static void setup() throws IOException, InterruptedException {
            ethereum = Util.getEthereumImpl(NetworkType.Ropsten, false, TestUtil.ROPSTEN_DATABASE);
            Constants.EXCLUDE_CONTRACTS.clear();
            Constants.MAX_TX_VALUE = newMaxTxValue;
            // ガス残量を見ないProxy
            Constants.ROPSTEN_PROXY_ADDRESS_HEX = "cddd52cb15c21d79e812430113cc616dfb8d77f3";
        }

        @AfterClass
        public static void tearDown() {
            Util.closeEthereum(ethereum);
            Constants.MAX_TX_VALUE = savedMaxTxValue;
            Constants.ROPSTEN_PROXY_ADDRESS_HEX = savedProxy;
        }

        @Test
        public void test_EtherStoreHighCos() {
            // ガス残量を見ないのでOOGになる
            String addressHex = "14b093501a08ac41d935ffa92f5e5a5281b0b404";
            MachineManager manager = new MachineManager(ethereum);
            final List<PoC> pocs = manager.execute(addressHex);
            assertEquals(0, manager.getExceptionCount());
            assertEquals(0, pocs.size());
        }
    }

    public static class NotOOG {
        @BeforeClass
        public static void setup() throws IOException, InterruptedException {
            ethereum = Util.getEthereumImpl(NetworkType.Ropsten, false, TestUtil.ROPSTEN_DATABASE);
            Constants.EXCLUDE_CONTRACTS.clear();
            Constants.MAX_TX_VALUE = newMaxTxValue;
        }

        @AfterClass
        public static void tearDown() {
            Util.closeEthereum(ethereum);
            Constants.MAX_TX_VALUE = savedMaxTxValue;
        }

        @Test
        public void test_EtherStoreHighCost() {
            // ガス残量を見るのでOOGにならない
            String addressHex = "14b093501a08ac41d935ffa92f5e5a5281b0b404";
            MachineManager manager = new MachineManager(ethereum);
            final List<PoC> pocs = manager.execute(addressHex);
            assertEquals(0, manager.getExceptionCount());
            assertEquals(1, pocs.size());
            assertEquals(PoCCategory.REENTRANCY, pocs.get(0).getCategory());
        }
    }
}