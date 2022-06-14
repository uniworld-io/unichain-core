package org.unichain;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;

import java.math.BigInteger;
import java.util.Arrays;

@Slf4j(topic = "Test")
public class DecimalTest {
    @org.junit.Test
    public void testBigInteger(){
        String b1Str = "100000000000000000000000000000100000000000000000000000000000100000000000000000000000000000";
        String b2Str = "100000000000000000000000000000100000000000000000000000000000100000000000000000000000000000";
        BigInteger b1 = new BigInteger(b1Str);
        BigInteger b2 = new BigInteger(b2Str);
        byte[] b1Arr = b1.toByteArray();
        byte[] b2Arr = b2.toByteArray();
        Assert.assertTrue("must be the same", Arrays.equals(b1.toByteArray(), b2.toByteArray()));
        Assert.assertTrue("must be the same", (new BigInteger(b1Arr)).compareTo(new BigInteger(b2Arr)) == 0);
        System.out.println("b1: " + Hex.encodeHexString(b1Arr));
        System.out.println("b2: " + Hex.encodeHexString(b2Arr));
        Assert.assertTrue(b1Str.equals(b1.toString()));
        Assert.assertTrue(b2Str.equals(b2.toString()));
        System.out.println("b1Str: " + b1Str);
        System.out.println("b2Str: " + b2Str);
    }
}
