package net.katagaitai.tagomori.util;

import com.google.common.collect.Lists;
import com.microsoft.z3.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.katagaitai.tagomori.EthereumImplFactory;
import net.katagaitai.tagomori.NetworkType;
import net.katagaitai.tagomori.evm.*;
import net.katagaitai.tagomori.poc.Payload;
import net.katagaitai.tagomori.poc.PoC;
import net.katagaitai.tagomori.poc.PoCCategory;
import net.katagaitai.tagomori.poc.Position;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.ByteUtil;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

@Slf4j(topic = "tagomori")
public class Util {
    public static String hexToDecimal(String hex) {
        final byte[] bytes = ByteUtil.hexStringToBytes(hex);
        return bytesToDecimal(bytes);
    }

    public static String bytesToDecimal(byte[] bytes) {
        return new BigInteger(1, bytes).toString();
    }

    public static String bitvecnumToHex(BitVecNum num) {
        byte[] bytes = bitvecnumToBytes(num);
        return ByteUtil.toHexString(bytes);
    }

    public static byte[] bitvecnumToBytes(BitVecNum num) {
        byte[] bytes = ByteUtil.bigIntegerToBytes(num.getBigInteger());
        final int byteSize = num.getSortSize() / 8;
        if (bytes.length < byteSize) {
            byte[] tmp = new byte[byteSize];
            System.arraycopy(bytes, 0, tmp, byteSize - bytes.length, bytes.length);
            bytes = tmp;
        } else if (bytes.length > byteSize) {
            bytes = Arrays.copyOfRange(bytes, bytes.length - byteSize, bytes.length);
        }
        return bytes;
    }

    public static void closeEthereum(EthereumImpl ethereum) {
        ethereum.close();
    }

    public static BigInteger getBigInteger(Model model, BitVecExpr expr) {
        BitVecExpr evaledExpr = (BitVecExpr) Z3Util.eval(model, expr, false);
        BigInteger bi;
        if (evaledExpr instanceof BitVecNum) {
            bi = ((BitVecNum) evaledExpr).getBigInteger();
        } else {
            // 制約に使用されていない場合。任意の値でよい。
            bi = BigInteger.ZERO;
        }
        return bi;
    }

    public static String getHex(Model model, BitVecExpr expr) {
        BitVecExpr evaledExpr = (BitVecExpr) Z3Util.eval(model, expr, false);
        String hex;
        if (evaledExpr instanceof BitVecNum) {
            hex = bitvecnumToHex(((BitVecNum) evaledExpr));
        } else {
            // 制約に使用されていない場合。任意の値でよい。
            hex = "";
        }
        return hex;
    }

    public static String normalizeAddress(String addressHex) {
        if (addressHex.length() < 40) {
            final String padding = new String(new char[40 - addressHex.length()]).replace("\0", "0");
            addressHex = padding + addressHex;
        } else if (addressHex.length() > 40) {
            addressHex = addressHex.substring(addressHex.length() - 40);
        }
        return addressHex.toLowerCase();
    }

    public static String exprToString(BitVecExpr e) {
        return e == null ? "null" : e.toString();
    }

    public static PoC newPoc(PoCCategory category, Machine machine) {
        if (machine.getState().isUsingProxy()) {
            return newPocUsingProxy(category, machine, machine.getManager().getProxyAddressHex());
        }

        MachineState state = machine.getState();
        final Solver solver = state.getSolver();

        Model model = Z3Util.getModel(solver);
        List<Payload> payloads = Lists.newArrayList();
        final UserInputHistory userInputHistory = state.getUserInputHistory();
        final String to = state.getContextAddressHex();
        final Integer chainId = machine.getManager().getChainId();
        for (int i = 0; i < userInputHistory.size(); i++) {
            UserInput input = userInputHistory.get(i);
            String data = getHex(model, input.getData().getExpr());
            BigInteger value = getBigInteger(model, input.getValue().getExpr());
            Payload payload = new Payload(to, value, data, chainId, null);
            payloads.add(payload);
        }
        String data = getHex(model, state.getTargetData().getExpr());
        BigInteger value = getBigInteger(model, state.getTargetValue().getExpr());
        Payload payload = new Payload(to, value, data, chainId, null);
        payloads.add(payload);
        return new PoC(category, state.newPosition(), payloads);
    }

