package org.unichain;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.unichain.core.capsule.utils.TransactionUtil;

@Slf4j(topic = "Test")
public class AssetNameTest {
    @org.junit.Test
    public void testAssetName(){
        String tokenName1 = "Ada";
        String tokenName2 = "ada hello $^&";
        String tokenName3= "ada peg token ada";
        String tokenName4= "ada peg token ada peg token ada peg tokenada peg tokenada peg tokenada peg tokenada peg tokenada peg tokenada peg tokenada peg token";

        Assert.assertTrue("token name 1 must be valid", TransactionUtil.validTokenName(tokenName1));
        Assert.assertTrue("token name 2 must be invalid", !TransactionUtil.validTokenName(tokenName2));
        Assert.assertTrue("token name 3 must be valid", TransactionUtil.validTokenName(tokenName3));
        Assert.assertTrue("token name 1 must be invalid", !TransactionUtil.validTokenName(tokenName4));
    }
}
