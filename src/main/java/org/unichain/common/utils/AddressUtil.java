package org.unichain.common.utils;

import org.apache.commons.codec.binary.Hex;
import org.unichain.common.crypto.ECKey;
import org.unichain.core.Wallet;

import java.nio.charset.StandardCharsets;

public class AddressUtil {
    public static class WalletInfo{
        public WalletInfo(String addressHex, String addressBase58, String privateKeyHex, byte[] address) {
            this.addressHex = addressHex;
            this.addressBase58 = addressBase58;
            this.privateKeyHex = privateKeyHex;
            this.address = address;
        }
        public String addressHex;
        public String addressBase58;
        public String privateKeyHex;
        public byte[] address;
    }

    public static WalletInfo generateWallet(){
        ECKey ecKey = new ECKey(Utils.getRandom());
        byte[] address = ecKey.getAddress();
        byte[] priKey = ecKey.getPrivKeyBytes();
        String priKeyHex = Hex.encodeHexString(priKey);
        String addressBase58 = Wallet.encode58Check(address);
        String addressHex = ByteArray.toHexString(address);
        return new WalletInfo(addressHex, addressBase58, priKeyHex, address);
    }

    public static byte[] generateRandomAddress(){
        return (new ECKey(Utils.getRandom())).getAddress();
    }

    public static byte[] genAssetAddrBySeed(String seed){
        return ECKey.computeAddress(seed.toUpperCase().getBytes(StandardCharsets.UTF_8));
    }
}
