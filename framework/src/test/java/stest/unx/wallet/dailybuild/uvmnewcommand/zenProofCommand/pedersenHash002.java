package stest.unx.wallet.dailybuild.uvmnewcommand.zenProofCommand;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.netty.util.internal.StringUtil;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.api.GrpcAPI.EmptyMessage;
import org.unx.api.GrpcAPI.Note;
import org.unx.api.GrpcAPI.TransactionExtention;
import org.unx.api.WalletGrpc;
import org.unx.api.WalletSolidityGrpc;
import org.unx.common.crypto.ECKey;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.ByteUtil;
import org.unx.common.utils.Commons;
import org.unx.common.utils.Utils;
import org.unx.core.Wallet;
import org.unx.core.exception.ZksnarkException;
import org.unx.core.zen.address.DiversifierT;
import org.unx.protos.Protocol.TransactionInfo;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.Base58;
import stest.unx.wallet.common.client.utils.HttpMethed;
import stest.unx.wallet.common.client.utils.PublicMethed;
import stest.unx.wallet.common.client.utils.ShieldedAddressInfo;
import stest.unx.wallet.common.client.Parameter;

@Slf4j
public class pedersenHash002 {

  public final String foundationAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  public final byte[] foundationAccountAddress = PublicMethed.getFinalAddress(foundationAccountKey);
  public static final String zenUrc20TokenOwnerKey = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.zenUrc20TokenOwnerKey");
  public static final byte[] zenUrc20TokenOwnerAddress = PublicMethed
      .getFinalAddress(zenUrc20TokenOwnerKey);
  public static final String zenUrc20TokenOwnerAddressString = PublicMethed
      .getAddressString(zenUrc20TokenOwnerKey);
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] contractExcAddress = ecKey1.getAddress();
  String contractExcKey = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  public ManagedChannel channelFull = null;
  public WalletGrpc.WalletBlockingStub blockingStubFull = null;
  public ManagedChannel channelSolidity = null;
  public WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);

  public static long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  public ByteString contractAddressByteString;
  public static byte[] contractAddressByte;
  public static String contractAddress;
  public static ByteString shieldAddressByteString;
  public static byte[] shieldAddressByte;
  public static String shieldAddress;
  public static String deployShieldUrc20Txid;
  public static String deployShieldTxid;
  private BigInteger publicFromAmount;
  Optional<ShieldedAddressInfo> receiverShieldAddressInfo;
  List<Note> shieldOutList = new ArrayList<>();
  public static String transfer =
      "transfer(bytes32[10][],bytes32[2][],bytes32[9][],bytes32[2],bytes32[21][])";
  public Wallet wallet = new Wallet();
  static HttpResponse response;
  static HttpPost httppost;
  public static Integer scalingFactorLogarithm = 0;
  public static Long totalSupply = 1000000000000L;


  /**
   * constructor.
   */
  @BeforeClass(enabled = true, description = "Deploy shield urc20 depend contract")
  public void deployShieldUrc20DependContract() {
    Wallet.setAddressPreFixByte(Parameter.CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    PublicMethed.printAddress(contractExcKey);
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    Assert.assertTrue(PublicMethed.sendcoin(contractExcAddress, 10000000000000L,
        foundationAccountAddress, foundationAccountKey, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String contractName = "shieldUrc20Token";

    String abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_shieldUrc20Token");
    String code = Configuration.getByPath("testng.conf")
        .getString("code.code_shieldUrc20Token");
    String constructorStr = "constructor(uint256,string,string)";
    String data = totalSupply.toString() + "," + "\"TokenURC20\"" + "," + "\"zen20\"";
    logger.info("data:" + data);
    deployShieldUrc20Txid = PublicMethed
        .deployContractWithConstantParame(contractName, abi, code, constructorStr, data, "",
            maxFeeLimit, 0L, 100, null,
            contractExcKey, contractExcAddress, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(deployShieldUrc20Txid);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(deployShieldUrc20Txid, blockingStubFull);
    contractAddressByteString = infoById.get().getContractAddress();
    contractAddressByte = infoById.get().getContractAddress().toByteArray();
    contractAddress = Base58.encode58Check(contractAddressByte);
    logger.info(contractAddress);
    String filePath = "src/test/resources/soliditycode/pedersenHash002.sol";
    contractName = "TokenURC20";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);
    code = retMap.get("byteCode").toString();
    abi = retMap.get("abI").toString();
    data = "\"" + contractAddress + "\"" + "," + scalingFactorLogarithm;
    constructorStr = "constructor(address,uint256)";
    deployShieldTxid = PublicMethed
        .deployContractWithConstantParame(contractName, abi, code, constructorStr, data, "",
            maxFeeLimit, 0L, 100, null,
            contractExcKey, contractExcAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(deployShieldTxid);
    infoById = PublicMethed.getTransactionInfoById(deployShieldTxid, blockingStubFull);
    shieldAddressByteString = infoById.get().getContractAddress();
    shieldAddressByte = infoById.get().getContractAddress().toByteArray();
    shieldAddress = Base58.encode58Check(shieldAddressByte);
    logger.info(shieldAddress);

    data = "\"" + shieldAddress + "\"" + "," + totalSupply.toString();
    String txid = PublicMethed.triggerContract(contractAddressByte,
        "approve(address,uint256)", data, false,
        0, maxFeeLimit, contractExcAddress, contractExcKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info("approve:" + txid);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    publicFromAmount = getRandomAmount();
  }


  @Test(enabled = true, description = "left and right value is 0")
  public void test01LeftAndRightValueIsZero() throws Exception {
    //Query account before mint balance
    final Long beforeMintAccountBalance = getBalanceOfShieldUrc20(zenUrc20TokenOwnerAddressString,
        zenUrc20TokenOwnerAddress, zenUrc20TokenOwnerKey, blockingStubFull);
    //Query contract before mint balance
    final Long beforeMintShieldAccountBalance = getBalanceOfShieldUrc20(shieldAddress,
        zenUrc20TokenOwnerAddress, zenUrc20TokenOwnerKey, blockingStubFull);
    //Generate new shiled account and set note memo
    receiverShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    String memo = "Shield urc20 from T account to shield account in" + System.currentTimeMillis();
    String receiverShieldAddress = receiverShieldAddressInfo.get().getAddress();

    shieldOutList.clear();
    shieldOutList = addShieldUrc20OutputList(shieldOutList, receiverShieldAddress,
        "" + publicFromAmount, memo, blockingStubFull);

    //Create shiled urc20 parameters
    GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters
        = createShieldedUrc20Parameters("ByValueIsZero", publicFromAmount,
        null, null, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity);
  }

  /**
   * constructor.
   */
  public GrpcAPI.ShieldedURC20Parameters createShieldedUrc20Parameters(String methodSuffix,
      BigInteger publicFromAmount, GrpcAPI.DecryptNotesURC20 inputNoteList,
      List<ShieldedAddressInfo> shieldedAddressInfoList, List<Note> outputNoteList,
      String publicToAddress, Long pubicToAmount, WalletGrpc.WalletBlockingStub blockingStubFull,
      WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity) throws ZksnarkException {

    GrpcAPI.PrivateShieldedURC20Parameters.Builder builder
        = GrpcAPI.PrivateShieldedURC20Parameters.newBuilder();

    //Mint type should set public from amount to parameter
    if (publicFromAmount.compareTo(BigInteger.ZERO) > 0) {
      builder.setFromAmount(publicFromAmount.toString());
    }

    builder.setShieldedURC20ContractAddress(ByteString.copyFrom(shieldAddressByte));
    long valueBalance = 0;

    if (inputNoteList != null) {
      logger.info("Enter transfer type code");
      List<String> rootAndPath = new ArrayList<>();
      for (int i = 0; i < inputNoteList.getNoteTxsCount(); i++) {
        long position = inputNoteList.getNoteTxs(i).getPosition();
        rootAndPath.add(getRootAndPath(methodSuffix, position, blockingStubSolidity));
      }
      if (rootAndPath.isEmpty() || rootAndPath.size() != inputNoteList.getNoteTxsCount()) {
        System.out.println("Can't get all merkle tree, please check the notes.");
        return null;
      }
      for (int i = 0; i < rootAndPath.size(); i++) {
        if (rootAndPath.get(i) == null) {
          System.out.println("Can't get merkle path, please check the note " + i + ".");
          return null;
        }
      }

      for (int i = 0; i < inputNoteList.getNoteTxsCount(); ++i) {
        if (i == 0) {
          String shieldedAddress = inputNoteList.getNoteTxs(i).getNote().getPaymentAddress();

          String spendingKey = ByteArray.toHexString(shieldedAddressInfoList.get(0).getSk());
          BytesMessage sk = BytesMessage.newBuilder()
              .setValue(ByteString.copyFrom(ByteArray.fromHexString(spendingKey))).build();
          Optional<GrpcAPI.ExpandedSpendingKeyMessage> esk = Optional
              .of(blockingStubFull.getExpandedSpendingKey(sk));

          //ExpandedSpendingKey expandedSpendingKey = spendingKey.expandedSpendingKey();
          builder.setAsk(esk.get().getAsk());
          builder.setNsk(esk.get().getNsk());
          builder.setOvk(esk.get().getOvk());
        }
        Note.Builder noteBuild = Note.newBuilder();
        noteBuild.setPaymentAddress(shieldedAddressInfoList.get(0).getAddress());
        noteBuild.setValue(inputNoteList.getNoteTxs(i).getNote().getValue());
        noteBuild.setRcm(inputNoteList.getNoteTxs(i).getNote().getRcm());
        noteBuild.setMemo(inputNoteList.getNoteTxs(i).getNote().getMemo());

        byte[] eachRootAndPath = ByteArray.fromHexString(rootAndPath.get(i));
        byte[] root = Arrays.copyOfRange(eachRootAndPath, 0, 32);
        byte[] path = Arrays.copyOfRange(eachRootAndPath, 32, 1056);
        GrpcAPI.SpendNoteURC20.Builder spendURC20NoteBuilder = GrpcAPI.SpendNoteURC20.newBuilder();
        spendURC20NoteBuilder.setNote(noteBuild.build());
        spendURC20NoteBuilder.setAlpha(ByteString.copyFrom(blockingStubFull.getRcm(
            EmptyMessage.newBuilder().build()).getValue().toByteArray()));
        spendURC20NoteBuilder.setRoot(ByteString.copyFrom(root));
        spendURC20NoteBuilder.setPath(ByteString.copyFrom(path));
        spendURC20NoteBuilder.setPos(inputNoteList.getNoteTxs(i).getPosition());

        valueBalance = Math
            .addExact(valueBalance, inputNoteList.getNoteTxs(i).getNote().getValue());
        builder.addShieldedSpends(spendURC20NoteBuilder.build());
      }
    } else {
      //@TODO remove randomOvk by sha256.of(privateKey)
      byte[] ovk = getRandomOvk();
      if (ovk != null) {
        builder.setOvk(ByteString.copyFrom(ovk));
      } else {
        System.out.println("Get random ovk from Rpc failure,please check config");
        return null;
      }
    }

    if (outputNoteList != null) {
      for (int i = 0; i < outputNoteList.size(); i++) {
        Note note = outputNoteList.get(i);
        valueBalance = Math.subtractExact(valueBalance, note.getValue());
        builder.addShieldedReceives(
            GrpcAPI.ReceiveNote.newBuilder().setNote(note).build());
      }
    }

    if (!StringUtil.isNullOrEmpty(publicToAddress)) {
      byte[] to = Commons.decodeFromBase58Check(publicToAddress);
      if (to == null) {
        return null;
      }
      builder.setTransparentToAddress(ByteString.copyFrom(to));
      builder.setToAmount(pubicToAmount.toString());
    }

    try {
      return blockingStubFull.createShieldedContractParameters(builder.build());
    } catch (Exception e) {
      Status status = Status.fromThrowable(e);
      System.out.println("createShieldedContractParameters failed,error "
          + status.getDescription());
    }
    return null;
  }

  public String getRootAndPath(String methodSuffix, long position,
      WalletSolidityGrpc.WalletSolidityBlockingStub
          blockingStubSolidity) {
    String methodStr = "getPath" + methodSuffix + "(uint256)";
    byte[] indexBytes = ByteArray.fromLong(position);
    String argsStr = ByteArray.toHexString(indexBytes);
    argsStr = "000000000000000000000000000000000000000000000000" + argsStr;
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtentionOnSolidity(shieldAddressByte, methodStr, argsStr, true,
            0, 1000000000L, "0", 0, zenUrc20TokenOwnerAddress,
            zenUrc20TokenOwnerKey, blockingStubSolidity);
    byte[] result = transactionExtention.getConstantResult(0).toByteArray();
    return ByteArray.toHexString(result);
  }

  /**
   * constructor.
   */
  public static HttpResponse getNewShieldedAddress(String httpNode) {
    try {
      String requestUrl = "http://" + httpNode + "/wallet/getnewshieldedaddress";
      response = HttpMethed.createConnect(requestUrl);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    return response;
  }

  /**
   * constructor.
   */
  public Optional<ShieldedAddressInfo> getNewShieldedAddress(WalletGrpc.WalletBlockingStub
      blockingStubFull) {
    ShieldedAddressInfo addressInfo = new ShieldedAddressInfo();

    try {
      Optional<BytesMessage> sk = Optional.of(blockingStubFull
          .getSpendingKey(EmptyMessage.newBuilder().build()));
      final Optional<GrpcAPI.DiversifierMessage> d = Optional.of(blockingStubFull.getDiversifier(
          EmptyMessage.newBuilder().build()));

      Optional<GrpcAPI.ExpandedSpendingKeyMessage> expandedSpendingKeyMessage
          = Optional.of(blockingStubFull
          .getExpandedSpendingKey(sk.get()));

      BytesMessage.Builder askBuilder = BytesMessage.newBuilder();
      askBuilder.setValue(expandedSpendingKeyMessage.get().getAsk());
      Optional<BytesMessage> ak = Optional.of(blockingStubFull.getAkFromAsk(askBuilder.build()));

      BytesMessage.Builder nskBuilder = BytesMessage.newBuilder();
      nskBuilder.setValue(expandedSpendingKeyMessage.get().getNsk());
      Optional<BytesMessage> nk = Optional.of(blockingStubFull.getNkFromNsk(nskBuilder.build()));

      GrpcAPI.ViewingKeyMessage.Builder viewBuilder = GrpcAPI.ViewingKeyMessage.newBuilder();
      viewBuilder.setAk(ak.get().getValue());
      viewBuilder.setNk(nk.get().getValue());
      Optional<GrpcAPI.IncomingViewingKeyMessage> ivk = Optional.of(blockingStubFull
          .getIncomingViewingKey(viewBuilder.build()));

      GrpcAPI.IncomingViewingKeyDiversifierMessage.Builder builder
          = GrpcAPI.IncomingViewingKeyDiversifierMessage
          .newBuilder();
      builder.setD(d.get());
      builder.setIvk(ivk.get());
      Optional<GrpcAPI.PaymentAddressMessage> addressMessage = Optional.of(blockingStubFull
          .getZenPaymentAddress(builder.build()));
      addressInfo.setSk(sk.get().getValue().toByteArray());
      addressInfo.setD(new DiversifierT(d.get().getD().toByteArray()));
      addressInfo.setIvk(ivk.get().getIvk().toByteArray());
      addressInfo.setOvk(expandedSpendingKeyMessage.get().getOvk().toByteArray());
      addressInfo.setPkD(addressMessage.get().getPkD().toByteArray());

      return Optional.of(addressInfo);

    } catch (Exception e) {
      e.printStackTrace();
    }

    return Optional.empty();
  }

  /**
   * constructor.
   */
  public static List<Note> addShieldUrc20OutputList(List<Note> shieldOutList,
      String shieldToAddress, String toAmountString, String menoString,
      WalletGrpc.WalletBlockingStub blockingStubFull) {
    String shieldAddress = shieldToAddress;
    String amountString = toAmountString;
    if (menoString.equals("null")) {
      menoString = "";
    }
    long shieldAmount = 0;
    if (!StringUtil.isNullOrEmpty(amountString)) {
      shieldAmount = Long.valueOf(amountString);
    }

    Note.Builder noteBuild = Note.newBuilder();
    noteBuild.setPaymentAddress(shieldAddress);
    //noteBuild.setPaymentAddress(shieldAddress);
    noteBuild.setValue(shieldAmount);
    noteBuild.setRcm(ByteString.copyFrom(blockingStubFull.getRcm(EmptyMessage.newBuilder().build())
        .getValue().toByteArray()));
    noteBuild.setMemo(ByteString.copyFrom(menoString.getBytes()));
    shieldOutList.add(noteBuild.build());
    return shieldOutList;
  }

  /**
   * constructor.
   */
  public Long getBalanceOfShieldUrc20(String queryAddress, byte[] ownerAddress,
      String ownerKey, WalletGrpc.WalletBlockingStub blockingStubFull) {
    String paramStr = "\"" + queryAddress + "\"";
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtention(contractAddressByte, "balanceOf(address)",
            paramStr, false, 0, 0, "0", 0,
            ownerAddress, ownerKey, blockingStubFull);

    String hexBalance = Hex.toHexString(transactionExtention
        .getConstantResult(0).toByteArray());
    for (int i = 0; i < hexBalance.length(); i++) {
      if (hexBalance.charAt(i) != '0') {
        hexBalance = hexBalance.substring(i);
        break;
      }
    }
    logger.info(hexBalance);
    return Long.parseLong(hexBalance, 16);
  }

  /**
   * constructor.
   */
  public byte[] getRandomOvk() {
    try {
      Optional<BytesMessage> sk = Optional.of(blockingStubFull
          .getSpendingKey(EmptyMessage.newBuilder().build()));
      Optional<GrpcAPI.ExpandedSpendingKeyMessage> expandedSpendingKeyMessage
          = Optional.of(blockingStubFull
          .getExpandedSpendingKey(sk.get()));
      return expandedSpendingKeyMessage.get().getOvk().toByteArray();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * constructor.
   */
  public BigInteger getRandomAmount() {
    Random random = new Random();
    int x = random.nextInt(100000) + 100;
    return BigInteger.valueOf(x);
  }

  public byte[] longTo32Bytes(long value) {
    byte[] longBytes = ByteArray.fromLong(value);
    byte[] zeroBytes = new byte[24];
    return ByteUtil.merge(zeroBytes, longBytes);
  }


  /**
   * constructor.
   */
  public static String getRcm(String httpNode) {
    try {
      String requestUrl = "http://" + httpNode + "/wallet/getrcm";
      response = HttpMethed.createConnect(requestUrl);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    return HttpMethed.parseResponseContent(response).getString("value");
  }

}
