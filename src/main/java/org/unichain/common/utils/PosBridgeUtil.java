package org.unichain.common.utils;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.crypto.Hash;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.utils.RLPList;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j(topic = "PosBridge")
public class PosBridgeUtil {

    @Builder
    public static class PosBridgeDepositExecMsg {
        public long rootChainId;
        public String rootTokenAddr; //0x..
        public long childChainId;
        public String childTokenAddr; //0x..
        public String depositAddr; //0x..
        public String receiveAddr; //0x
        public long data;
        public long assetType;
    }

    @Builder
    public static class PosBridgeWithdrawExecMsg {
        public long childChainId;
        public String childTokenAddr; //0x..
        public long rootChainId;
        public String rootTokenAddr; //0x..
        public String withdrawAddr; //0x..
        public String receiveAddr; //0x
        public long data;
        public long assetType;
    }

    /**
     * @param msg hex string of abi encode event
     * @param signatures array of hex string signed by validators
     * @param config consensus config
     */
    public static void validateSignatures(final String msg, final String[] signatures, final PosBridgeConfigCapsule config){
        try{
            final String digest = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(msg)));
            final Set<String> unduplicatedSignatures = Arrays.stream(signatures).collect(Collectors.toSet());

            //@TODO: set from config, confirm format hex: 0x...
            final Set<String> validators = new HashSet<>();

            int countVerify = 0;
            for(String signature: unduplicatedSignatures){
                String signer = SignExt.recoverAddress(digest, signature);//format hex: 0x...
                if(validators.contains(signer))
                    countVerify++;
            }
            var rate = ((double)countVerify)/validators.size();
            Assert.isTrue(countVerify >= config.getMinValidator(), "LESS_THAN_MIN_VALIDATOR");
            Assert.isTrue(rate >= config.getConsensusRate(), "LESS_THAN_CONSENSUS_RATE");
        }catch (Exception e){
            logger.error("validate signature failed -->", e);
            throw e;
        }
    }

    /**
     * @param msg msg that signed by validators
     * @param signatures rlp encoded signatures list
     * @param config consensus config
     */
    public static void validateSignatures(final byte[] msg, final byte[] signatures, final PosBridgeConfigCapsule config) throws Exception{
        try{
            var whitelist = config.getValidators();
            var signedValidators = new HashMap<String, String>();
            for(var rlpItem : (RLPList)RlpDecoder.decode(signatures).getValues().get(0)){
                var sig = ((RlpString)rlpItem).getBytes();
                var hash = Hash.sha3(msg);
                var signedAddr = Hex.encodeHexString(ECKey.signatureToAddress(hash, sig));
                //make sure whitelist map is hex address without prefix 0x
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
     *   rootChainId: 0x...,
     *      rootTokenAddr: 0x...,
     *      childChainId: 0x...,
     *      childTokenAddr: 0x...,
     *      depositAddr:0x...
     *      receiveAddr: 0x...,
     *      data: 1000L //token amount or id
     *      type: 1 //1: native 2: urc20, 3: nft
     */
    public static PosBridgeDepositExecMsg decodePosBridgeDepositExecMsg(byte[] msg){
        var msgItems = ((RlpList) RlpDecoder.decode(msg).getValues().get(0)).getValues();
        return  PosBridgeDepositExecMsg.builder()
                .rootChainId((new BigInteger(((RlpString)msgItems.get(0)).getBytes())).longValue())
                .rootTokenAddr(Hex.encodeHexString(((RlpString)msgItems.get(1)).getBytes()))
                .childChainId((new BigInteger(((RlpString)msgItems.get(2)).getBytes())).longValue())
                .childTokenAddr(Hex.encodeHexString(((RlpString)msgItems.get(3)).getBytes()))
                .depositAddr(Hex.encodeHexString(((RlpString)msgItems.get(4)).getBytes()))
                .receiveAddr(Hex.encodeHexString(((RlpString)msgItems.get(5)).getBytes()))
                .data((new BigInteger(((RlpString)msgItems.get(6)).getBytes())).longValue())
                .assetType((new BigInteger(((RlpString)msgItems.get(7)).getBytes())).longValue())
                .build();
    }

    /**
     * childChainId: 0x...
     *      childTokenAddr: 0x...
     *      rootChainId: 0x...
     *      rootTokenAddr: 0x...
     *      withdrawAddr: 0x...
     *      receiveAddr: 0x...
     *      data: 1000
     *      type: 1 //1: native 2: urc20, 3: nft
     */
    public static PosBridgeWithdrawExecMsg decodePosBridgeWithdrawExecMsg(byte[] msg){
        var rplItems = ((RlpList) RlpDecoder.decode(msg).getValues().get(0)).getValues();
        return  PosBridgeWithdrawExecMsg.builder()
                .childChainId((new BigInteger(((RlpString)rplItems.get(0)).getBytes())).longValue())
                .childTokenAddr(Hex.encodeHexString(((RlpString)rplItems.get(1)).getBytes()))
                .rootChainId((new BigInteger(((RlpString)rplItems.get(2)).getBytes())).longValue())
                .rootTokenAddr(Hex.encodeHexString(((RlpString)rplItems.get(3)).getBytes()))
                .withdrawAddr(Hex.encodeHexString(((RlpString)rplItems.get(4)).getBytes()))
                .receiveAddr(Hex.encodeHexString(((RlpString)rplItems.get(5)).getBytes()))
                .data((new BigInteger(((RlpString)rplItems.get(6)).getBytes())).longValue())
                .assetType((new BigInteger(((RlpString)rplItems.get(7)).getBytes())).longValue())
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
