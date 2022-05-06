package org.unichain.common.utils;

import org.apache.commons.codec.binary.Hex;
import org.unichain.common.crypto.ECKey;
import org.unichain.core.Wallet;
import org.unichain.core.db.Manager;

public class AddressUtil {
    public static class WalletInfo{
        public WalletInfo(String addressHex, String addressBase58, String privateKeyHex) {
            this.addressHex = addressHex;
            this.addressBase58 = addressBase58;
            this.privateKeyHex = privateKeyHex;
        }
        public String addressHex;
        public String addressBase58;
        public String privateKeyHex;
    }
    public static WalletInfo generateAddress(Manager safetyCheck){
        int effort = 8;
        WalletInfo walletInfo = null;
        while (effort-- > 0){
            ECKey ecKey = new ECKey(Utils.getRandom());
            byte[] address = ecKey.getAddress();
            if(safetyCheck.getAccountStore().has(address))
            {
                continue;
            }
            else
            {
                byte[] priKey = ecKey.getPrivKeyBytes();
                String priKeyHex = Hex.encodeHexString(priKey);
                String addressBase58 = Wallet.encode58Check(address);
                String addressHex = ByteArray.toHexString(address);
                walletInfo =  new WalletInfo(addressHex, addressBase58, priKeyHex);
                break;
            }
        }
        return walletInfo;
    }
}
