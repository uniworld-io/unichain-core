package org.unichain;

import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.protos.Protocol;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.crypto.Hash;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TestPOSBridge {

    @org.junit.Test
    public void testEtherFamilyAddress(){
        org.junit.Assert.assertTrue("invalid web3 address", org.web3j.crypto.WalletUtils.isValidAddress("0x21Aa7b195bc748D506E07D678fbDCaE7dF579Cff"));
        org.junit.Assert.assertTrue("invalid web3 address", !org.web3j.crypto.WalletUtils.isValidAddress("0x44fff11519410945baae942b9b8da46eb1aecf7897"));
        org.junit.Assert.assertTrue("invalid unichain address", Wallet.addressValid(Numeric.hexStringToByteArray("0x44fff11519410945baae942b9b8da46eb1aecf7897")));
    }

    @org.junit.Test
    public  void testBase58Decode(){
        var addr = Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POS_BRIDGE_GENESIS_ADMIN_WALLET);
        Assert.assertTrue( "invalid Genesis admin wallet", addr!= null);
        System.out.println("+++ testBase58Decode : SUCCESS" + Numeric.toHexString(addr));
    }

    @org.junit.Test
    public void testValidatingSignatures(){
        try {
            var msgHex = "0000000000000000000000000000000000000000000000000000000000001092000000000000000000000000000000000000000000000000000000000000264500000000000000000000000079aa45e8ef1419be485c906ec327ea0ed1b6274c0000000000000000000000003ca8b76a67aa25482dcd70cabfc05561f8f67fd300000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000003e8";
            var sigList = Arrays.asList("02774923c6b29efcf4e49b7bdac7a37813ae863ea0e2cfe43687ba1fb70c9fcd2b4558f4d94e4bd3ed0585b4169c269d4cbc89e841b73aaad45d08f3a26d25571b");
            var signer = "4b194A3fdd790c31C0559b221f182eEdC049be3f".toLowerCase();

            var config = Protocol.PosBridgeConfig.newBuilder()
                    .setMinValidator(1)
                    .setConsensusRate(70)
                    .putValidators(signer, signer)
                    .setInitialized(true)
                    .build();

            var configCap = new PosBridgeConfigCapsule(config);

            Assert.assertTrue("invalid signature", PosBridgeUtil.validateSignatures(msgHex, sigList, configCap));
            System.out.println("+++ testValidatingSignatures: SUCCESS --> " + config);
        }
        catch (Exception e){
            System.err.println("+++ testValidatingSignatures: FAILED --> " + e);
        }
    }

//    @org.junit.Test
    public void testDecodeWithdrawMsg(){
            //expect 9797,4242,0x7488EAFF3632A4Cc413c2F9a04c970ca972dc97C,0x3ca8b76a67Aa25482dCd70cAbfc05561f8F67fd3,0x00000000000000000000000000000000000000000000000000000000000003e8
            var expectDecode = PosBridgeUtil.PosBridgeWithdrawExecMsg.builder()
                    .childChainId(9797L)
                    .rootChainId(4242L)
                    .childTokenAddr("0x7488EAFF3632A4Cc413c2F9a04c970ca972dc97C".toLowerCase())
                    .receiveAddr("0x3ca8b76a67Aa25482dCd70cAbfc05561f8F67fd3".toLowerCase())
                    .withdrawData(null)
                    .build();

            String encoded = "0x000000000000000000000000000000000000000000000000000000000000264500000000000000000000000000000000000000000000000000000000000010920000000000000000000000007488eaff3632a4cc413c2f9a04c970ca972dc97c0000000000000000000000003ca8b76a67aa25482dcd70cabfc05561f8f67fd300000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000003e8";
            var decoded = PosBridgeUtil.decodePosBridgeWithdrawExecMsg(encoded);
            Assert.assertTrue("unmatched decode msg, expected : " +  expectDecode + "\n got: " + decoded, (decoded.childChainId == expectDecode.childChainId)
                    && (decoded.rootChainId == expectDecode.rootChainId)
                    && (decoded.childTokenAddr.equalsIgnoreCase(expectDecode.childTokenAddr))
                    && (decoded.receiveAddr.equalsIgnoreCase(expectDecode.receiveAddr)));
    }

    @org.junit.Test
    public void testValidatorCodec(){
        String addr1 = "0x7488eaff3632a4cc413c2f9a04c970ca972dc97c";
        String addr2 = "0x3ca8b76a67aa25482dcd70cabfc05561f8f67fd3";
        String msg = "0x00000000000000000000000000000000000000000000000000000000000026450000000000000000000000007488eaff3632a4cc413c2f9a04c970ca972dc97c0000000000000000000000003ca8b76a67aa25482dcd70cabfc05561f8f67fd30000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000003e8";

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

        Uint32 rValue = (Uint32) ret.get(0);
        Address rAddr1 = (Address) ret.get(1);
        Address rAddr2 = (Address) ret.get(2);
        Assert.assertTrue("ummatched address1", rAddr1.toString().equalsIgnoreCase(addr1));
        Assert.assertTrue("ummatched address2", rAddr2.toString().equalsIgnoreCase(addr2));
    }