    private static PoC newPocUsingProxy(PoCCategory category, Machine machine, String proxyAddressHex) {
        MachineState state = machine.getState();
        final Solver solver = state.getSolver();

        Model model = Z3Util.getModel(solver);
        List<Payload> payloads = Lists.newArrayList();
        final UserInputHistory userInputHistory = state.getUserInputHistory();
        final Integer chainId = machine.getManager().getChainId();
        String targetAddressHex = state.getTargetAddressHex();

        for (int i = 0; i < userInputHistory.size(); i++) {
            // proxy.call
            UserInput input = userInputHistory.get(i);
            String data = getHex(model, input.getData().getExpr());
            BigInteger proxyValue = getBigInteger(model, input.getValue().getExpr());
            String proxyData = Constants.PROXY_CALL_SIGNATURE +
                    "000000000000000000000000" + targetAddressHex +
                    "0000000000000000000000000000000000000000000000000000000000000040" +
                    String.format("%064x", data.length() / 2) +
                    data;
            Payload payload = new Payload(proxyAddressHex, proxyValue, proxyData, chainId, null);
            payloads.add(payload);
        }

        if (category == PoCCategory.ERC20) {
            String data = getHex(model, state.getTargetData().getExpr());
            BigInteger value = getBigInteger(model, state.getTargetValue().getExpr());
            Payload payload = new Payload(targetAddressHex, value, data, chainId, null);
            payloads.add(payload);
            return new PoC(category, state.newPosition(), payloads);
        }

        if (state.getReentrancyData() != null && state.getReentrancyValue() != null) {
            // proxy.register
            String data = getHex(model, state.getReentrancyData().getExpr());
            BigInteger proxyValue = getBigInteger(model, state.getReentrancyValue().getExpr());
            String proxyData = Constants.PROXY_REGISTER_SIGNATURE +
                    "000000000000000000000000" + targetAddressHex +
                    "0000000000000000000000000000000000000000000000000000000000000040" +
                    String.format("%064x", data.length() / 2) +
                    data;
            Payload payload = new Payload(proxyAddressHex, proxyValue, proxyData, chainId, null);
            payloads.add(payload);
        }

        {
            // proxy.call
            String data = getHex(model, state.getTargetData().getExpr());
            BigInteger proxyValue = getBigInteger(model, state.getTargetValue().getExpr());
            String proxyData = Constants.PROXY_CALL_SIGNATURE +
                    "000000000000000000000000" + targetAddressHex +
                    "0000000000000000000000000000000000000000000000000000000000000040" +
                    String.format("%064x", data.length() / 2) +
                    data;
            Payload payload = new Payload(proxyAddressHex, proxyValue, proxyData, chainId, null);
            payloads.add(payload);
        }

        {
            // proxy.withdraw
            String proxyData = Constants.PROXY_WITHDRAW_SIGNATURE;
            Payload payload = new Payload(proxyAddressHex, BigInteger.ZERO, proxyData, chainId, null);
            payloads.add(payload);
        }

        return new PoC(category, state.newPosition(), payloads);
    }

    public static File getPoCFile(PoC poc) {
        Position p = poc.getSink();
        return new File(Constants.RESULT_DIRECTORY, String.format("%s.json", p.getContextAddress().substring(2)));
    }

    public static String addHexPrefix(String addressHex) {
        if (addressHex == null || addressHex.length() == 0) {
            return "";
        }
        return addressHex.startsWith("0x") ? addressHex : "0x" + addressHex;
    }

    public static void addZeroValueConstraints(Machine machine) {
        MachineState state = machine.getState();
        for (Value value : state.getTxValues()) {
            state.addZeroValueConstraint(value.getExpr());
        }
    }

    public static int getInt(BitVecNum num) {
        try {
            return num.getBigInteger().intValueExact();
        } catch (ArithmeticException e) {
            log.debug("", e);
            return -1;
        }
    }

    public static EthereumImpl getEthereumImpl(NetworkType type, boolean sync, File database)
            throws IOException, InterruptedException {
        EthereumImplFactory factory = new EthereumImplFactory(type, sync, database);
        return factory.getEthereumImpl();
    }

    public static byte[] getBytes(File file, char[] password) {
        byte[] result;
        if (password != null) {
            try {
                String enckeyHex = new String(Files.readAllBytes(file.toPath()));
                if (!enckeyHex.matches("[0-9a-fA-F]{248}")) {
                    System.out.println(file + "の内容が不正です。16進数文字列で記載してください。");
                    System.exit(1);
                    throw new RuntimeException();
                }
                byte[] enckey = ByteUtil.hexStringToBytes(enckeyHex);
                byte[] xorkey = HashUtil.sha3(new String(password).getBytes());
                result = enckey;
                for (int i = 0; i < 32; i++) {
                    result[i] = (byte) (result[i] ^ xorkey[i]);
                }
            } catch (IOException e) {
                System.out.println(file + "を開くことができません。読み取り権限を付与してください。");
                System.exit(1);
                throw new RuntimeException(e);
            }
        } else {
            try {
                String privkeyHex = new String(Files.readAllBytes(file.toPath()));
                if (!privkeyHex.matches("[0-9a-fA-F]{248}")) {
                    System.out.println(file + "の内容が不正です。16進数文字列で記載してください。");
                    System.exit(1);
                    throw new RuntimeException();
                }
                result = ByteUtil.hexStringToBytes(privkeyHex);
            } catch (IOException e) {
                System.out.println(file + "を開くことができません。読み取り権限を付与してください。");
                System.exit(1);
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    public static RocksDB getDb(String path) {
        try (Options options = new Options()) {
            options.setCreateIfMissing(true);
            options.setIncreaseParallelism(2);
            return RocksDB.open(options, path);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isERC20(String hex) {
        return hex.contains(Constants.ERC20_TOTALSUPPLY_SIGNATURE) &&
                hex.contains(Constants.ERC20_BALANCEOF_SIGNATURE) &&
                hex.contains(Constants.ERC20_TRANSFER_SIGNATURE) &&
                hex.contains(Constants.ERC20_TRANSFERFROM_SIGNATURE) &&
                hex.contains(Constants.ERC20_APPROVE_SIGNATURE) &&
                hex.contains(Constants.ERC20_ALLOWANCE_SIGNATURE);
    }
}
