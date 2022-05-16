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
import org.web3j.utils.Numeric;

import java.util.*;

@Slf4j(topic = "PosBridge")
public class PosBridgeUtil {

    public static final int ASSET_TYPE_NATIVE = 1;//native
    public static final int ASSET_TYPE_TOKEN = 2;//erc20
    public static final int ASSET_TYPE_NFT = 3;//erc721


    private static String BLIND_URI_HEX = "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000020687474703a2f2f6a7573745f6175746f5f67656e2e6f72672f78782e6a736f6e";

    @Builder
    @ToString
    public static class PosBridgeDepositExecMsg {
        public long rootChainId;
        public String rootTokenAddr;
        public long childChainId;
        public String receiveAddr;
        public DynamicBytes depositData;
        public String extHex;
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

//        Uint256 value = abiDecodeToUint256((DynamicBytes) out.get(4));

        return PosBridgeDepositExecMsg.builder()
                .rootChainId(((Uint32) out.get(0)).getValue().longValue())
                .childChainId(((Uint32) out.get(1)).getValue().longValue())
                .rootTokenAddr(((Address) out.get(2)).getValue())
                .receiveAddr(toUniAddress(((Address) out.get(3)).getValue()))
                .depositData((DynamicBytes) out.get(4))
                .extHex(BLIND_URI_HEX) //@todo add uri msg in source msg
                .build();
    }

    public static Uint256 abiDecodeToUint256(DynamicBytes bytes) {
        return abiDecodeToUint256(Hex.encodeHexString((bytes).getValue()));
    }

    public static Uint256 abiDecodeToUint256(String hex) {
        List<TypeReference<?>> valueTypes = new ArrayList<>();
        valueTypes.add(new TypeReference<Uint256>() {
        });
        return (Uint256) FunctionReturnDecoder.decode(
                        hex,
                        org.web3j.abi.Utils.convert(valueTypes))
                .get(0);
    }


    public static String abiDecodeFromToString(String hex) {
        List<TypeReference<?>> types = new ArrayList<>();
        types.add(new TypeReference<Utf8String>() {
        });
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
