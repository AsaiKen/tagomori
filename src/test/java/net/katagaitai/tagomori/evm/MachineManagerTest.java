package net.katagaitai.tagomori.evm;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import java.util.List;

import static junit.framework.TestCase.*;

public class MachineManagerTest {
    private static EthereumImpl ethereum;

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        ethereum = Util.getEthereumImpl(NetworkType.Ropsten, false, TestUtil.ROPSTEN_DATABASE);
        Constants.EXCLUDE_CONTRACTS.clear();
    }

    @AfterClass
    public static void tearDown() {
        Util.closeEthereum(ethereum);
    }

    @Test
    public void test_THCCTF_ropsten() {
        String addressHex = "3608ae866C4d49999c14dA8150117016818b0d81";
        MachineManager manager = new MachineManager(ethereum);
        manager.setMaxEdgeVisitedCount(30);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_2transactions_ropsten_fail() {
        String addressHex = "0f99a1DC276E4045a8fF8Da4910aCDdb7c6757d1";
        MachineManager manager = new MachineManager(ethereum);
        manager.setMaxTransactionCount(1);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_2transactions_ropsten() {
        String addressHex = "0f99a1DC276E4045a8fF8Da4910aCDdb7c6757d1";
        MachineManager manager = new MachineManager(ethereum);
        manager.setMaxTransactionCount(2);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(2 + 1, Lists.newArrayList(pocs).get(0).getPayloads().size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_3transactions_ropsten_fail() {
        String addressHex = "eafbe543a263a782e039d4f755db7e6d867646de";
        MachineManager manager = new MachineManager(ethereum);
        manager.setMaxTransactionCount(2);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_3transactions_ropsten() {
        String addressHex = "eafbe543a263a782e039d4f755db7e6d867646de";
        MachineManager manager = new MachineManager(ethereum);
        manager.setMaxTransactionCount(3);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(3 + 1, Lists.newArrayList(pocs).get(0).getPayloads().size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

//    // creationCodeにDATAがconcatされているケース
//    @Test
//    public void test_CREATE_ropsten_skipSimulation()  {
//        String addressHex = "a9Db632d51BD42a43Fa390dE8054DbcE92E2Cc84";
//        manager.setSkipSimulation(true);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(1, pocs.size());
//    }

    @Test
    public void test_CREATE_CALL_ropsten() {
        String addressHex = "9dfD6402251398EADa38205ACBb5828f0193E379";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_CALLCODE_ropsten_vulnerable() {
        String addressHex = "de475f904b1ad996d67f056ca2dd32868bcdf7aa";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_CALLCODE_ropsten_notVulnerable() {
        String addressHex = "455db138f41c29d29c6d8627f0d0036d3bb84d00";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_DELEGATECALL_ropsten() {
        String addressHex = "b99c68348f759352436bd80c6249cd14fb0fef36";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_reentrancy_ropsten() {
        String addressHex = "C320E373b369b58E716c1925780D36F703030f3D";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.REENTRANCY, pocs.get(0).getCategory());
    }

    @Test
    public void test_arbitraryDelegatecall_ropsten() {
        String addressHex = "47Fb2aFA895F351Db519827677e59eE70e5454Ef";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.DELEGATECALL, pocs.get(0).getCategory());
    }

    @Test
    public void test_arbitraryCallcode_ropsten() {
        String addressHex = "0a671be1fbfcf20cd90e1e1e9225732fd7f65d8b";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.CALLCODE, pocs.get(0).getCategory());
    }

    @Test
    public void test_safeCode() {
        String addressHex = "a1f9990383e38b0d2882f04f2d1cafc31ad25e86";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_ethernaut_AlienCodex() {
        String addressHex = "093b8520c339375d31b5c74730147be6d3086f1c";
        MachineManager manager = new MachineManager(ethereum);
        manager.setMaxTransactionCount(4);
        manager.setLongSizeDynamicArray(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_ethernaut_Delegation() {
        String addressHex = "d462c853b39da53242a0fb55e3ba68fbfbbfaa24";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_ethernaut_Fallout() {
        String addressHex = "F3E9D7a824eA57129c6A9961fBeC30bc55C68bAd";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.CALL, pocs.get(0).getCategory());
    }

    @Test
    public void test_ethernaut_GatekeeperOne() {
        String addressHex = "7e3590E0ebC2Cbe936C71da4CE3564F067A47a01";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_ethernaut_Locked() {
        String addressHex = "201706776c1c61a573966fb74e874dd4d3229d11";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_ethernaut_Preservation() {
        String addressHex = "eacba85a73ce41e72b56cb4e1247daf633e27dfe";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.DELEGATECALL, pocs.get(0).getCategory());
    }

    @Test
    public void test_ethernaut_Telephone() {
        String addressHex = "a9f683976c293e992181bd53ddbaf44079b3cc42";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_ethernaut_Token() {
        String addressHex = "042777def66f19e50a45a132deab7718f7b8076d";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_recursive() {
        String addressHex = "6CFB0E75DF15d04b7C977B7bf29bEA3d0076f66a";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_MAIAN_prodigal() {
        String addressHex = "97982bfE0a23C5a08AdDDD60088ea3463A265166";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.CALL, pocs.get(0).getCategory());
    }

    @Test
    public void test_MAIAN_suicidal() {
        String addressHex = "47f21D447863C96b78242D2356F49cE7a564694a";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.SUICIDE, pocs.get(0).getCategory());
    }

    @Test
    public void test_MAIAN_ParityWalletLibrary() {
        // https://github.com/paritytech/parity-ethereum/blob/4d08e7b0aec46443bf26547b17d10cb302672835/js/src/contracts/snippets/enhanced-wallet.sol
        String addressHex = "08a8082fefa74adea3cc7e661fd199f908a515b8";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertFalse(manager.isTimeout());
        // initMultiownedした後、killまたはexecute
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.CALL, pocs.get(0).getCategory());
    }

    @Test
    public void test_ストレージに書き込まない場合2週目以降を省略できる() {
        String addressHex = "3d3766018A8E3eE85c81770EDE4c7CD33C6790d6";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_mythril_calls() {
        String addressHex = "10ea921c2045ea1181fe370f4c0b0ee692c60db3";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_mythril_etherstore() {
        String addressHex = "D1fD80bf95d7cC3b719784aEA0c7045b6E7c79a3";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        // reentrancy
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.REENTRANCY, pocs.get(0).getCategory());
    }

    @Test
    public void test_mythril_exceptions() {
        String addressHex = "8678d4c1c6260230ea720d3dd8a1151ac8de9071";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_mythril_hashforether() {
        String addressHex = "b032dea6971a7a4bfeef248bca4f0d9b0f1df620";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.CALL, pocs.get(0).getCategory());
    }

    @Test
    public void test_mythril_origin() {
        String addressHex = "412e0c5b1a5569db011366482de2b8f30f85e276";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_mythril_returnvalue() {
        String addressHex = "412e0c5b1a5569db011366482de2b8f30f85e276";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_mythril_rubixi() {
        // https://etherscan.io/address/0xe82719202e5965Cf5D9B6673B7503a3b92DE20be#code
        String addressHex = "f151afcfaa6f902610f73d7ac020ba2207e4708e";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        // creatorを上書きした後、send
        assertTrue(pocs.size() > 0);
        assertEquals(PoCCategory.CALL, pocs.get(0).getCategory());
    }

    @Test
    public void test_mythril_suicide() {
        String addressHex = "412e0c5b1a5569db011366482de2b8f30f85e276";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_mythril_timelock() {
        String addressHex = "8a64d732dd3fa2fb775adccb6e90cbf4a638b38d";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_mythril_weak_random() {
        String addressHex = "03f03db61fecf0a1cc7876631c4bbe5664799cd2";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        // coinbaseをコントロールできることが前提のケース。このツールの対象外。
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_oyente_puzzle() {
        String addressHex = "e7afbd9a00fe2edc56db44701e7578ade4c8b60d";
        MachineManager manager = new MachineManager(ethereum);
        manager.setSkipSimulation(true);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(0, pocs.size());
    }

    @Test
    public void test_reentrancy() {
        String addressHex = "c77603ac6842e591c21c51b8d0283962ba8c18c3";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.REENTRANCY, pocs.get(0).getCategory());
    }

//    @Test
//    public void test_000db1bcb4311ae422cd1117bb249cba0fdb156c() {
//        String addressHex = "000db1bcb4311ae422cd1117bb249cba0fdb156c";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertFalse(manager.isTimeout());
//        assertEquals(0, pocs.size());
//    }
//
//    @Test
//    public void test_0014191568b4202ffd6cfba59cdb6049911e4d2d() {
//        String addressHex = "0014191568b4202ffd6cfba59cdb6049911e4d2d";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertFalse(manager.isTimeout());
//        assertEquals(0, pocs.size());
//    }
//
//    @Test
//    public void test_0085e34b52f44f63cdc63c2566f8273c9f91cdca() {
//        String addressHex = "0085e34b52f44f63cdc63c2566f8273c9f91cdca";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertFalse(manager.isTimeout());
//        assertEquals(0, pocs.size());
//    }
//
//    @Test
//    public void test_05c790d5848b7231f7e81fdd5d55589f9cb1717e() {
//        String addressHex = "05c790d5848b7231f7e81fdd5d55589f9cb1717e";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertFalse(manager.isTimeout());
//        assertEquals(0, pocs.size());
//    }
//
//    @Test
//    public void test_061034bff8a6e878812c456a937920803b6de3d7() {
//        String addressHex = "061034bff8a6e878812c456a937920803b6de3d7";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertFalse(manager.isTimeout());
//        assertEquals(0, pocs.size());
//    }
//
//    @Test
//    public void test_08de806b30692bf5b422addae93ea4c77ff21e31() {
//        String addressHex = "08de806b30692bf5b422addae93ea4c77ff21e31";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertFalse(manager.isTimeout());
//        assertEquals(0, pocs.size());
//    }
//
//    @Test
//    public void test_0bcb300c55c12d6f183b2a106fee3a8b0bc84403() {
//        String addressHex = "0bcb300c55c12d6f183b2a106fee3a8b0bc84403";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertFalse(manager.isTimeout());
//        assertEquals(0, pocs.size());
//    }

    @Test
    public void test_CALL先で任意のCALL() {
        // CALL先の"ea7b971ed5643693e8f84f5768ddf4db511b6625"に任意のCALLがある
        String addressHex = "c248c2bf5459ea9987b24560ec58dc0fbf097320";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertFalse(manager.isTimeout());
        assertEquals(1, pocs.size());
    }

    @Test
    public void test_repeat() {
        String addressHex = "03e9fb9fb3d2dcdcba18662a9c7c997ddb10d5f3";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertFalse(manager.isTimeout());
        assertEquals(1, pocs.size());
        assertTrue(pocs.get(0).getPayloads().size() > 2);
    }

    @Test
    public void test_ERC20_TestToken() {
        String addressHex = "e2418c3b0bb1f65137228bc72c257607cfde3188";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.ERC20, pocs.get(0).getCategory());
    }


    @Test
    public void test_ERC20_TestToken2() {
        String addressHex = "876351a7fc02d14ea65cec9fd4becb0129c2203b";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.ERC20, pocs.get(0).getCategory());
    }

    // 以下はメモリ512byte,データ1024byteなら通る
//    @Test
//    public void test_ERC20_TestToken3() {
//        String addressHex = "6bb5624415fc51d667eb848b7f47023c7906434b";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertEquals(1, pocs.size());
//        assertEquals(PoCCategory.ERC20, pocs.get(0).getCategory());
//    }
//
//    @Test
//    public void test_ERC20_TestToken4() {
//        String addressHex = "3f9557c823c17bff3c12d86a8aa1766d4358e722";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertEquals(1, pocs.size());
//        assertEquals(PoCCategory.ERC20, pocs.get(0).getCategory());
//    }
//
//    @Test
//    public void test_ERC20_TestToken5() {
//        String addressHex = "938e5c311f1245a9c1edde13e4914b9ad5e0d3fe";
//        MachineManager manager = new MachineManager(ethereum);
//        final List<PoC> pocs = manager.execute(addressHex);
//        assertEquals(0, manager.getExceptionCount());
//        assertEquals(1, pocs.size());
//        assertEquals(PoCCategory.ERC20, pocs.get(0).getCategory());
//    }

    @Test
    public void test_ERC20_BECToken() {
        String addressHex = "b5891663602f3474d77fd9101ed29165aa7efde7";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.ERC20, pocs.get(0).getCategory());
    }

    @Test
    public void test_EtherStoreHighCost() {
        String addressHex = "14b093501a08ac41d935ffa92f5e5a5281b0b404";
        MachineManager manager = new MachineManager(ethereum);
        final List<PoC> pocs = manager.execute(addressHex);
        assertEquals(0, manager.getExceptionCount());
        assertEquals(1, pocs.size());
        assertEquals(PoCCategory.REENTRANCY, pocs.get(0).getCategory());
    }

}
