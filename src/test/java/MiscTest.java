import com.google.common.collect.Lists;
import net.katagaitai.tagomori.evm.Code;
import net.katagaitai.tagomori.util.Constants;
import org.ethereum.util.ByteUtil;
import org.junit.Test;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.DecoderException;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.Assert.*;

public class MiscTest {
    @Test
    public void test_配列のequalsはObjectのequalsになる() {
        //noinspection ArrayEquals
        assertFalse(new byte[]{1, 2, 3}.equals(new byte[]{1, 2, 3}));
        assertTrue(Arrays.equals(new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
    }

    @Test
    public void test_lombolのEqualsAndHashCodeはArraysのequalを使う() {
        assertTrue(new Code(new byte[]{1, 2, 3}).equals(new Code(new byte[]{1, 2, 3})));
    }

    @Test
    public void test_LinkedListのpopはget0と同じ値を返す() {
        LinkedList<Integer> list = Lists.newLinkedList(Lists.newArrayList(1, 2, 3));
        assertEquals(list.get(0), list.pop());
    }

    @Test
    public void test_Hexdecodeは0文字だと空配列を返す() {
        assertTrue(Arrays.equals(new byte[0], Hex.decode("")));
    }

    @Test(expected = DecoderException.class)
    public void test_Hexdecodeは1文字だとDecoderExceptionが出る() {
        Hex.decode("2");
    }

    @Test
    public void test_Hexdecodeは2文字だとDecoderExceptionが出ない() {
        assertTrue(Arrays.equals(new byte[]{2}, Hex.decode("02")));
    }

    @Test
    public void test_ByteUtil() {
        assertEquals("", ByteUtil.toHexString(new byte[0]));
        assertTrue(Arrays.equals(new byte[0], ByteUtil.hexStringToBytes("")));
    }

    @Test
    public void test_BigIntegers_asUnsignedByteArray() {
        BigInteger bi_bytes_msb0 = BigInteger.valueOf(0x7f);
        // signumが1の場合、MSBが0ならそのまま出力する
        assertTrue(Arrays.equals(new byte[]{(byte) 0x7f}, bi_bytes_msb0.toByteArray()));
        // asUnsignedByteArrayだとそのままのバイト列になる
        assertTrue(Arrays.equals(new byte[]{(byte) 0x7f}, BigIntegers.asUnsignedByteArray(bi_bytes_msb0)));
        // bigIntegerToBytesも同様
        assertTrue(Arrays.equals(new byte[]{(byte) 0x7f}, ByteUtil.bigIntegerToBytes(bi_bytes_msb0)));

        BigInteger bi_bytes_msb1 = BigInteger.valueOf(0xff);
        // signumが1の場合、byte[]のMSBが1にならないように先頭に0x00が追加される
        assertTrue(Arrays.equals(new byte[]{0, (byte) 0xff}, bi_bytes_msb1.toByteArray()));
        // asUnsignedByteArrayだとそのままのバイト列になる
        assertTrue(Arrays.equals(new byte[]{-1}, BigIntegers.asUnsignedByteArray(bi_bytes_msb1)));
        // bigIntegerToBytesも同様
        assertTrue(Arrays.equals(new byte[]{-1}, ByteUtil.bigIntegerToBytes(bi_bytes_msb1)));
    }

    @Test
    public void test_ByteUtil_toHexStringは先頭の0を出力する() {
        assertEquals("0123", ByteUtil.toHexString(new byte[]{0x01, 0x23}));
    }

    @Test
    public void test_WeiToHex() {
        System.out.println(BigInteger.valueOf(10).pow(18).toString(16));
    }

    @Test
    public void test_printPublicKey() {
        System.out.println(ByteUtil.toHexString(Constants.SENDER_EC_KEY.getAddress()));
    }
}
