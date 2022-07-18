package stest.unx.wallet.dailybuild.manual;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI;
import org.unx.api.WalletGrpc;
import org.unx.common.crypto.ECKey;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.Utils;
import org.unx.core.Wallet;
import org.unx.protos.Protocol;
import org.unx.protos.contract.SmartContractOuterClass;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.Parameter;
import stest.unx.wallet.common.client.WalletClient;
import stest.unx.wallet.common.client.utils.PublicMethed;

@Slf4j
public class ContractUrc1155 {

  private final String testKey002 =
      Configuration.getByPath("testng.conf").getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private final String fullnode =
      Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list").get(1);
  private Long maxFeeLimit =
      Configuration.getByPath("testng.conf").getLong("defaultParameter.maxFeeLimit");
  Optional<Protocol.TransactionInfo> infoById = null;
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] ownerAddressByte = ecKey2.getAddress();
  String ownerKey = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  String ownerAddressString = null;
  String holderAddressString = null;
  String noHolderAddress = null;
  String txid = null;
  byte[] urc1155AddressByte = null;
  /** constructor. */

  @BeforeSuite
  public void beforeSuite() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
  }

  /** constructor. */
  @BeforeClass(enabled = true)
  public void beforeClass() throws Exception {
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    deployUrc1155();

    deployHolder();
    deployNoHolder();
  }

  @Test(enabled = true, description = "Trigger Urc1155 balanceOf method")
  public void test01triggerUrc1155BalanceOfMethod() {
    int coinType = 3;
    List<Object> parameters = Arrays.asList(ownerAddressString, coinType);
    String data = PublicMethed.parametersString(parameters);

    logger.info("data:" + data);
    GrpcAPI.TransactionExtention transactionExtention =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOf(address,uint256)",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    String hexBalance = Hex.toHexString(transactionExtention.getConstantResult(0).toByteArray());
    long result = Long.parseLong(hexBalance, 16);
    Assert.assertEquals((long) Math.pow(10, 4), result);
  }

  @Test(enabled = true, description = "Trigger Urc1155 balanceOfBatch method")
  public void test02triggerUrc1155BalanceOfBatchMethod() {
    List<Object> address =
        Stream.of(
                ownerAddressString,
                ownerAddressString,
                ownerAddressString,
                ownerAddressString,
                ownerAddressString,
                ownerAddressString)
            .collect(Collectors.toList());
    List<Integer> coinType = Stream.of(0, 1, 2, 3, 4, 5).collect(Collectors.toList());
    List<Object> parameters = Arrays.asList(address, coinType);
    String data = PublicMethed.parametersString(parameters);
    logger.info("data:" + data);
    GrpcAPI.TransactionExtention transactionExtention1 =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOfBatch(address[],uint256[])",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    String hexBalance = Hex.toHexString(transactionExtention1.getConstantResult(0).toByteArray());
    logger.info("hexBalance:" + hexBalance);
    final long unxAmount = (long) Math.pow(10, 3);
    final long bttAmount = (long) Math.pow(10, 2);
    final long winAmount = (long) Math.pow(10, 5);
    final long ginzaAmount = (long) Math.pow(10, 4);
    final long apenftAmount = 1L;
    final long apenft1Amount = 1L;
    Assert.assertEquals(unxAmount, Long.parseLong(hexBalance.substring(128, 192), 16));
    Assert.assertEquals(bttAmount, Long.parseLong(hexBalance.substring(192, 256), 16));
    Assert.assertEquals(winAmount, Long.parseLong(hexBalance.substring(256, 320), 16));
    Assert.assertEquals(ginzaAmount, Long.parseLong(hexBalance.substring(320, 384), 16));
    Assert.assertEquals(apenftAmount, Long.parseLong(hexBalance.substring(384, 448), 16));
    Assert.assertEquals(apenft1Amount, Long.parseLong(hexBalance.substring(448, 512), 16));
  }

  @Test(enabled = true, description = "Trigger Urc1155  safeTransferFrom function")
  public void test03triggerUrc1155SafeTransferFromFunction() {
    ECKey ecKey3 = new ECKey(Utils.getRandom());
    byte[] contract003Address = ecKey3.getAddress();
    String sendAddress = WalletClient.encode58Check(contract003Address);
    logger.info(sendAddress);
    int coinType = 3;
    final int coinAmount = 2;
    List<Object> parameters1 = Arrays.asList(ownerAddressString, coinType);
    String data = PublicMethed.parametersString(parameters1);
    logger.info("data1:" + data);
    GrpcAPI.TransactionExtention transactionExtention =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOf(address,uint256)",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    String hexBalance = Hex.toHexString(transactionExtention.getConstantResult(0).toByteArray());
    long result = Long.parseLong(hexBalance, 16);
    Assert.assertEquals((long) Math.pow(10, 4), result);
    String bytes = "0000000000000000000000000000000000e6b58be8af95e5ad97e7aca6e4b8b2";
    List<Object> parameters =
        Arrays.asList(ownerAddressString, sendAddress, coinType, coinAmount, bytes);
    data = PublicMethed.parametersString(parameters);
    logger.info("data2:" + data);
    String txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "safeTransferFrom(address,address,uint256,uint256,bytes)",
            data,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    Optional<Protocol.Transaction> byId = PublicMethed.getTransactionById(txid, blockingStubFull);
    String s = ByteArray.toHexString(byId.get().getRawData().getContract(0).toByteArray());
    Assert.assertTrue(s.contains(bytes));

    List<Object> parameters3 = Arrays.asList(ownerAddressString, coinType);
    data = PublicMethed.parametersString(parameters3);
    logger.info("data3:" + data);
    GrpcAPI.TransactionExtention transactionExtention3 =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOf(address,uint256)",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    hexBalance = Hex.toHexString(transactionExtention3.getConstantResult(0).toByteArray());
    result = Long.parseLong(hexBalance, 16);
    Assert.assertEquals((long) Math.pow(10, 4) - coinAmount, result);

    parameters = Arrays.asList(sendAddress, coinType);
    data = PublicMethed.parametersString(parameters);

    logger.info("data2:" + data);
    transactionExtention =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOf(address,uint256)",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    hexBalance = Hex.toHexString(transactionExtention.getConstantResult(0).toByteArray());
    result = Long.parseLong(hexBalance, 16);
    Assert.assertEquals(coinAmount, result);
  }

  @Test(enabled = true, description = "trigger Urc1155 SafeBatchTransferFrom function")
  public void test04triggerUrc1155SafeBatchTransferFromFunction() {

    ECKey ecKey4 = new ECKey(Utils.getRandom());
    byte[] receiverAddress = ecKey4.getAddress();
    String sendAddress = WalletClient.encode58Check(receiverAddress);
    List<Object> coinType = Stream.of(0, 1, 5).collect(Collectors.toList());
    List<Object> coinAccount = Stream.of(50, 10, 1).collect(Collectors.toList());
    String bytes = "0000000000000000000000000000000000e6b58be8af95e5ad97e7aca6e4b8b2";
    List<Object> parameters =
        Arrays.asList(ownerAddressString, sendAddress, coinType, coinAccount, bytes);
    String input = PublicMethed.parametersString(parameters);
    logger.info("input:" + input);
    String txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)",
            input,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    Optional<Protocol.Transaction> byId = PublicMethed.getTransactionById(txid, blockingStubFull);
    String s = ByteArray.toHexString(byId.get().getRawData().getContract(0).toByteArray());
    Assert.assertTrue(s.contains(bytes));
    List<Object> address =
        Stream.of(
                ownerAddressString,
                ownerAddressString,
                ownerAddressString,
                ownerAddressString,
                ownerAddressString,
                ownerAddressString)
            .collect(Collectors.toList());
    List<Integer> coinType1 = Stream.of(0, 1, 2, 3, 4, 5).collect(Collectors.toList());
    List<Object> parameters1 = Arrays.asList(address, coinType1);
    String data = PublicMethed.parametersString(parameters1);

    logger.info("data2:" + data);
    GrpcAPI.TransactionExtention transactionExtention =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOfBatch(address[],uint256[])",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    String hexBalance = Hex.toHexString(transactionExtention.getConstantResult(0).toByteArray());
    logger.info("hexBalance:" + hexBalance);
    Assert.assertEquals(
        (long) Math.pow(10, 3) - 50, Long.parseLong(hexBalance.substring(128, 192), 16));
    Assert.assertEquals(
        (long) Math.pow(10, 2) - 10, Long.parseLong(hexBalance.substring(192, 256), 16));
    Assert.assertEquals((long) Math.pow(10, 5), Long.parseLong(hexBalance.substring(256, 320), 16));
    Assert.assertEquals(
        (long) Math.pow(10, 4) - 2, Long.parseLong(hexBalance.substring(320, 384), 16));
    Assert.assertEquals(1, Long.parseLong(hexBalance.substring(384, 448), 16));
    Assert.assertEquals(0, Long.parseLong(hexBalance.substring(448, 512), 16));

    address =
        Stream.of(
                sendAddress,
                sendAddress,
                ownerAddressString,
                ownerAddressString,
                ownerAddressString,
                sendAddress)
            .collect(Collectors.toList());
    coinType = Stream.of(0, 1, 2, 3, 4, 5).collect(Collectors.toList());
    parameters = Arrays.asList(address, coinType);
    data = PublicMethed.parametersString(parameters);

    logger.info("data2:" + data);
    transactionExtention =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOfBatch(address[],uint256[])",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    hexBalance = Hex.toHexString(transactionExtention.getConstantResult(0).toByteArray());
    Assert.assertEquals(50, Long.parseLong(hexBalance.substring(128, 192), 16));
    Assert.assertEquals(10, Long.parseLong(hexBalance.substring(192, 256), 16));
    Assert.assertEquals((long) Math.pow(10, 5), Long.parseLong(hexBalance.substring(256, 320), 16));
    Assert.assertEquals(
        (long) Math.pow(10, 4) - 2, Long.parseLong(hexBalance.substring(320, 384), 16));
    Assert.assertEquals(1, Long.parseLong(hexBalance.substring(384, 448), 16));
    Assert.assertEquals(1, Long.parseLong(hexBalance.substring(448, 512), 16));
  }

  @Test(enabled = true, description = "Urc1155Holder can receive urc1155")
  public void test05Urc1155HolderCanReceiveUrc1155() {
    List<Object> parameters = Arrays.asList(holderAddressString, true);
    String data = PublicMethed.parametersString(parameters);
    logger.info("data:" + data);
    txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "setApprovalForAll(address,bool)",
            data,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("setApprovalForAll_txid:" + txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    int coinType = 0;
    int coinAmount = 10;
    String bytes = "0000000000000000000000000000000000e6b58be8af95e5ad97e7aca6e4b8b2";
    parameters =
        Arrays.asList(ownerAddressString, holderAddressString, coinType, coinAmount, bytes);
    data = PublicMethed.parametersString(parameters);
    logger.info("data:" + data);
    txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "safeTransferFrom(address,address,uint256,uint256,bytes)",
            data,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("safeTransferFrom_txid:" + txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    Optional<Protocol.Transaction> byId = PublicMethed.getTransactionById(txid, blockingStubFull);
    String s = ByteArray.toHexString(byId.get().getRawData().getContract(0).toByteArray());
    Assert.assertTrue(s.contains(bytes));
    logger.info("infobyid1 : --- " + byId);

    logger.info("infobyid1 : --- " + infoById);

    parameters = Arrays.asList(holderAddressString, coinType);
    data = PublicMethed.parametersString(parameters);
    logger.info("data2:" + data);
    GrpcAPI.TransactionExtention transactionExtention =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOf(address,uint256)",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);

    String hexBalance = Hex.toHexString(transactionExtention.getConstantResult(0).toByteArray());
    long result = Long.parseLong(hexBalance, 16);
    Assert.assertEquals(coinAmount, result);
  }

  @Test(enabled = true, description = "Urc1155Holder can receive urc1155[]")
  public void test06Urc1155HolderCanReceiveUrc1155_01() {

    List<Object> parameters = Arrays.asList(holderAddressString, true);
    String data = PublicMethed.parametersString(parameters);
    logger.info("data:" + data);
    txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "setApprovalForAll(address,bool)",
            data,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("setApprovalForAll_txid:" + txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    int unxAmount = 50;
    int bttAmount = 30;
    int winAmount = 25;
    int ginzaAmount = 10;
    int apenftAmount = 1;
    List<Object> coinType = Stream.of(0, 1, 2, 3, 4).collect(Collectors.toList());
    List<Object> coinAmount =
        Stream.of(unxAmount, bttAmount, winAmount, ginzaAmount, apenftAmount)
            .collect(Collectors.toList());
    String bytes = "0000000000000000000000000000000000e6b58be8af95e5ad97e7aca6e4b8b2";
    parameters =
        Arrays.asList(ownerAddressString, holderAddressString, coinType, coinAmount, bytes);
    data = PublicMethed.parametersString(parameters);
    logger.info("data1:" + data);
    txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)",
            data,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("safeBatchTransferFrom_txid:" + txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid1 : --- " + infoById);
    Optional<Protocol.Transaction> byId = PublicMethed.getTransactionById(txid, blockingStubFull);
    String s = ByteArray.toHexString(byId.get().getRawData().getContract(0).toByteArray());
    Assert.assertTrue(s.contains(bytes));
    List<Object> address =
        Stream.of(
                holderAddressString,
                holderAddressString,
                holderAddressString,
                holderAddressString,
                holderAddressString,
                holderAddressString)
            .collect(Collectors.toList());
    coinType = Stream.of(0, 1, 2, 3, 4, 5).collect(Collectors.toList());
    parameters = Arrays.asList(address, coinType);
    data = PublicMethed.parametersString(parameters);
    logger.info("data2:" + data);
    GrpcAPI.TransactionExtention transactionExtention =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOfBatch(address[],uint256[])",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    String hexBalance = Hex.toHexString(transactionExtention.getConstantResult(0).toByteArray());
    Assert.assertEquals(unxAmount + 10, Long.parseLong(hexBalance.substring(128, 192), 16));
    Assert.assertEquals(bttAmount, Long.parseLong(hexBalance.substring(192, 256), 16));
    Assert.assertEquals(winAmount, Long.parseLong(hexBalance.substring(256, 320), 16));
    Assert.assertEquals(ginzaAmount, Long.parseLong(hexBalance.substring(320, 384), 16));
    Assert.assertEquals(apenftAmount, Long.parseLong(hexBalance.substring(384, 448), 16));
    Assert.assertEquals(0, Long.parseLong(hexBalance.substring(448, 512), 16));
  }

  @Test(enabled = true, description = "Non-urc1155Holder can not receive urc1155")
  public void test07NonUrc1155HolderCanNotReceiveUrc1155() {
    List<Object> parameters = Arrays.asList(noHolderAddress, true);
    String data = PublicMethed.parametersString(parameters);
    logger.info("data:" + data);
    txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "setApprovalForAll(address,bool)",
            data,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("setApprovalForAll_txid:" + txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid : --- " + infoById);
    String bytes = "0000000000000000000000000000000000e6b58be8af95e5ad97e7aca6e4b8b2";
    List<Object> parameters1 = Arrays.asList(ownerAddressString, noHolderAddress, 1, 1, bytes);
    String data1 = PublicMethed.parametersString(parameters1);
    logger.info("data:" + data1);
    txid =
        PublicMethed.triggerContract(
            urc1155AddressByte,
            "safeTransferFrom(address,address,uint256,uint256,bytes)",
            data1,
            false,
            0,
            maxFeeLimit,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("safeTransferFrom_txid:" + txid);
    infoById = PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    logger.info("infobyid1 : --- " + infoById);
    parameters = Arrays.asList(noHolderAddress, 1);
    data = PublicMethed.parametersString(parameters);
    logger.info("data2:" + data);
    GrpcAPI.TransactionExtention transactionExtention1 =
        PublicMethed.triggerConstantContractForExtention(
            urc1155AddressByte,
            "balanceOf(address,uint256)",
            data,
            false,
            0,
            0,
            "0",
            0,
            ownerAddressByte,
            ownerKey,
            blockingStubFull);

    String hexBalance = Hex.toHexString(transactionExtention1.getConstantResult(0).toByteArray());
    long result = Long.parseLong(hexBalance, 16);
    Assert.assertEquals(0, result);
  }

  /** constructor. */
  public void deployUrc1155() throws Exception {
    Assert.assertTrue(
        PublicMethed.sendcoin(
            ownerAddressByte, 500000000000L, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String contractName = "UnxCoins";
    String filePath = "./src/test/resources/soliditycode/contractUrc1155.sol";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    int deploySuccessFlag = 1;
    Integer retryTimes = 5;

    while (retryTimes-- > 0 && deploySuccessFlag != 0) {
      String txid =
          PublicMethed.deployContractAndGetTransactionInfoById(
              contractName,
              abi,
              code,
              "",
              maxFeeLimit,
              0L,
              100,
              null,
              ownerKey,
              ownerAddressByte,
              blockingStubFull);

      PublicMethed.waitProduceNextBlock(blockingStubFull);

      logger.info("Deploy IssueCoins txid:" + txid);
      Optional<Protocol.TransactionInfo> infoById =
          PublicMethed.getTransactionInfoById(txid, blockingStubFull);
      com.google.protobuf.ByteString contractAddress = infoById.get().getContractAddress();
      SmartContractOuterClass.SmartContract smartContract =
          PublicMethed.getContract(contractAddress.toByteArray(), blockingStubFull);
      logger.info("smartContract:" + smartContract);
      urc1155AddressByte = contractAddress.toByteArray();
      ownerAddressString = WalletClient.encode58Check(ownerAddressByte);
      logger.info("urc1155AddressByte:" + urc1155AddressByte);
      logger.info("ownerAddressString:" + ownerAddressString);
      deploySuccessFlag = infoById.get().getResult().getNumber();
    }

    Assert.assertEquals(deploySuccessFlag, 0);
  }

  /** constructor. */
  public void deployHolder() throws Exception {
    String contractName = "MyContractCanReceiver";
    String filePath = "./src/test/resources/soliditycode/contractUrc1155.sol";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid =
        PublicMethed.deployContractAndGetTransactionInfoById(
            contractName,
            abi,
            code,
            "",
            maxFeeLimit,
            0L,
            100,
            null,
            ownerKey,
            ownerAddressByte,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    logger.info("Deploy IssueCoins txid:" + txid);
    Optional<Protocol.TransactionInfo> infoById =
        PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    com.google.protobuf.ByteString contractAddress = infoById.get().getContractAddress();
    SmartContractOuterClass.SmartContract smartContract =
        PublicMethed.getContract(contractAddress.toByteArray(), blockingStubFull);

    holderAddressString = WalletClient.encode58Check(contractAddress.toByteArray());

    logger.info("HolderAddress:" + holderAddressString);
    Assert.assertTrue(smartContract.getAbi() != null);
    Assert.assertEquals(infoById.get().getResult().getNumber(), 0);
  }

  /** constructor. */
  public void deployNoHolder() throws Exception {
    String contractName = "MyContractCanNotReceiver";
    String filePath = "./src/test/resources/soliditycode/contractUrc1155.sol";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();

    String txid =
        PublicMethed.deployContractAndGetTransactionInfoById(
            contractName,
            abi,
            code,
            "",
            maxFeeLimit,
            0L,
            100,
            null,
            ownerKey,
            ownerAddressByte,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    logger.info("Deploy IssueCoins txid:" + txid);
    Optional<Protocol.TransactionInfo> infoById =
        PublicMethed.getTransactionInfoById(txid, blockingStubFull);
    com.google.protobuf.ByteString contractAddress = infoById.get().getContractAddress();
    SmartContractOuterClass.SmartContract smartContract =
        PublicMethed.getContract(contractAddress.toByteArray(), blockingStubFull);

    noHolderAddress = WalletClient.encode58Check(contractAddress.toByteArray());
    logger.info("NoHolderAddress:" + noHolderAddress);
    Assert.assertTrue(smartContract.getAbi() != null);
    Assert.assertEquals(infoById.get().getResult().getNumber(), 0);
  }

  /** constructor. */
  @AfterClass
  public void shutdown() throws InterruptedException {
    PublicMethed.freedResource(fromAddress, ownerKey, ownerAddressByte, blockingStubFull);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
