package stest.unx.wallet.fulltest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI.AccountResourceMessage;
import org.unx.api.WalletGrpc;
import org.unx.common.crypto.ECKey;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.Utils;
import org.unx.protos.Protocol.TransactionInfo;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.PublicMethed;

@Slf4j
public class UnwDice {

  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  byte[] contractAddress;
  Long maxFeeLimit = 1000000000L;
  Optional<TransactionInfo> infoById = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contract008Address = ecKey1.getAddress();
  String contract008Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ArrayList<String> txidList = new ArrayList<String>();
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);


  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(contract008Key);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    PublicMethed.printAddress(testKey002);
    AccountResourceMessage accountResource = PublicMethed.getAccountResource(contract008Address,
        blockingStubFull);
  }

  @Test(enabled = true, threadPoolSize = 30, invocationCount = 30)
  public void unwDice() {
    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] unxDiceAddress = ecKey1.getAddress();
    String unxDiceKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    PublicMethed
        .sendcoin(unxDiceAddress, 100000000000L, fromAddress, testKey002, blockingStubFull);
    String contractName = "UnwDice";
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_UnwDice_unwDice");
    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_UnwDice_unwDice");
    byte[] contractAddress = PublicMethed.deployContract(contractName, abi, code, "",
        maxFeeLimit, 1000000000L, 100, null, unxDiceKey, unxDiceAddress, blockingStubFull);
    SmartContract smartContract = PublicMethed.getContract(contractAddress, blockingStubFull);
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    Assert.assertTrue(smartContract.getAbi() != null);

    String txid;

    for (Integer i = 0; i < 100; i++) {
      String initParmes = "\"" + "10" + "\"";
      txid = PublicMethed.triggerContract(contractAddress,
          "rollDice(uint256)", initParmes, false,
          1000000, maxFeeLimit, unxDiceAddress, unxDiceKey, blockingStubFull);
      logger.info(txid);
      txidList.add(txid);

      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }

  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Integer successTimes = 0;
    Integer failedTimes = 0;
    Integer totalTimes = 0;
    for (String txid1 : txidList) {
      totalTimes++;
      infoById = PublicMethed.getTransactionInfoById(txid1, blockingStubFull);
      if (infoById.get().getBlockNumber() > 3523732) {
        logger.info("blocknum is " + infoById.get().getBlockNumber());
        successTimes++;
      } else {
        failedTimes++;
      }
    }
    logger.info("Total times is " + totalTimes.toString());
    logger.info("success times is " + successTimes.toString());
    logger.info("failed times is " + failedTimes.toString());
    logger.info("success percent is " + successTimes / totalTimes);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


