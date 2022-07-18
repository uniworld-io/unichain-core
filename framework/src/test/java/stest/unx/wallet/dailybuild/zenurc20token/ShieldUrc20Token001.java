package stest.unx.wallet.dailybuild.zenurc20token;

import io.grpc.ManagedChannelBuilder;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI.TransactionExtention;
import org.unx.api.WalletGrpc;
import org.unx.protos.Protocol.TransactionInfo;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.PublicMethed;
import stest.unx.wallet.common.client.utils.ZenUrc20Base;

@Slf4j
public class ShieldUrc20Token001 extends ZenUrc20Base {

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  @Test(enabled = true, description = "Check shield contract deploy success")
  public void test01checkShieldContractDeploySuccess() {
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(deployShieldUrc20Txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    infoById = PublicMethed
        .getTransactionInfoById(deployShieldTxid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);

    //scalingFactor()
  }

  @Test(enabled = true, description = "View scaling factor test")
  public void test02ViewScalingFactor() {
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        "scalingFactor()", "", false,
        0, maxFeeLimit, zenUrc20TokenOwnerAddress, zenUrc20TokenOwnerKey, blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info(txid);
    logger.info(Integer.toString(infoById.get().getResultValue()));

    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(shieldAddressByte, "scalingFactor()",
            "", false, 0, 0, "0", 0,
            zenUrc20TokenOwnerAddress, zenUrc20TokenOwnerKey, blockingStubFull);

    logger.info("transactionExtention:" + transactionExtention);
    String scalingFactor = PublicMethed
        .bytes32ToString(transactionExtention.getConstantResult(0).toByteArray());
    Assert.assertEquals("00000000000000000000000000000001",
        scalingFactor);

  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


