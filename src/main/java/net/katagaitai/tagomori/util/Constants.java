package net.katagaitai.tagomori.util;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.spongycastle.util.Arrays;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j(topic = "tagomori")
public class Constants {
    public static final int MEMORY_BITS = 8 * 256;
    public static final int DATA_BITS = 8 * 256;
    public static final int MAX_CHUNK_BYTE_SIZE = 1024;
    public static final int MAX_EDGE_VISITED_COUNT = 2;
    public static final int MAX_TRANSACTION_COUNT = 2;
    public static final int MAX_CALLSTACK_SIZE = 2;
    public static final long MANAGER_TIMEOUT_MILLS = 180_000;
    public static final int SOLVER_TIMEOUT_MILLS = 10_000;
    public static final boolean MAPPING_KEY_TO_ZERO = false;
    public static final boolean AND_ARG_TO_ADDRESS = false;
    public static final boolean LONG_SIZE_DYNAMIC_ARRAY = false;
    // msg.valueの上限
    public static BigInteger MAX_TX_VALUE = BigInteger.valueOf(10).pow(18);

    public static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    public static final boolean SKIP_SIMULATION = false;
    public static final BigInteger INITIAL_NONCE = BigInteger.ONE;
    public static final int MAX_SAT_SIZE = 64;
    public static final int MAX_OVERFLOW_STORAGE_KEY = 10;

    public static final String ERC20_TOTALSUPPLY_SIGNATURE = "18160ddd";
    public static final String ERC20_BALANCEOF_SIGNATURE = "70a08231";
    public static final String ERC20_TRANSFER_SIGNATURE = "a9059cbb";
    public static final String ERC20_TRANSFERFROM_SIGNATURE = "23b872dd";
    public static final String ERC20_APPROVE_SIGNATURE = "095ea7b3";
    public static final String ERC20_ALLOWANCE_SIGNATURE = "dd62ed3e";
    public static final BigInteger ERC20_MIN_BALANCE = BigInteger.valueOf(10).pow(18);

