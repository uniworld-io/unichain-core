package stest.unx.wallet.dailybuild.account;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI.AccountResourceMessage;
import org.unx.api.WalletGrpc;
import org.unx.common.crypto.ECKey;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.Utils;
import org.unx.protos.Protocol.Account;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.PublicMethed;

@Slf4j
public class WalletTestAccount012 {
  private static final long sendAmount = 10000000000L;
  private static final long frozenAmountForUnwPower = 3456789L;
  private static final long frozenAmountForNet = 7000000L;
  private final String foundationKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] foundationAddress = PublicMethed.getFinalAddress(foundationKey);

  private final String witnessKey = Configuration.getByPath("testng.conf")
      .getString("witness.key1");
  private final byte[] witnessAddress = PublicMethed.getFinalAddress(witnessKey);

  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] frozenAddress = ecKey1.getAddress();
  String frozenKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(frozenKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

  }

  @Test(enabled = true, description = "Freeze balance to get unx power")
  public void test01FreezeBalancegetUnwPower() {


    final Long beforeFrozenTime = System.currentTimeMillis();
    Assert.assertTrue(PublicMethed.sendcoin(frozenAddress, sendAmount,
        foundationAddress, foundationKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);


    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    final Long beforeTotalUnwPowerWeight = accountResource.getTotalUnwPowerWeight();
    final Long beforeUnwPowerLimit = accountResource.getUnwPowerLimit();


    Assert.assertTrue(PublicMethed.freezeBalancegetUnwPower(frozenAddress,frozenAmountForUnwPower,
        0,2,null,frozenKey,blockingStubFull));
    Assert.assertTrue(PublicMethed.freezeBalancegetUnwPower(frozenAddress,frozenAmountForNet,
        0,0,null,frozenKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long afterFrozenTime = System.currentTimeMillis();
    Account account = PublicMethed.queryAccount(frozenAddress,blockingStubFull);
    Assert.assertEquals(account.getUnwPower().getFrozenBalance(),frozenAmountForUnwPower);
    Assert.assertTrue(account.getUnwPower().getExpireTime() > beforeFrozenTime
        && account.getUnwPower().getExpireTime() < afterFrozenTime);

    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    Long afterTotalUnwPowerWeight = accountResource.getTotalUnwPowerWeight();
    Long afterUnwPowerLimit = accountResource.getUnwPowerLimit();
    Long afterUnwPowerUsed = accountResource.getUnwPowerUsed();
    Assert.assertEquals(afterTotalUnwPowerWeight - beforeTotalUnwPowerWeight,
        frozenAmountForUnwPower / 1000000L);

    Assert.assertEquals(afterUnwPowerLimit - beforeUnwPowerLimit,
        frozenAmountForUnwPower / 1000000L);



    Assert.assertTrue(PublicMethed.freezeBalancegetUnwPower(frozenAddress,
        6000000 - frozenAmountForUnwPower,
        0,2,null,frozenKey,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    afterUnwPowerLimit = accountResource.getUnwPowerLimit();

    Assert.assertEquals(afterUnwPowerLimit - beforeUnwPowerLimit,
        6);


  }


  @Test(enabled = true,description = "Vote witness by unx power")
  public void test02VotePowerOnlyComeFromUnwPower() {
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    final Long beforeUnwPowerUsed = accountResource.getUnwPowerUsed();


    HashMap<byte[],Long> witnessMap = new HashMap<>();
    witnessMap.put(witnessAddress,frozenAmountForNet / 1000000L);
    Assert.assertFalse(PublicMethed.voteWitness(frozenAddress,frozenKey,witnessMap,
        blockingStubFull));
    witnessMap.put(witnessAddress,frozenAmountForUnwPower / 1000000L);
    Assert.assertTrue(PublicMethed.voteWitness(frozenAddress,frozenKey,witnessMap,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    Long afterUnwPowerUsed = accountResource.getUnwPowerUsed();
    Assert.assertEquals(afterUnwPowerUsed - beforeUnwPowerUsed,
        frozenAmountForUnwPower / 1000000L);

    final Long secondBeforeUnwPowerUsed = afterUnwPowerUsed;
    witnessMap.put(witnessAddress,(frozenAmountForUnwPower / 1000000L) - 1);
    Assert.assertTrue(PublicMethed.voteWitness(frozenAddress,frozenKey,witnessMap,
        blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    afterUnwPowerUsed = accountResource.getUnwPowerUsed();
    Assert.assertEquals(secondBeforeUnwPowerUsed - afterUnwPowerUsed,
        1);


  }

  @Test(enabled = true,description = "Unx power is not allow to others")
  public void test03UnwPowerIsNotAllowToOthers() {
    Assert.assertFalse(PublicMethed.freezeBalancegetUnwPower(frozenAddress,
        frozenAmountForUnwPower, 0,2,
        ByteString.copyFrom(foundationAddress),frozenKey,blockingStubFull));
  }


  @Test(enabled = true,description = "Unfreeze balance for unx power")
  public void test04UnfreezeBalanceForUnwPower() {
    AccountResourceMessage accountResource = PublicMethed
        .getAccountResource(foundationAddress, blockingStubFull);
    final Long beforeTotalUnwPowerWeight = accountResource.getTotalUnwPowerWeight();


    Assert.assertTrue(PublicMethed.unFreezeBalance(frozenAddress,frozenKey,2,
        null,blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    accountResource = PublicMethed
        .getAccountResource(frozenAddress, blockingStubFull);
    Long afterTotalUnwPowerWeight = accountResource.getTotalUnwPowerWeight();
    Assert.assertEquals(beforeTotalUnwPowerWeight - afterTotalUnwPowerWeight,
        6);

    Assert.assertEquals(accountResource.getUnwPowerLimit(),0L);
    Assert.assertEquals(accountResource.getUnwPowerUsed(),0L);

    Account account = PublicMethed.queryAccount(frozenAddress,blockingStubFull);
    Assert.assertEquals(account.getUnwPower().getFrozenBalance(),0);


  }
  

  /**
   * constructor.
   */

  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
    PublicMethed.unFreezeBalance(frozenAddress, frozenKey, 2, null,
        blockingStubFull);
    PublicMethed.unFreezeBalance(frozenAddress, frozenKey, 0, null,
        blockingStubFull);
    PublicMethed.freedResource(frozenAddress, frozenKey, foundationAddress, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


