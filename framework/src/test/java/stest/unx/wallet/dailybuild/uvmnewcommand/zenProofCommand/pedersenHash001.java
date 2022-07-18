package stest.unx.wallet.dailybuild.uvmnewcommand.zenProofCommand;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.unx.api.WalletGrpc;
import org.unx.api.WalletSolidityGrpc;
import org.unx.common.crypto.ECKey;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.Utils;
import org.unx.core.Wallet;
import org.unx.protos.Protocol.TransactionInfo;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.Parameter;
import stest.unx.wallet.common.client.utils.PublicMethed;

@Slf4j
public class pedersenHash001 {

  private final String testNetAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key2");
  private final byte[] testNetAccountAddress = PublicMethed.getFinalAddress(testNetAccountKey);
  byte[] contractAddress = null;
  String txid;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private Long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private ManagedChannel channelFull1 = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);

  @BeforeSuite
  public void beforeSuite() {
    Wallet wallet = new Wallet();
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
  }

  /**
   * constructor.
   */

  @BeforeClass(enabled = true)
  public void beforeClass() {
    PublicMethed.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1).usePlaintext(true).build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);
    txid = PublicMethed
        .sendcoinGetTransactionId(contractExcAddress, 10000000000L, testNetAccountAddress,
            testNetAccountKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "src/test/resources/soliditycode/pedersenHash001.sol";
    String contractName = "pedersenHashTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress = PublicMethed
        .deployContract(contractName, abi, code, "", maxFeeLimit, 0L, 100, null, contractExcKey,
            contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
  }

  @Test(enabled = true, description = "data is empty")
  public void test01DataIsEmpty() {
    String method = "test1()";
    txid = PublicMethed
        .triggerContract(contractAddress, method, "", false, 0, maxFeeLimit, contractExcAddress,
            contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    Assert.assertEquals("SUCESS", infoById.get().getResult().toString());
    byte[] result = infoById.get().getContractResult(0).toByteArray();
    String boolResult = ByteArray.toHexString(ByteArray.subArray(result, 0, 32));
    System.out.println("boolResult: " + boolResult);
    Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000000",
        boolResult);

  }

  @Test(enabled = true, description = "data length limit")
  public void test02DataLengthLimit() {
    String method = "test2(bytes)";
    // length:64
    String argsStr1 = "\"0000000000000000000000000000000000000000000000000000000000000001"
        + "0000000000000000000000000000000000000000000000000000000000000002\"";
    Optional<TransactionInfo> infoById = null;
    txid = PublicMethed.triggerContract(contractAddress, method, argsStr1, false, 0, maxFeeLimit,
        contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    Assert.assertEquals("SUCESS", infoById.get().getResult().toString());
    int boolResult = ByteArray.toInt(infoById.get().getContractResult(0).toByteArray());
    Assert.assertFalse(Boolean.valueOf(String.valueOf(boolResult)));
    Assert.assertTrue(maxFeeLimit > infoById.get().getFee());

    // length:128
    String argsStr2 = "\"0000000000000000000000000000000000000000000000000000000000000001"
        + "0000000000000000000000000000000000000000000000000000000000000001"
        + "0000000000000000000000000000000000000000000000000000000000000002"
        + "0000000000000000000000000000000000000000000000000000000000000002\"";
    txid = PublicMethed.triggerContract(contractAddress, method, argsStr2, false, 0, maxFeeLimit,
        contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    Assert.assertEquals("0000000000000000000000000000000000000000000000000000000000000001"
            + "0000000000000000000000000000000000000000000000000000000000000040"
            + "0000000000000000000000000000000000000000000000000000000000000020"
            + "7d6b910840eb7b47f76492aca4a3344888b8fa5aab77a49e9445cda718d75040",
        ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray()));
  }

  @Test(enabled = true, description = "normal")
  public void test02Normal() {
    String method = "test3(uint32,bytes32,bytes32)";
    String argsStr1 = "0000000000000000000000000000000000000000000000000000000000000001"
        + "0000000000000000000000000000000000000000000000000000000000000001"
        + "0000000000000000000000000000000000000000000000000000000000000002";
    Optional<TransactionInfo> infoById = null;
    txid = PublicMethed.triggerContract(contractAddress, method, argsStr1, true, 0, maxFeeLimit,
        contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertEquals(0, infoById.get().getResultValue());
    Assert.assertEquals("7d6b910840eb7b47f76492aca4a3344888b8fa5aab77a49e9445cda718d75040",
        ByteArray.toHexString(infoById.get().getContractResult(0).toByteArray()));
  }

  @AfterClass
  public void shutdown() throws InterruptedException {
    long balance = PublicMethed.queryAccount(contractExcKey, blockingStubFull).getBalance();
    PublicMethed
        .sendcoin(testNetAccountAddress, balance - 1000000, contractExcAddress, contractExcKey,
            blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelFull1 != null) {
      channelFull1.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

}
