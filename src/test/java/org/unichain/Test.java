package org.unichain;

import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.web3j.crypto.Hash;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

public class Test {
    public static void main(String[] args) {
//        testGenAddr();
//        testRlpDecode();
        testSignatureRecover();
    }

    private static void testSignatureRecover(){
        try {
            //running live test
            String addr = "b4d72725cd653156208a1c21dfb43463e555a0e2";
            String privKey = "a21e45fc1a9c50281d8b7267f0b2968ceb932ad32e4562c0b29dbd48b7bade74";
            String msgHash = "f6b69759d4b765f83547e3bcfc6523d613b2abf1d6db4dffa1e896e4c1310124";
            String sig = "d1c8577e12e92b8f40abe14867a40881df093f28811c390c5409fb1a6735178113b82d4ea5707f101c93df71847e0797dac1871f8b9501c95baba4bfedb4b5711c";
            String msg = "hello baby";

            String hash2 = Hash.sha3String(msg);
            //recover
            byte[] hashByte = Hex.decodeHex(msgHash);
            byte[] sigByte = Hex.decodeHex(sig);
            byte[] addrByte = ECKey.signatureToAddress(hashByte, sigByte);
            System.out.println("+++++++ addess hex recovered: [" + Hex.encodeHexString(addrByte));
            System.out.println("+++++++ hashcode [" + hash2);
            System.out.println("++++ COMPLETED+++");
        } catch (Exception e) {
            System.err.println("error while recover address --> " + e);
        }
    }

    private static void testGenAddr(){
        try {
            ECKey ecKey = new ECKey(Utils.getRandom());
            byte[] priKey = ecKey.getPrivKeyBytes();
            byte[] address = ecKey.getAddress();
            String priKeyStr = Hex.encodeHexString(priKey);
            String base58check = Wallet.encode58Check(address);
            String hexString = ByteArray.toHexString(address);
            System.out.println("hex addr: " + hexString);
            System.out.println("hex priv: " + priKeyStr);
        }
        catch (Exception e){
            System.err.println("error while gen address --> " + e);
        }
    }

    private static void testRlpDecode(){
        try {
            String encodedHex = "f864b841d1c8577e12e92b8f40abe14867a40881df093f28811c390c5409fb1a6735178113b82d4ea5707f101c93df71847e0797dac1871f8b9501c95baba4bfedb4b5711ca0f6b69759d4b765f83547e3bcfc6523d613b2abf1d6db4dffa1e896e4c1310124";
            String part1Hex = "d1c8577e12e92b8f40abe14867a40881df093f28811c390c5409fb1a6735178113b82d4ea5707f101c93df71847e0797dac1871f8b9501c95baba4bfedb4b5711c";
            String part2Hex = "f6b69759d4b765f83547e3bcfc6523d613b2abf1d6db4dffa1e896e4c1310124";
            for(var item : RlpDecoder.decode(Hex.decodeHex(encodedHex)).getValues()){
                RlpString rlpString  = (RlpString) ((RlpList) item).getValues().get(0);
                System.out.println("++ decoded: " + Hex.encodeHexString(rlpString.getBytes()));
                rlpString  = (RlpString) ((RlpList) item).getValues().get(1);
                System.out.println("++ decoded: " + Hex.encodeHexString(rlpString.getBytes()));
            }

            System.out.println("++++ COMPLETED+++");
        } catch (Exception e) {
            System.err.println("error while recover address --> " + e);
        }
    }
}