    public static ECKey SENDER_EC_KEY = ECKey.fromPrivate(
            ByteUtil.hexStringToBytes("873acd4075171e28ae9c0f1f0c26c262d02426116d63d315191a2c38ff6c0ba8"));
    public static byte[] SENDER_ADDRESS = SENDER_EC_KEY.getAddress();
    public static String SENDER_ADDRESS_HEX = ByteUtil.toHexString(SENDER_ADDRESS);
    public static String ROPSTEN_PROXY_ADDRESS_HEX = "3d07bc9efa092bc68781125d7f8ee5abf2dfcb7d";
    public static String ROPSTEN_TRANSFER_ADDRESS_HEX = "913575c90d1fbf4d84ab5f78807a80cbfe215bd4";
    // オープンソース版は、mainnetを検査できないように、ProxyとTransferのアドレスをnullにしておく。
    public static String MAINNET_PROXY_ADDRESS_HEX = null;
    public static String MAINNET_TRANSFER_ADDRESS_HEX = null;
    public static String PROXY_CALL_SIGNATURE = "1b8b921d";
    public static String PROXY_REGISTER_SIGNATURE = "24b8fbf6";
    public static String PROXY_WITHDRAW_SIGNATURE = "3ccfd60b";
    public static String RESULT_DIRECTORY = "result";
    // テストで使用するコントラクトを除外する
    public static Set<String> EXCLUDE_CONTRACTS = Sets.newHashSet(
            "3608ae866C4d49999c14dA8150117016818b0d81",
            "0f99a1DC276E4045a8fF8Da4910aCDdb7c6757d1",
            "eafbe543a263a782e039d4f755db7e6d867646de",
            "a9Db632d51BD42a43Fa390dE8054DbcE92E2Cc84",
            "9dfD6402251398EADa38205ACBb5828f0193E379",
            "de475f904b1ad996d67f056ca2dd32868bcdf7aa",
            "455db138f41c29d29c6d8627f0d0036d3bb84d00",
            "b99c68348f759352436bd80c6249cd14fb0fef36",
            "C320E373b369b58E716c1925780D36F703030f3D",
            "47Fb2aFA895F351Db519827677e59eE70e5454Ef",
            "0a671be1fbfcf20cd90e1e1e9225732fd7f65d8b",
            "a1f9990383e38b0d2882f04f2d1cafc31ad25e86",
            "9B083fb77E29d12437C885e092DA1924659163d4",
            "c88b3259d4aa480e4e40e1ad5c9fad54012cb7af",
            "10f6ec408ff7ba3b6d3c3089cd1cadf9c4fe883c",
            "F3E9D7a824eA57129c6A9961fBeC30bc55C68bAd",
            "7e3590E0ebC2Cbe936C71da4CE3564F067A47a01",
            "201706776c1c61a573966fb74e874dd4d3229d11",
            "eacba85a73ce41e72b56cb4e1247daf633e27dfe",
            "a9f683976c293e992181bd53ddbaf44079b3cc42",
            "c721edae3f0b8f2c067a3e3321956ef8e910ddcf",
            "6CFB0E75DF15d04b7C977B7bf29bEA3d0076f66a",
            "97982bfE0a23C5a08AdDDD60088ea3463A265166",
            "47f21D447863C96b78242D2356F49cE7a564694a",
            "08a8082fefa74adea3cc7e661fd199f908a515b8",
            "3d3766018A8E3eE85c81770EDE4c7CD33C6790d6",
            "10ea921c2045ea1181fe370f4c0b0ee692c60db3",
            "D1fD80bf95d7cC3b719784aEA0c7045b6E7c79a3",
            "8678d4c1c6260230ea720d3dd8a1151ac8de9071",
            "b032dea6971a7a4bfeef248bca4f0d9b0f1df620",
            "412e0c5b1a5569db011366482de2b8f30f85e276",
            "412e0c5b1a5569db011366482de2b8f30f85e276",
            "f151afcfaa6f902610f73d7ac020ba2207e4708e",
            "412e0c5b1a5569db011366482de2b8f30f85e276",
            "8a64d732dd3fa2fb775adccb6e90cbf4a638b38d",
            "03f03db61fecf0a1cc7876631c4bbe5664799cd2",
            "e7afbd9a00fe2edc56db44701e7578ade4c8b60d",
            "c77603ac6842e591c21c51b8d0283962ba8c18c3",
            "000db1bcb4311ae422cd1117bb249cba0fdb156c",
            "0014191568b4202ffd6cfba59cdb6049911e4d2d",
            "0085e34b52f44f63cdc63c2566f8273c9f91cdca",
            "05c790d5848b7231f7e81fdd5d55589f9cb1717e",
            "061034bff8a6e878812c456a937920803b6de3d7",
            "08de806b30692bf5b422addae93ea4c77ff21e31",
            "0bcb300c55c12d6f183b2a106fee3a8b0bc84403",
            "ea7b971ed5643693e8f84f5768ddf4db511b6625",
            "c248c2bf5459ea9987b24560ec58dc0fbf097320",
            "03e9fb9fb3d2dcdcba18662a9c7c997ddb10d5f3",
            "e2418c3b0bb1f65137228bc72c257607cfde3188",
            "876351a7fc02d14ea65cec9fd4becb0129c2203b",
            "6bb5624415fc51d667eb848b7f47023c7906434b",
            "3f9557c823c17bff3c12d86a8aa1766d4358e722",
            "938e5c311f1245a9c1edde13e4914b9ad5e0d3fe",
            "b5891663602f3474d77fd9101ed29165aa7efde7",
            "d2fc66d806efda673a94e10e05110da91d2652fb",
            "d462c853b39da53242a0fb55e3ba68fbfbbfaa24",
            "093b8520c339375d31b5c74730147be6d3086f1c",
            "042777def66f19e50a45a132deab7718f7b8076d",
            "14b093501a08ac41d935ffa92f5e5a5281b0b404"
    ).stream().map(String::toLowerCase).collect(Collectors.toSet());
    public static String CONTRACT_DB = "contract";
    public static String BLOCKNUMBER_DB = "blocknumber";
}
