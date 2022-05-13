package org.unichain.common.utils;

import lombok.Builder;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.crypto.Hash;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j(topic = "PosBridge")
public class PosBridgeUtil {
    private static String BLIND_URI_HEX = "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000020687474703a2f2f6a7573745f6175746f5f67656e2e6f72672f78782e6a736f6e";

    @Builder
    @ToString
    public static class PosBridgeDepositExecMsg {
        public long rootChainId;
        public String rootTokenAddr;
        public long childChainId;
        public String receiveAddr;
        public long value;
        public String extHex;
    }

    @Builder
    @ToString
    public static class PosBridgeWithdrawExecMsg {
        public long childChainId;
        public String childTokenAddr;
        public long rootChainId;
        public String receiveAddr;
        public long value;
    }

    /**
     * make sure all hex is not prefixed with 0x
     */
    public static void validateSignatures(final String msgHex, final List<String> hexSignatures, final PosBridgeConfigCapsule config) throws Exception{
        try{
            var msg = Hex.decodeHex(msgHex);
            var whitelist = config.getValidators();
            var signedValidators = new HashMap<String, String>();
            for(var sigHex : hexSignatures){
                var sig = Hex.decodeHex(sigHex);
                var hash = Hash.sha3(msg);
                var signedAddr = Hex.encodeHexString(ECKey.signatureToAddress(hash, sig))
                        .toLowerCase()
                        .substring(2);
                if(whitelist.containsKey(signedAddr))
                    signedValidators.put(signedAddr, signedAddr);
            };
            var rate = ((double)signedValidators.size())/whitelist.size();
            Assert.isTrue(rate >= config.getConsensusRate(), "not enough POS bridge's consensus rate");
        }
        catch (Exception e){
            logger.error("validate signature failed -->", e);
            throw e;
        }
    }

    /**
     calldata format:
     {
         message: bytes[] // abi encoded {
             uint32 rootChainId,
             uint32 childChainId,
             address rootToken,
             address receiverAddr,
             bytes value
         }
     }
     */
    public static PosBridgeDepositExecMsg decodePosBridgeDepositExecMsg(String msgHex) {
        List<TypeReference<?>> types = new ArrayList<>();
        TypeReference<Uint32> type1 = new TypeReference<Uint32>() {};
        TypeReference<Uint32> type2 = new TypeReference<Uint32>() {};
        TypeReference<Address> type3 = new TypeReference<Address>() {};
        TypeReference<Address> type4 = new TypeReference<Address>() {};
        TypeReference<DynamicBytes> type5 = new TypeReference<DynamicBytes>() {};
        types.add(type1);
        types.add(type2);
        types.add(type3);
        types.add(type4);
        types.add(type5);
        List<Type> out = FunctionReturnDecoder.decode(msgHex, org.web3j.abi.Utils.convert(types));

        Uint256 value = abiDecodeToUint256((DynamicBytes)out.get(4));

        return  PosBridgeDepositExecMsg.builder()
                .rootChainId(((Uint32)out.get(0)).getValue().longValue())
                .childChainId(((Uint32)out.get(1)).getValue().longValue())
                .rootTokenAddr(((Address)out.get(2)).getValue())
                .receiveAddr(((Address)out.get(3)).getValue())
                .value(value.getValue().longValue())
                .extHex(BLIND_URI_HEX) //@todo add uri msg in source msg
                .build();
    }

    public static Uint256 abiDecodeToUint256(DynamicBytes bytes){
        List<TypeReference<?>> valueTypes = new ArrayList<>();
        valueTypes.add(new TypeReference<Uint256>() {});
        Uint256 value = (Uint256)FunctionReturnDecoder.decode(Hex.encodeHexString((bytes).getValue()), org.web3j.abi.Utils.convert(valueTypes))
                .get(0);
        return value;
    }

    public static String abiDecodeFromToString(String hex){
        List<TypeReference<?>> types = new ArrayList<>();
        types.add(new TypeReference<Utf8String>() {});
        return ((Utf8String)FunctionReturnDecoder.decode(hex, org.web3j.abi.Utils.convert(types))
                .get(0)).getValue();
    }


    /**
     message: bytes[] // abi encoded {
         uint32 childChainId,
         uint32 rootChainId,
         address childToken,
         address receiveAddr,
         bytes value
     }
     */
    public static PosBridgeWithdrawExecMsg decodePosBridgeWithdrawExecMsg(String msgHex) {
        List<TypeReference<?>> types = new ArrayList<>();
        TypeReference<Uint32> type1 = new TypeReference<Uint32>() {};
        TypeReference<Uint32> type2 = new TypeReference<Uint32>() {};
        TypeReference<Address> type3 = new TypeReference<Address>() {};
        TypeReference<Address> type4 = new TypeReference<Address>() {};
        TypeReference<DynamicBytes> type5 = new TypeReference<DynamicBytes>() {};
        types.add(type1);
        types.add(type2);
        types.add(type3);
        types.add(type4);
        types.add(type5);
        List<Type> out = FunctionReturnDecoder.decode(msgHex, org.web3j.abi.Utils.convert(types));

        //decode value as unint256
        List<TypeReference<?>> valueTypes = new ArrayList<>();
        valueTypes.add(new TypeReference<Uint256>() {});
        Uint256 value = (Uint256)FunctionReturnDecoder.decode(Hex.encodeHexString(((DynamicBytes)out.get(4)).getValue()), org.web3j.abi.Utils.convert(valueTypes))
                .get(0);

        return  PosBridgeWithdrawExecMsg.builder()
                .childChainId(((Uint32)out.get(0)).getValue().longValue())
                .rootChainId(((Uint32)out.get(1)).getValue().longValue())
                .childTokenAddr(((Address)out.get(2)).getValue())
                .receiveAddr(((Address)out.get(3)).getValue())
                .value(value.getValue().longValue())
                .build();
    }

    public static String makeTokenMapKey(long chainId, String token){
        return (Long.toHexString(chainId) + "_" + token);
    }

    public static String makeTokenMapKey(String chainIdHex, String token){
        return (chainIdHex + "_" + token);
    }

    public static byte[] makeTokenMapKeyBytes(long chainId, String token) {
        return makeTokenMapKey(chainId, token).getBytes();
    }

    public static boolean isUnichain(long chainId){
        return (chainId == (long) Wallet.getAddressPreFixByte());
    }
}
