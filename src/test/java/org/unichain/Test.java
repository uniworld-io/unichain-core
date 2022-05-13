package org.unichain;

import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.protos.Protocol;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.crypto.Hash;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

import java.util.ArrayList;
import java.util.List;

public class Test {
    public static void main(String[] args) {
//        testGenAddr();
//        testRlpDecode();
//        testSignatureRecover();
//        testAbiCodec();
//        testValidatorCodec();
        testDecodeWithdrawMsg();
//        testValidatingSignatures();
//        testBase58Decode();
    }


    private static void testBase58Decode(){
        try {
//            byte[] addr0 = Numeric.hexStringToByteArray("0x21Aa7b195bc748D506E07D678fbDCaE7dF579Cff");
//            Assert.isTrue(Wallet.addressValid(addr0), "invalid address");
            Assert.isTrue(org.web3j.crypto.WalletUtils.isValidAddress("0x21Aa7b195bc748D506E07D678fbDCaE7dF579Cff"), "invalid web3 address");
            Assert.isTrue(org.web3j.crypto.WalletUtils.isValidAddress("0x44fff11519410945baae942b9b8da46eb1aecf7897"), "invalid web3  address");
            Assert.isTrue(Wallet.addressValid(Numeric.hexStringToByteArray("0x44fff11519410945baae942b9b8da46eb1aecf7897")), "invalid tronx address");

            //            System.out.println("+++ testBase58Decode hex: " + Hex.encodeHexString(addr));
//
            byte[] addr = Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_GENESIS_ADMIN_WALLET);
            System.out.println("+++ testBase58Decode hex: SUCCESS" + Numeric.toHexString(addr));
        }
        catch (Exception e){
            System.err.println("+++ testBase58Decode: FAILED --> " + e);
        }
    }

    private static void testValidatingSignatures(){
        try {
            var msgHex = "0000000000000000000000000000000000000000000000000000000000001092000000000000000000000000000000000000000000000000000000000000264500000000000000000000000079aa45e8ef1419be485c906ec327ea0ed1b6274c0000000000000000000000003ca8b76a67aa25482dcd70cabfc05561f8f67fd300000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000003e8";
            var sigList = new ArrayList<String>();
            sigList.add("02774923c6b29efcf4e49b7bdac7a37813ae863ea0e2cfe43687ba1fb70c9fcd2b4558f4d94e4bd3ed0585b4169c269d4cbc89e841b73aaad45d08f3a26d25571b");
            var signer = "4b194A3fdd790c31C0559b221f182eEdC049be3f".toLowerCase();

            var config = Protocol.PosBridgeConfig.newBuilder()
                    .setMinValidator(1)
                    .setConsensusRate(70)
                    .putValidators(signer, signer)
                    .setInitialized(true)
                    .build();

            var configCap = new PosBridgeConfigCapsule(config);

            PosBridgeUtil.validateSignatures(msgHex, sigList, configCap);

            System.out.println("+++ testValidatingSignatures: SUCCESS --> " + config);
        }
        catch (Exception e){
            System.err.println("+++ testValidatingSignatures: FAILED --> " + e);
        }
    }

    private static void testDecodeWithdrawMsg(){
        try {
            //expect 9797,4242,0x7488EAFF3632A4Cc413c2F9a04c970ca972dc97C,0x3ca8b76a67Aa25482dCd70cAbfc05561f8F67fd3,0x00000000000000000000000000000000000000000000000000000000000003e8
            var expectDecode = PosBridgeUtil.PosBridgeWithdrawExecMsg.builder()
                    .childChainId(9797L)
                    .rootChainId(4242L)
                    .childTokenAddr("0x7488EAFF3632A4Cc413c2F9a04c970ca972dc97C".toLowerCase())
                    .receiveAddr("0x3ca8b76a67Aa25482dCd70cAbfc05561f8F67fd3".toLowerCase())
                    .value(1000L);

            String input = "0x000000000000000000000000000000000000000000000000000000000000264500000000000000000000000000000000000000000000000000000000000010920000000000000000000000007488eaff3632a4cc413c2f9a04c970ca972dc97c0000000000000000000000003ca8b76a67aa25482dcd70cabfc05561f8f67fd300000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000003e8";
            var decoded = PosBridgeUtil.decodePosBridgeWithdrawExecMsg(input);
            System.out.println("+++ decoded testDecodeWithdrawMsg: " + decoded);
            System.out.println("+++ expected : " + expectDecode);
        }
        catch (Exception e){
            System.err.println("+++error while testDecodeWithdrawMsg --> " + e);
        }
    }

    private static void testValidatorCodec(){
        String addr1 = "0x7488eaff3632a4cc413c2f9a04c970ca972dc97c";
        String addr2 = "0x3ca8b76a67aa25482dcd70cabfc05561f8f67fd3";

        String msg = "0x00000000000000000000000000000000000000000000000000000000000026450000000000000000000000007488eaff3632a4cc413c2f9a04c970ca972dc97c0000000000000000000000003ca8b76a67aa25482dcd70cabfc05561f8f67fd30000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000003e8";
        String signatures = "0x8d13ac0c7c932aa9e64e4c0fd098214270591ae0c5ca18ec2dd54a25405179b361b13d69fb4d6bd590ff6d7bba440b08c668178549533726d4f61ea83dc7eae71c";

        /**
         * Decode as
         * 'uint32', 'address', 'address', 'bytes'
         */
        List<TypeReference<?>> typeReferences = new ArrayList<>();
        TypeReference<Uint32> t1 = new TypeReference<Uint32>() {
        };

        TypeReference<Address> t2 = new TypeReference<Address>() {
        };

        TypeReference<Address> t3 = new TypeReference<Address>() {
        };

        TypeReference<DynamicBytes> t4 = new TypeReference<DynamicBytes>() {
        };

        typeReferences.add(t1);
        typeReferences.add(t2);
        typeReferences.add(t3);
        typeReferences.add(t4);
        List<Type> ret = FunctionReturnDecoder.decode(msg, org.web3j.abi.Utils.convert(typeReferences));

        Address rv2 = (Address) ret.get(1);
        Address rv3 = (Address) ret.get(2);
        System.out.println("+++ decode value0:" + rv2.toString());
        System.out.println("+++ decode addr1:" + rv2.toString());
        System.out.println("+++ decode addr2:" + rv3.toString());
    }

    private static void testAbiCodec(){
        try{
            String sig = "d1c8577e12e92b8f40abe14867a40881df093f28811c390c5409fb1a6735178113b82d4ea5707f101c93df71847e0797dac1871f8b9501c95baba4bfedb4b5711c";
            String msgHash = "f6b69759d4b765f83547e3bcfc6523d613b2abf1d6db4dffa1e896e4c1310124";

            List<org.web3j.abi.datatypes.Type> params = new ArrayList<>();
            Int256 v1 = new Int256(10249999L);
            Bool v2 = new Bool(true);
            DynamicBytes v3 = new DynamicBytes(Hex.decodeHex(sig));
            DynamicBytes v4 = new DynamicBytes(Hex.decodeHex(msgHash));
            params.add(v1);
            params.add(v2);
            params.add(v3);
            params.add(v4);


            String hex = FunctionEncoder.encodeConstructor(params);
            System.out.println("++++ encoded hex: " + hex);

            //decode
            List<TypeReference<?>> typeReferences = new ArrayList<>();
            TypeReference<Int256> t1 = new TypeReference<Int256>() {
            };

            TypeReference<Bool> t2 = new TypeReference<Bool>() {
            };

            TypeReference<DynamicBytes> t3 = new TypeReference<DynamicBytes>() {
            };

            TypeReference<DynamicBytes> t4 = new TypeReference<DynamicBytes>() {
            };

            typeReferences.add(t1);
            typeReferences.add(t2);
            typeReferences.add(t3);
            typeReferences.add(t4);
            List<Type> ret = FunctionReturnDecoder.decode(hex, org.web3j.abi.Utils.convert(typeReferences));
            DynamicBytes rv4 = (DynamicBytes) ret.get(3);
            String rhash = Hex.encodeHexString(rv4.getValue());
            System.out.println("+++ decode hash :" + rhash);
            System.out.println("+++ same ? " + (rhash.equals(msgHash)));


            //how to encode multiple arrays of bytes ?
        }
        catch (Exception e){
            System.err.println("error while codec --> " + e);
        }
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