//    @org.junit.Test
    public void testRecoverAddressFromSignature() throws Exception{
            //origin data
            String addr = "b4d72725cd653156208a1c21dfb43463e555a0e2";
            String privKey = "a21e45fc1a9c50281d8b7267f0b2968ceb932ad32e4562c0b29dbd48b7bade74";
            String msgHash = "f6b69759d4b765f83547e3bcfc6523d613b2abf1d6db4dffa1e896e4c1310124";
            String sig = "d1c8577e12e92b8f40abe14867a40881df093f28811c390c5409fb1a6735178113b82d4ea5707f101c93df71847e0797dac1871f8b9501c95baba4bfedb4b5711c";
            String msg = "hello baby";

            //recover
            String recoverHash = Hash.sha3String(msg);
            byte[] hashByte = Hex.decodeHex(recoverHash);
            byte[] sigByte = Hex.decodeHex(sig);
            String addrRecover = Numeric.prependHexPrefix(Numeric.toHexString(ECKey.signatureToAddress(hashByte, sigByte)));
            Assert.assertTrue("unmatched recover addr: " + addrRecover + " vs " + addr, addrRecover.equalsIgnoreCase(addr));
    }

    @org.junit.Test
    public void testGenAddr(){
        ECKey ecKey = new ECKey(Utils.getRandom());
        byte[] priKey = ecKey.getPrivKeyBytes();
        byte[] address = ecKey.getAddress();
        String priKeyStr = Hex.encodeHexString(priKey);
        String base58check = Wallet.encode58Check(address);
        String hexString = ByteArray.toHexString(address);
        Assert.assertTrue("Bad priKeyStr", Objects.nonNull(priKeyStr));
        Assert.assertTrue("Bad base58check", Objects.nonNull(base58check));
        Assert.assertTrue("Bad hexString", Objects.nonNull(hexString));
    }

    @org.junit.Test
    public void testRlpDecode() throws Exception{
            String encodedHex = "f864b841d1c8577e12e92b8f40abe14867a40881df093f28811c390c5409fb1a6735178113b82d4ea5707f101c93df71847e0797dac1871f8b9501c95baba4bfedb4b5711ca0f6b69759d4b765f83547e3bcfc6523d613b2abf1d6db4dffa1e896e4c1310124";
            String part1Hex = "d1c8577e12e92b8f40abe14867a40881df093f28811c390c5409fb1a6735178113b82d4ea5707f101c93df71847e0797dac1871f8b9501c95baba4bfedb4b5711c";
            String part2Hex = "f6b69759d4b765f83547e3bcfc6523d613b2abf1d6db4dffa1e896e4c1310124";
            for(var item : RlpDecoder.decode(Hex.decodeHex(encodedHex)).getValues()){
                RlpString rlpString  = (RlpString) ((RlpList) item).getValues().get(0);
                var rlpHex  = Hex.encodeHexString(rlpString.getBytes());
                Assert.assertTrue(Objects.nonNull(rlpHex));
                System.out.println("++ decoded: " + rlpHex);
                rlpString  = (RlpString) ((RlpList) item).getValues().get(1);
                Assert.assertTrue(Objects.nonNull(rlpString));
                System.out.println("++ decoded: " + Hex.encodeHexString(rlpString.getBytes()));
            }
    }
}
