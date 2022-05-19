package org.unichain;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.junit.Assert;
import org.unichain.common.utils.AddressUtil;
import org.unichain.core.Wallet;

import java.util.Arrays;

@Slf4j(topic = "Test")
public class GenAddressTest {
    @org.junit.Test
    public void testGenAddrFromPubKey(){
            String tokenSym1 = "SSI";
            String tokenSym11 = "ssi";
            String tokenSym21= "ACB";
            String tokenSym22= "Acb";

            var addr1 = AddressUtil.genAssetAddrBySeed(tokenSym1);
            var addr11 = AddressUtil.genAssetAddrBySeed(tokenSym11);
            var addr21 = AddressUtil.genAssetAddrBySeed(tokenSym21);
            var addr22 = AddressUtil.genAssetAddrBySeed(tokenSym22);
            Assert.assertTrue("same address with same symbol", Wallet.addressValid(addr1) && Wallet.addressValid(addr11) && Arrays.equals(addr1, addr11));
            Assert.assertTrue("same address with same symbol", Wallet.addressValid(addr21) && Wallet.addressValid(addr22) && Arrays.equals(addr21, addr22));
            Assert.assertTrue("different symbols must gen different address", !Arrays.equals(addr1, addr22));
            System.out.println("addr1: " + Wallet.encode58Check(addr1));
            System.out.println("addr2: " + Wallet.encode58Check(addr21));
    }
}
