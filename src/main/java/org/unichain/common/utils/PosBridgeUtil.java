package org.unichain.common.utils;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.crypto.Hash;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.posbridge.ext.*;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.utils.Numeric;

import java.util.*;

@Slf4j(topic = "PosBridge")
public class PosBridgeUtil {

    /**
     * null address
     */
    public static class NativeToken{
        private NativeToken(){}

        public static final String BNB = "0x000000000000000000000000000000000000dEaD";
        public static final String ETH = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE";
        public static final String UNI = "0x4416748f8d05163e917388fa79050bafe5a30faa2f";

        public static boolean contains(final String address){
            if(BNB.equalsIgnoreCase(address) || ETH.equalsIgnoreCase(address) || UNI.equalsIgnoreCase(address)){
                return true;
            }
            return false;
        }
    }

    @Getter
    public enum AssetType{
        NATIVE(1, "NATIVE"),
        ERC20(2, "ERC20"),
        ERC721(3, "ERC721");

        private final int number;
        private final String type;

        AssetType(int number, String type){
            this.number = number;
            this.type = type;
        }
        public static AssetType valueOfNumber(int number) throws IllegalAccessException {
            switch (number){
                case 1:
                    return NATIVE;
                case 2:
                    return ERC20;
                case 3:
                    return ERC721;
                default:
                    throw new IllegalAccessException("Not mapped asset type");
            }
        }
    }

    public static Predicate lookupPredicate(int numberType, Manager dbManager, TransactionResultCapsule ret, PosBridgeConfigCapsule config) throws Exception {
        AssetType assetType = AssetType.valueOfNumber(numberType);
        switch (assetType){
            case NATIVE: {
                return new UnwPredicate(dbManager, ret, config);
            }
            case ERC20: {
                return new Urc20Predicate(dbManager, ret, config);
            }
            case ERC721: {
                return new Urc721Predicate(dbManager, ret, config);
            }
            default:
                throw new Exception("invalid asset type");
        }
    }

    public static ChildToken lookupChildToken(int numberType, Manager dbManager, TransactionResultCapsule ret) throws Exception {
        AssetType assetType = AssetType.valueOfNumber(numberType);
        switch (assetType){
            case NATIVE:
            case ERC20: {
                return new ChildTokenUrc20(dbManager, ret);
            }
            case ERC721: {
                return new ChildTokenUrc721(dbManager, ret);
            }
            default:
                throw new Exception("invalid asset type");
        }
    }

    @Builder
    @ToString
    public static class PosBridgeDepositExecMsg {
        public long rootChainId;
        public String rootTokenAddr;
        public long childChainId;
        public String receiveAddr;
        public DynamicBytes depositData;
    }

    @Builder
    @ToString
    public static class PosBridgeWithdrawExecMsg {
        public long childChainId;
        public String childTokenAddr;
        public long rootChainId;
        public String receiveAddr;
        public DynamicBytes withdrawData;
    }

    @Builder
    @ToString
    public static class ERC721Decode {
        public long tokenId;
        public String uri;
    }

    /**
     * Assume that validator address with prefix 0x
     */
    public static boolean validateSignatures(final String msgHex, final List<String> hexSignatures, final PosBridgeConfigCapsule config) throws Exception {
        try {
            var msg = Numeric.hexStringToByteArray(msgHex);
            var whitelist = config.getValidators();
            int countVerify = 0;
            Set<String> unDuplicateSignatures = new HashSet<>(hexSignatures);
            for (var sigHex : unDuplicateSignatures) {
                var sig = Numeric.hexStringToByteArray(sigHex);
                var hash = Hash.sha3(msg);
                //recover addr with prefix 0x
                var signer = Numeric.toHexString(ECKey.signatureToAddress(hash, sig)).toLowerCase(Locale.ROOT);
                logger.info("Recover address: {}", signer);
                if (whitelist.containsKey(signer))
                    countVerify++;
            }
            var rate = ((double) countVerify) / whitelist.size();
            Assert.isTrue(countVerify >= config.getMinValidator(), "LESS_THAN_MIN_VALIDATOR");
            Assert.isTrue(rate >= config.getConsensusRate(), "LESS_THAN_CONSENSUS_RATE");
            return true;
        } catch (Exception e) {
            logger.warn("validate signature: msg: {}", msgHex);
            logger.warn("validate signature: signatures: {}", hexSignatures);
            logger.error("validate signature failed -->", e);
            throw e;
        }
    }

    /**
     * calldata format:
     * {
     * message: bytes[] // abi encoded {
     * uint32 rootChainId,
     * uint32 childChainId,
     * address rootToken,
     * address receiverAddr,
     * bytes value
     * }
     * }
     */
    public static PosBridgeDepositExecMsg decodePosBridgeDepositExecMsg(String msgHex) {
        msgHex = Numeric.prependHexPrefix(msgHex);
        List<TypeReference<?>> types = new ArrayList<>();
        TypeReference<Uint32> type1 = new TypeReference<Uint32>() {
        };
        TypeReference<Uint32> type2 = new TypeReference<Uint32>() {
        };
        TypeReference<Address> type3 = new TypeReference<Address>() {
        };
        TypeReference<Address> type4 = new TypeReference<Address>() {
        };
        TypeReference<DynamicBytes> type5 = new TypeReference<DynamicBytes>() {
        };
        types.add(type1);
        types.add(type2);
        types.add(type3);
        types.add(type4);
        types.add(type5);
        List<Type> out = FunctionReturnDecoder.decode(msgHex, org.web3j.abi.Utils.convert(types));

        return PosBridgeDepositExecMsg.builder()
                .rootChainId(((Uint32) out.get(0)).getValue().longValue())
                .childChainId(((Uint32) out.get(1)).getValue().longValue())
                .rootTokenAddr(((Address) out.get(2)).getValue())
                .receiveAddr(toUniAddress(((Address) out.get(3)).getValue()))
                .depositData((DynamicBytes) out.get(4))
                .build();
    }

    public static Uint256 abiDecodeToUint256(DynamicBytes bytes) {
        return abiDecodeToUint256(Hex.encodeHexString((bytes).getValue()));
    }

    public static Uint256 abiDecodeToUint256(String hex) {
        List<TypeReference<?>> valueTypes = new ArrayList<>();
        valueTypes.add(new TypeReference<Uint256>() {
        });
        return (Uint256) FunctionReturnDecoder.decode(hex, org.web3j.abi.Utils.convert(valueTypes)).get(0);
    }

    public static ERC721Decode abiDecodeToErc721(String hex){
        List<TypeReference<?>> valueTypes = new ArrayList<>();
        valueTypes.add(new TypeReference<Uint256>() {});
        valueTypes.add(new TypeReference<Utf8String>() {});
        List<Type> types = FunctionReturnDecoder.decode(hex, org.web3j.abi.Utils.convert(valueTypes));
        return ERC721Decode.builder()
                .tokenId(((Uint256) types.get(0)).getValue().longValue())
                .uri(((Utf8String) types.get(1)).getValue())
                .build();
    }

    public static String abiDecodeFromToString(String hex) {
        List<TypeReference<?>> types = new ArrayList<>();
        types.add(new TypeReference<Utf8String>() {});
        return ((Utf8String) FunctionReturnDecoder.decode(hex, org.web3j.abi.Utils.convert(types))
                .get(0)).getValue();
    }


    /**
     * message: bytes[] // abi encoded {
     * uint32 childChainId,
     * uint32 rootChainId,
     * address childToken,
     * address receiveAddr,
     * bytes value
     * }
     */
    public static PosBridgeWithdrawExecMsg decodePosBridgeWithdrawExecMsg(String msgHex) {
        List<TypeReference<?>> types = new ArrayList<>();
        TypeReference<Uint32> type1 = new TypeReference<Uint32>() {
        };
        TypeReference<Uint32> type2 = new TypeReference<Uint32>() {
        };
        TypeReference<Address> type3 = new TypeReference<Address>() {
        };
        TypeReference<Address> type4 = new TypeReference<Address>() {
        };
        TypeReference<DynamicBytes> type5 = new TypeReference<DynamicBytes>() {
        };
        types.add(type1);
        types.add(type2);
        types.add(type3);
        types.add(type4);
        types.add(type5);
        List<Type> out = FunctionReturnDecoder.decode(msgHex, org.web3j.abi.Utils.convert(types));

        return PosBridgeWithdrawExecMsg.builder()
                .childChainId(((Uint32) out.get(0)).getValue().longValue())
                .rootChainId(((Uint32) out.get(1)).getValue().longValue())
                .childTokenAddr(((Address) out.get(2)).getValue())
                .receiveAddr(toUniAddress(((Address) out.get(3)).getValue()))
                .withdrawData((DynamicBytes) out.get(4))
                .build();
    }

    public static String makeTokenMapKey(long chainId, String token) {
        return makeTokenMapKey(Long.toHexString(chainId), token);
    }

    public static String makeTokenMapKey(String chainIdHex, String token) {
        return (chainIdHex + "_" + token.toLowerCase(Locale.ROOT));
    }

    public static boolean isUnichain(long chainId) {
        return (chainId == Wallet.getAddressPreFixByte());
    }

    public static String toUniAddress(String input) {
        String cleanPrefix = Numeric.cleanHexPrefix(input);
        if (cleanPrefix.length() == 42 && !cleanPrefix.startsWith(Wallet.getAddressPreFixString()))
            return input;
        return "0x" + Wallet.getAddressPreFixString() + cleanPrefix;
    }

    public static String cleanUniPrefix(String input) {
        String cleanPrefix = Numeric.cleanHexPrefix(input);
        if (cleanPrefix.length() == 42
                && cleanPrefix.startsWith(Wallet.getAddressPreFixString())
        ) {
            return "0x" + input.substring(4);
        }
        return input;
    }
}
