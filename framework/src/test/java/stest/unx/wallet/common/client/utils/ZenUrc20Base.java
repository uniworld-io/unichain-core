package stest.unx.wallet.common.client.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.netty.util.internal.StringUtil;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.testng.annotations.BeforeSuite;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.api.GrpcAPI.EmptyMessage;
import org.unx.api.GrpcAPI.Note;
import org.unx.api.GrpcAPI.ShieldedURC20Parameters;
import org.unx.api.GrpcAPI.TransactionExtention;
import org.unx.api.WalletGrpc;
import org.unx.api.WalletSolidityGrpc;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.ByteUtil;
import org.unx.common.utils.Commons;
import org.unx.core.Wallet;
import org.unx.core.exception.ZksnarkException;
import org.unx.core.zen.address.DiversifierT;
import org.unx.protos.Protocol.TransactionInfo;
import org.unx.protos.contract.ShieldContract;
import stest.unx.wallet.common.client.Configuration;

@Slf4j
public class ZenUrc20Base {

  public final String foundationAccountKey = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  public final byte[] foundationAccountAddress = PublicMethed.getFinalAddress(foundationAccountKey);
  public static final String zenUrc20TokenOwnerKey = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.zenUrc20TokenOwnerKey");
  public static final byte[] zenUrc20TokenOwnerAddress = PublicMethed
      .getFinalAddress(zenUrc20TokenOwnerKey);
  public static final String zenUrc20TokenOwnerAddressString = PublicMethed
      .getAddressString(zenUrc20TokenOwnerKey);
  public ManagedChannel channelFull = null;
  public WalletGrpc.WalletBlockingStub blockingStubFull = null;
  public ManagedChannel channelSolidity = null;
  public ManagedChannel channelPbft = null;

  public WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  public WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubPbft = null;
  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);

  public static long maxFeeLimit = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.maxFeeLimit");
  public com.google.protobuf.ByteString contractAddressByteString;
  public static byte[] contractAddressByte;
  public static String contractAddress;
  public static com.google.protobuf.ByteString shieldAddressByteString;
  public static byte[] shieldAddressByte;
  public static String shieldAddress;
  public static String deployShieldUrc20Txid;
  public static String deployShieldTxid;
  public static String mint = "mint(uint256,bytes32[9],bytes32[2],bytes32[21])";
  public static String transfer =
      "transfer(bytes32[10][],bytes32[2][],bytes32[9][],bytes32[2],bytes32[21][])";
  public static String burn = "burn(bytes32[10],bytes32[2],uint256,bytes32[2],address,"
      + "bytes32[3],bytes32[9][],bytes32[21][])";
  public Wallet wallet = new Wallet();
  static HttpResponse response;
  static HttpPost httppost;
  static JSONObject responseContent;
  public static Integer scalingFactorLogarithm = 0;
  public static Long totalSupply = 1000000000000L;


  /**
   * constructor.
   */
  @BeforeSuite(enabled = true, description = "Deploy shield urc20 depend contract")
  public void deployShieldUrc20DependContract() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    getDailyBuildStartNum();
    Assert.assertTrue(PublicMethed.sendcoin(zenUrc20TokenOwnerAddress, 10000000000000L,
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
            zenUrc20TokenOwnerKey, zenUrc20TokenOwnerAddress, blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(deployShieldUrc20Txid);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(deployShieldUrc20Txid, blockingStubFull);
    contractAddressByteString = infoById.get().getContractAddress();
    contractAddressByte = infoById.get().getContractAddress().toByteArray();
    contractAddress = Base58.encode58Check(contractAddressByte);
    logger.info(contractAddress);

    contractName = "shield";
    abi = Configuration.getByPath("testng.conf")
        .getString("abi.abi_shield");
    code = Configuration.getByPath("testng.conf")
        .getString("code.code_shield");
    data = "\"" + contractAddress + "\"" + "," + scalingFactorLogarithm;
    constructorStr = "constructor(address,uint256)";
    deployShieldTxid = PublicMethed
        .deployContractWithConstantParame(contractName, abi, code, constructorStr, data, "",
            maxFeeLimit, 0L, 100, null,
            zenUrc20TokenOwnerKey, zenUrc20TokenOwnerAddress, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info(deployShieldTxid);
    infoById = PublicMethed
        .getTransactionInfoById(deployShieldTxid, blockingStubFull);
    shieldAddressByteString = infoById.get().getContractAddress();
    shieldAddressByte = infoById.get().getContractAddress().toByteArray();
    shieldAddress = Base58.encode58Check(shieldAddressByte);
    logger.info(shieldAddress);

    data = "\"" + shieldAddress + "\"" + "," + totalSupply.toString();
    String txid = PublicMethed.triggerContract(contractAddressByte,
        "approve(address,uint256)", data, false,
        0, maxFeeLimit, zenUrc20TokenOwnerAddress, zenUrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    logger.info("approve:" + txid);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);


  }

  /**
   * constructor.
   */
  public void getDailyBuildStartNum() {
    DailyBuildReport.startBlockNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder()
            .build()).getBlockHeader().getRawData().getNumber();
    System.out.println("!!!!!!! 222222222startnum:" + DailyBuildReport.startBlockNum);
  }


  /**
   * constructor.
   */
  public GrpcAPI.ShieldedURC20Parameters createShieldedUrc20Parameters(BigInteger publicFromAmount,
      GrpcAPI.DecryptNotesURC20 inputNoteList, List<ShieldedAddressInfo> shieldedAddressInfoList,
      List<Note> outputNoteList, String publicToAddress, Long pubicToAmount,
      WalletGrpc.WalletBlockingStub blockingStubFull,
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
        rootAndPath.add(getRootAndPath(position, blockingStubSolidity));
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

        System.out.println("address " + shieldedAddressInfoList.get(0).getAddress());
        System.out.println("value " + inputNoteList.getNoteTxs(i).getNote().getValue());
        System.out.println("unxId " + inputNoteList.getNoteTxs(i).getTxid());
        System.out.println("index " + inputNoteList.getNoteTxs(i).getIndex());
        System.out.println("position " + inputNoteList.getNoteTxs(i).getPosition());

        byte[] eachRootAndPath = ByteArray.fromHexString(rootAndPath.get(i));
        byte[] root = Arrays.copyOfRange(eachRootAndPath, 0, 32);
        byte[] path = Arrays.copyOfRange(eachRootAndPath, 32, 1056);
        GrpcAPI.SpendNoteURC20.Builder spendUrc20NoteBuilder = GrpcAPI.SpendNoteURC20.newBuilder();
        spendUrc20NoteBuilder.setNote(noteBuild.build());
        spendUrc20NoteBuilder.setAlpha(ByteString.copyFrom(blockingStubFull.getRcm(
            EmptyMessage.newBuilder().build()).getValue().toByteArray()));
        spendUrc20NoteBuilder.setRoot(ByteString.copyFrom(root));
        spendUrc20NoteBuilder.setPath(ByteString.copyFrom(path));
        spendUrc20NoteBuilder.setPos(inputNoteList.getNoteTxs(i).getPosition());

        valueBalance = Math.addExact(valueBalance, inputNoteList.getNoteTxs(i).getNote()
            .getValue());
        builder.addShieldedSpends(spendUrc20NoteBuilder.build());
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
        GrpcAPI.Note note = outputNoteList.get(i);
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


  /**
   * constructor.
   */
  public GrpcAPI.ShieldedURC20Parameters createShieldedUrc20ParametersWithoutAsk(
      BigInteger publicFromAmount,
      GrpcAPI.DecryptNotesURC20 inputNoteList, List<ShieldedAddressInfo> shieldedAddressInfoList,
      List<Note> outputNoteList, String publicToAddress, byte[] receiverAddressbyte,
      Long pubicToAmount,
      WalletGrpc.WalletBlockingStub blockingStubFull,
      WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity) throws ZksnarkException {

    GrpcAPI.PrivateShieldedURC20ParametersWithoutAsk.Builder builder
        = GrpcAPI.PrivateShieldedURC20ParametersWithoutAsk.newBuilder();

    //Mint type should set public from amount to parameter
    if (publicFromAmount.compareTo(BigInteger.ZERO) > 0) {
      builder.setFromAmount(publicFromAmount.toString());
    }

    builder.setShieldedURC20ContractAddress(ByteString.copyFrom(shieldAddressByte));

    long valueBalance = 0;
    byte[] ask = new byte[32];
    if (inputNoteList != null) {
      List<String> rootAndPath = new ArrayList<>();
      for (int i = 0; i < inputNoteList.getNoteTxsCount(); i++) {
        long position = inputNoteList.getNoteTxs(i).getPosition();
        rootAndPath.add(getRootAndPath(position, blockingStubSolidity));
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
          String spendingKey = ByteArray.toHexString(shieldedAddressInfoList.get(i).getSk());
          BytesMessage sk = BytesMessage.newBuilder()
              .setValue(ByteString.copyFrom(ByteArray.fromHexString(spendingKey))).build();
          Optional<GrpcAPI.ExpandedSpendingKeyMessage> esk = Optional
              .of(blockingStubFull.getExpandedSpendingKey(sk));
          System.arraycopy(esk.get().getAsk().toByteArray(), 0, ask, 0, 32);

          String ask1 = ByteArray.toHexString(esk.get().getAsk().toByteArray());

          BytesMessage ask2 = BytesMessage.newBuilder()
              .setValue(ByteString.copyFrom(ByteArray.fromHexString(ask1))).build();
          Optional<BytesMessage> ak = Optional.of(blockingStubFull.getAkFromAsk(ask2));
          String akString = ByteArray.toHexString(ak.get().getValue().toByteArray());

          builder.setAk(ByteString.copyFrom(ByteArray.fromHexString(akString)));
          builder.setOvk(esk.get().getOvk());
          builder.setNsk(esk.get().getNsk());

        }
        Note.Builder noteBuild = Note.newBuilder();
        noteBuild.setPaymentAddress(shieldedAddressInfoList.get(i).getAddress());
        noteBuild.setValue(inputNoteList.getNoteTxs(i).getNote().getValue());
        noteBuild.setRcm(inputNoteList.getNoteTxs(i).getNote().getRcm());
        noteBuild.setMemo(inputNoteList.getNoteTxs(i).getNote().getMemo());

        System.out.println("address " + shieldedAddressInfoList.get(i).getAddress());
        System.out.println("value " + inputNoteList.getNoteTxs(i).getNote().getValue());
        System.out.println("unxId " + ByteArray.toHexString(inputNoteList.getNoteTxs(i)
            .getTxid().toByteArray()));
        System.out.println("index " + inputNoteList.getNoteTxs(i).getIndex());
        System.out.println("position " + inputNoteList.getNoteTxs(i).getPosition());

        byte[] eachRootAndPath = ByteArray.fromHexString(rootAndPath.get(i));
        byte[] root = Arrays.copyOfRange(eachRootAndPath, 0, 32);
        byte[] path = Arrays.copyOfRange(eachRootAndPath, 32, 1056);
        GrpcAPI.SpendNoteURC20.Builder spendUrc20NoteBuilder = GrpcAPI.SpendNoteURC20.newBuilder();
        spendUrc20NoteBuilder.setNote(noteBuild.build());
        spendUrc20NoteBuilder.setAlpha(ByteString.copyFrom(blockingStubFull.getRcm(
            EmptyMessage.newBuilder().build()).getValue().toByteArray()));
        spendUrc20NoteBuilder.setRoot(ByteString.copyFrom(root));
        spendUrc20NoteBuilder.setPath(ByteString.copyFrom(path));
        spendUrc20NoteBuilder.setPos(inputNoteList.getNoteTxs(i).getPosition());

        builder.addShieldedSpends(spendUrc20NoteBuilder.build());
        valueBalance = Math.addExact(valueBalance, inputNoteList.getNoteTxs(i)
            .getNote().getValue());
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
        GrpcAPI.Note note = outputNoteList.get(i);
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

    ShieldedURC20Parameters parameters = blockingStubFull
        .createShieldedContractParametersWithoutAsk(builder.build());
    if (parameters == null) {
      System.out.println("createShieldedContractParametersWithoutAsk failed!");
      return null;
    }

    GrpcAPI.ShieldedURC20TriggerContractParameters.Builder stBuilder =
        GrpcAPI.ShieldedURC20TriggerContractParameters.newBuilder();
    stBuilder.setShieldedURC20Parameters(parameters);

    if (parameters.getParameterType().equals("burn")) {
      stBuilder.setAmount(pubicToAmount.toString());
      stBuilder.setTransparentToAddress(ByteString.copyFrom(receiverAddressbyte));
    }

    ByteString messageHash = parameters.getMessageHash();
    List<ShieldContract.SpendDescription> spendDescList = parameters.getSpendDescriptionList();
    ShieldedURC20Parameters.Builder newBuilder =
        ShieldedURC20Parameters.newBuilder().mergeFrom(parameters);
    for (int i = 0; i < spendDescList.size(); i++) {
      GrpcAPI.SpendAuthSigParameters.Builder builder1 = GrpcAPI.SpendAuthSigParameters.newBuilder();
      builder1.setAsk(ByteString.copyFrom(ask));
      builder1.setTxHash(messageHash);
      builder1.setAlpha(builder.build().getShieldedSpends(i).getAlpha());

      BytesMessage authSig = blockingStubFull.createSpendAuthSig(builder1.build());
      newBuilder.getSpendDescriptionBuilder(i)
          .setSpendAuthoritySignature(
              ByteString.copyFrom(authSig.getValue().toByteArray()));

      stBuilder.addSpendAuthoritySignature(authSig);
      BytesMessage triggerInputData;
      try {
        triggerInputData = blockingStubFull.getTriggerInputForShieldedURC20Contract(stBuilder
            .build());
      } catch (Exception e) {
        triggerInputData = null;
        System.out.println("getTriggerInputForShieldedURC20Contract error, please retry!");
      }
      if (triggerInputData == null) {
        return null;
      }
      newBuilder.setTriggerContractInput(ByteArray.toHexString(triggerInputData.getValue()
          .toByteArray()));


    }
    return newBuilder.build();
  }


  /**
   * constructor.
   */
  public String getRootAndPath(long position, WalletSolidityGrpc.WalletSolidityBlockingStub
      blockingStubSolidity) {
    String methodStr = "getPath(uint256)";
    byte[] indexBytes = ByteArray.fromLong(position);
    String argsStr = ByteArray.toHexString(indexBytes);
    argsStr = "000000000000000000000000000000000000000000000000" + argsStr;
    TransactionExtention transactionExtention = PublicMethed
        .triggerConstantContractForExtentionOnSolidity(shieldAddressByte, methodStr, argsStr,
            true, 0, 1000000000L, "0", 0, zenUrc20TokenOwnerAddress,
            zenUrc20TokenOwnerKey, blockingStubSolidity);
    byte[] result = transactionExtention.getConstantResult(0).toByteArray();
    return ByteArray.toHexString(result);
  }


  /**
   * constructor.
   */
  public String encodeMintParamsToHexString(GrpcAPI.ShieldedURC20Parameters parameters,
      BigInteger value) {
    byte[] mergedBytes;
    ShieldContract.ReceiveDescription revDesc = parameters.getReceiveDescription(0);
    mergedBytes = ByteUtil.merge(
        ByteUtil.bigIntegerToBytes(value, 32),
        revDesc.getNoteCommitment().toByteArray(),
        revDesc.getValueCommitment().toByteArray(),
        revDesc.getEpk().toByteArray(),
        revDesc.getZkproof().toByteArray(),
        parameters.getBindingSignature().toByteArray(),
        revDesc.getCEnc().toByteArray(),
        revDesc.getCOut().toByteArray(),
        new byte[12]
    );
    return ByteArray.toHexString(mergedBytes);
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

      System.out.println("ivk " + ByteArray.toHexString(ivk.get().getIvk().toByteArray()));
      System.out.println("ovk " + ByteArray.toHexString(expandedSpendingKeyMessage.get()
          .getOvk().toByteArray()));

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
  public String getBalanceOfShieldUrc20String(String queryAddress, byte[] ownerAddress,
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
    return hexBalance;
  }


  /**
   * constructor.
   */
  public GrpcAPI.DecryptNotesURC20 scanShieldedUrc20NoteByIvk(ShieldedAddressInfo
      shieldedAddressInfo, WalletGrpc.WalletBlockingStub blockingStubFull,
      WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity) throws Exception {
    long currentBlockNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber();

    Long startNum = currentBlockNum - 90L;
    final Long endNum = currentBlockNum;
    if (currentBlockNum < 100) {
      startNum = 1L;
    }

    String spendingKey = ByteArray.toHexString(shieldedAddressInfo.getSk());
    BytesMessage sk = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(spendingKey))).build();
    Optional<GrpcAPI.ExpandedSpendingKeyMessage> esk = Optional
        .of(blockingStubFull.getExpandedSpendingKey(sk));

    String ask = ByteArray.toHexString(esk.get().getAsk().toByteArray());

    BytesMessage ask1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(ask))).build();
    Optional<BytesMessage> ak = Optional.of(blockingStubFull.getAkFromAsk(ask1));
    String akString = ByteArray.toHexString(ak.get().getValue().toByteArray());
    //System.out.println("ak:" + ByteArray.toHexString(ak.get().getValue().toByteArray()));

    String nsk = ByteArray.toHexString(esk.get().getNsk().toByteArray());

    BytesMessage nsk1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(nsk))).build();
    Optional<BytesMessage> nk = Optional.of(blockingStubFull.getNkFromNsk(nsk1));
    //System.out.println("nk:" + ByteArray.toHexString(nk.get().getValue().toByteArray()));
    String nkString = ByteArray.toHexString(nk.get().getValue().toByteArray());

    GrpcAPI.ViewingKeyMessage.Builder viewBuilder = GrpcAPI.ViewingKeyMessage.newBuilder();
    viewBuilder.setAk(ak.get().getValue());
    viewBuilder.setNk(nk.get().getValue());
    GrpcAPI.IncomingViewingKeyMessage ivk = blockingStubFull
        .getIncomingViewingKey(viewBuilder.build());

    //ivk.getIvk()
    String ivkString = ByteArray.toHexString(ivk.getIvk().toByteArray());
    String ivkStringOld = ByteArray.toHexString(shieldedAddressInfo.getIvk());
    GrpcAPI.IvkDecryptURC20Parameters parameters = GrpcAPI.IvkDecryptURC20Parameters
        .newBuilder()
        .setStartBlockIndex(startNum)
        .setEndBlockIndex(endNum)
        .setShieldedURC20ContractAddress(ByteString.copyFrom(Commons.decode58Check(shieldAddress)))
        .setIvk(ByteString.copyFrom(ByteArray.fromHexString(ivkString)))
        .setAk(ByteString.copyFrom(ByteArray.fromHexString(akString)))
        .setNk(ByteString.copyFrom(ByteArray.fromHexString(nkString)))
        .build();
    try {
      return blockingStubSolidity.scanShieldedURC20NotesByIvk(parameters);
    } catch (Exception e) {
      System.out.println(e);
      Status status = Status.fromThrowable(e);
      System.out.println("ScanShieldedURC20NoteByIvk failed,error " + status.getDescription());

    }
    return null;
  }


  /**
   * constructor.
   */
  public GrpcAPI.DecryptNotesURC20 scanShieldedUrc20NoteByIvk(ShieldedAddressInfo
      shieldedAddressInfo, WalletGrpc.WalletBlockingStub blockingStubFull) throws Exception {
    long currentBlockNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber();

    Long startNum = currentBlockNum - 90L;
    final Long endNum = currentBlockNum;
    if (currentBlockNum < 100) {
      startNum = 1L;
    }

    String spendingKey = ByteArray.toHexString(shieldedAddressInfo.getSk());
    BytesMessage sk = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(spendingKey))).build();
    Optional<GrpcAPI.ExpandedSpendingKeyMessage> esk = Optional
        .of(blockingStubFull.getExpandedSpendingKey(sk));

    String ask = ByteArray.toHexString(esk.get().getAsk().toByteArray());

    BytesMessage ask1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(ask))).build();
    Optional<BytesMessage> ak = Optional.of(blockingStubFull.getAkFromAsk(ask1));
    String akString = ByteArray.toHexString(ak.get().getValue().toByteArray());

    String nsk = ByteArray.toHexString(esk.get().getNsk().toByteArray());

    BytesMessage nsk1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(nsk))).build();
    Optional<BytesMessage> nk = Optional.of(blockingStubFull.getNkFromNsk(nsk1));
    String nkString = ByteArray.toHexString(nk.get().getValue().toByteArray());

    GrpcAPI.ViewingKeyMessage.Builder viewBuilder = GrpcAPI.ViewingKeyMessage.newBuilder();
    viewBuilder.setAk(ak.get().getValue());
    viewBuilder.setNk(nk.get().getValue());
    GrpcAPI.IncomingViewingKeyMessage ivk = blockingStubFull
        .getIncomingViewingKey(viewBuilder.build());

    String ivkString = ByteArray.toHexString(ivk.getIvk().toByteArray());
    GrpcAPI.IvkDecryptURC20Parameters parameters = GrpcAPI.IvkDecryptURC20Parameters
        .newBuilder()
        .setStartBlockIndex(startNum)
        .setEndBlockIndex(endNum)
        .setShieldedURC20ContractAddress(ByteString.copyFrom(Commons.decode58Check(shieldAddress)))
        .setIvk(ByteString.copyFrom(ByteArray.fromHexString(ivkString)))
        .setAk(ByteString.copyFrom(ByteArray.fromHexString(akString)))
        .setNk(ByteString.copyFrom(ByteArray.fromHexString(nkString)))
        //.setEvents()
        .build();
    try {
      return blockingStubFull.scanShieldedURC20NotesByIvk(parameters);
    } catch (Exception e) {
      System.out.println(e);
      Status status = Status.fromThrowable(e);
      System.out.println("ScanShieldedURC20NoteByIvk failed,error " + status.getDescription());

    }
    return null;
  }


  /**
   * constructor.
   */
  public GrpcAPI.DecryptNotesURC20 scanShieldedUrc20NoteByIvkWithRange(ShieldedAddressInfo
      shieldedAddressInfo, Long startNum, Long endNum,
      WalletGrpc.WalletBlockingStub blockingStubFull) throws Exception {

    String spendingKey = ByteArray.toHexString(shieldedAddressInfo.getSk());
    BytesMessage sk = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(spendingKey))).build();
    Optional<GrpcAPI.ExpandedSpendingKeyMessage> esk = Optional
        .of(blockingStubFull.getExpandedSpendingKey(sk));

    String ask = ByteArray.toHexString(esk.get().getAsk().toByteArray());

    BytesMessage ask1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(ask))).build();
    Optional<BytesMessage> ak = Optional.of(blockingStubFull.getAkFromAsk(ask1));
    String akString = ByteArray.toHexString(ak.get().getValue().toByteArray());

    String nsk = ByteArray.toHexString(esk.get().getNsk().toByteArray());

    BytesMessage nsk1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(nsk))).build();
    Optional<BytesMessage> nk = Optional.of(blockingStubFull.getNkFromNsk(nsk1));
    String nkString = ByteArray.toHexString(nk.get().getValue().toByteArray());

    GrpcAPI.ViewingKeyMessage.Builder viewBuilder = GrpcAPI.ViewingKeyMessage.newBuilder();
    viewBuilder.setAk(ak.get().getValue());
    viewBuilder.setNk(nk.get().getValue());
    GrpcAPI.IncomingViewingKeyMessage ivk = blockingStubFull
        .getIncomingViewingKey(viewBuilder.build());

    String ivkString = ByteArray.toHexString(ivk.getIvk().toByteArray());
    GrpcAPI.DecryptNotesURC20 result = GrpcAPI.DecryptNotesURC20.newBuilder().build();
    GrpcAPI.DecryptNotesURC20 tempNoteTxs;
    while (startNum < endNum) {
      GrpcAPI.IvkDecryptURC20Parameters parameters = GrpcAPI.IvkDecryptURC20Parameters
          .newBuilder()
          .setStartBlockIndex(startNum)
          .setEndBlockIndex(startNum + 99)
          .setShieldedURC20ContractAddress(ByteString
                  .copyFrom(Commons.decode58Check(shieldAddress)))
          .setIvk(ByteString.copyFrom(ByteArray.fromHexString(ivkString)))
          .setAk(ByteString.copyFrom(ByteArray.fromHexString(akString)))
          .setNk(ByteString.copyFrom(ByteArray.fromHexString(nkString)))
          .build();
      tempNoteTxs = blockingStubFull.scanShieldedURC20NotesByIvk(parameters);
      logger.info("tempNoteTxs size:" + tempNoteTxs.getNoteTxsCount());

      result = result.toBuilder().addAllNoteTxs(tempNoteTxs.getNoteTxsList()).build();

      startNum = startNum + 99;
    }
    try {
      return result;
    } catch (Exception e) {
      System.out.println(e);
      Status status = Status.fromThrowable(e);
      System.out.println("ScanShieldedURC20NoteByIvk failed,error " + status.getDescription());

    }
    return null;
  }


  /**
   * constructor.
   */
  public GrpcAPI.DecryptNotesURC20 scanShieldedUrc20NoteByOvk(ShieldedAddressInfo
      shieldedAddressInfo, WalletGrpc.WalletBlockingStub blockingStubFull) throws Exception {
    long currentBlockNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber();
    Long startNum = currentBlockNum - 90L;
    Long endNum = currentBlockNum;
    if (currentBlockNum < 100) {
      startNum = 1L;
    }

    String ovkString = ByteArray.toHexString(shieldedAddressInfo.getOvk());
    GrpcAPI.OvkDecryptURC20Parameters parameters = GrpcAPI.OvkDecryptURC20Parameters.newBuilder()
        .setStartBlockIndex(startNum)
        .setEndBlockIndex(endNum)
        .setOvk(ByteString.copyFrom(ByteArray.fromHexString(ovkString)))
        .setShieldedURC20ContractAddress(ByteString.copyFrom(Commons.decode58Check(shieldAddress)))
        .build();

    try {
      return blockingStubFull.scanShieldedURC20NotesByOvk(parameters);
    } catch (Exception e) {
      System.out.println(e);
      Status status = Status.fromThrowable(e);
      System.out.println("ScanShieldedURC20NoteByovk failed,error " + status.getDescription());

    }
    return null;
  }


  /**
   * constructor.
   */
  public GrpcAPI.DecryptNotesURC20 scanShieldedUrc20NoteByOvk(ShieldedAddressInfo
      shieldedAddressInfo, WalletGrpc.WalletBlockingStub blockingStubFull,
      WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity) throws Exception {
    long currentBlockNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber();
    Long startNum = currentBlockNum - 90L;
    Long endNum = currentBlockNum;
    if (currentBlockNum < 100) {
      startNum = 1L;
    }

    String ovkString = ByteArray.toHexString(shieldedAddressInfo.getOvk());
    GrpcAPI.OvkDecryptURC20Parameters parameters = GrpcAPI.OvkDecryptURC20Parameters.newBuilder()
        .setStartBlockIndex(startNum)
        .setEndBlockIndex(endNum)
        .setOvk(ByteString.copyFrom(ByteArray.fromHexString(ovkString)))
        .setShieldedURC20ContractAddress(ByteString.copyFrom(Commons.decode58Check(shieldAddress)))
        .build();

    try {
      return blockingStubSolidity.scanShieldedURC20NotesByOvk(parameters);
    } catch (Exception e) {
      System.out.println(e);
      Status status = Status.fromThrowable(e);
      System.out.println("ScanShieldedURC20NoteByovk failed,error " + status.getDescription());

    }
    return null;
  }

  /**
   * constructor.
   */
  public static Boolean getUrc20SpendResult(
      ShieldedAddressInfo shieldAddressInfo, GrpcAPI.DecryptNotesURC20.NoteTx noteTx,
      WalletGrpc.WalletBlockingStub blockingStubFull) {

    GrpcAPI.NfURC20Parameters.Builder builder = GrpcAPI.NfURC20Parameters.newBuilder();

    String spendingKey = ByteArray.toHexString(shieldAddressInfo.getSk());
    BytesMessage sk = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(spendingKey))).build();
    Optional<GrpcAPI.ExpandedSpendingKeyMessage> esk = Optional
        .of(blockingStubFull.getExpandedSpendingKey(sk));

    String ask = ByteArray.toHexString(esk.get().getAsk().toByteArray());
    BytesMessage ask1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(ask))).build();
    Optional<BytesMessage> ak = Optional.of(blockingStubFull.getAkFromAsk(ask1));
    String nsk = ByteArray.toHexString(esk.get().getNsk().toByteArray());
    BytesMessage nsk1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(nsk))).build();
    Optional<BytesMessage> nk = Optional.of(blockingStubFull.getNkFromNsk(nsk1));
    builder.setAk(ak.get().getValue());
    builder.setNk(nk.get().getValue());
    builder.setPosition(noteTx.getPosition());
    builder.setShieldedURC20ContractAddress(ByteString.copyFrom(shieldAddressByte));

    Note.Builder noteBuild = Note.newBuilder();
    noteBuild.setPaymentAddress(shieldAddressInfo.getAddress());
    noteBuild.setValue(noteTx.getNote().getValue());
    noteBuild.setRcm(noteTx.getNote().getRcm());
    noteBuild.setMemo(noteTx.getNote().getMemo());
    builder.setNote(noteBuild.build());

    Optional<GrpcAPI.NullifierResult> result = Optional.of(blockingStubFull
        .isShieldedURC20ContractNoteSpent(builder.build()));
    return result.get().getIsSpent();
  }


  /**
   * constructor.
   */
  public static Boolean getUrc20SpendResult(
      ShieldedAddressInfo shieldAddressInfo, GrpcAPI.DecryptNotesURC20.NoteTx noteTx,
      WalletGrpc.WalletBlockingStub blockingStubFull,
      WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity) {

    GrpcAPI.NfURC20Parameters.Builder builder = GrpcAPI.NfURC20Parameters.newBuilder();

    String spendingKey = ByteArray.toHexString(shieldAddressInfo.getSk());
    BytesMessage sk = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(spendingKey))).build();
    Optional<GrpcAPI.ExpandedSpendingKeyMessage> esk = Optional
        .of(blockingStubFull.getExpandedSpendingKey(sk));

    String ask = ByteArray.toHexString(esk.get().getAsk().toByteArray());
    BytesMessage ask1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(ask))).build();
    Optional<BytesMessage> ak = Optional.of(blockingStubFull.getAkFromAsk(ask1));
    String nsk = ByteArray.toHexString(esk.get().getNsk().toByteArray());
    BytesMessage nsk1 = BytesMessage.newBuilder()
        .setValue(ByteString.copyFrom(ByteArray.fromHexString(nsk))).build();
    Optional<BytesMessage> nk = Optional.of(blockingStubFull.getNkFromNsk(nsk1));
    builder.setAk(ak.get().getValue());
    builder.setNk(nk.get().getValue());
    builder.setPosition(noteTx.getPosition());
    builder.setShieldedURC20ContractAddress(ByteString.copyFrom(shieldAddressByte));

    Note.Builder noteBuild = Note.newBuilder();
    noteBuild.setPaymentAddress(shieldAddressInfo.getAddress());
    noteBuild.setValue(noteTx.getNote().getValue());
    noteBuild.setRcm(noteTx.getNote().getRcm());
    noteBuild.setMemo(noteTx.getNote().getMemo());
    builder.setNote(noteBuild.build());

    Optional<GrpcAPI.NullifierResult> result = Optional.of(blockingStubSolidity
        .isShieldedURC20ContractNoteSpent(builder.build()));
    return result.get().getIsSpent();
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

  /**
   * constructor.
   */
  public Long getRandomLongAmount() {
    Random random = new Random();
    int x = random.nextInt(100000) + 100;
    return Long.valueOf(x);
  }

  /**
   * constructor.
   */
  public String encodeTransferParamsToHexString(GrpcAPI.ShieldedURC20Parameters parameters) {
    byte[] input = new byte[0];
    byte[] spendAuthSig = new byte[0];
    byte[] output = new byte[0];
    byte[] c = new byte[0];
    byte[] bindingSig;
    final byte[] mergedBytes;
    List<ShieldContract.SpendDescription> spendDescs = parameters.getSpendDescriptionList();
    for (ShieldContract.SpendDescription spendDesc : spendDescs) {
      input = ByteUtil.merge(input,
          spendDesc.getNullifier().toByteArray(),
          spendDesc.getAnchor().toByteArray(),
          spendDesc.getValueCommitment().toByteArray(),
          spendDesc.getRk().toByteArray(),
          spendDesc.getZkproof().toByteArray()
      );
      spendAuthSig = ByteUtil.merge(
          spendAuthSig, spendDesc.getSpendAuthoritySignature().toByteArray());
    }
    byte[] inputOffsetbytes = longTo32Bytes(192);
    long spendCount = spendDescs.size();
    byte[] spendCountBytes = longTo32Bytes(spendCount);
    byte[] authOffsetBytes = longTo32Bytes(192 + 32 + 320 * spendCount);
    List<ShieldContract.ReceiveDescription> recvDescs = parameters.getReceiveDescriptionList();
    for (ShieldContract.ReceiveDescription recvDesc : recvDescs) {
      output = ByteUtil.merge(output,
          recvDesc.getNoteCommitment().toByteArray(),
          recvDesc.getValueCommitment().toByteArray(),
          recvDesc.getEpk().toByteArray(),
          recvDesc.getZkproof().toByteArray()
      );
      c = ByteUtil.merge(c,
          recvDesc.getCEnc().toByteArray(),
          recvDesc.getCOut().toByteArray(),
          new byte[12]
      );
    }
    long recvCount = recvDescs.size();
    byte[] recvCountBytes = longTo32Bytes(recvCount);
    byte[] outputOffsetbytes = longTo32Bytes(192 + 32 + 320 * spendCount + 32 + 64 * spendCount);
    byte[] coffsetBytes = longTo32Bytes(192 + 32 + 320 * spendCount + 32 + 64 * spendCount + 32
        + 288 * recvCount);
    bindingSig = parameters.getBindingSignature().toByteArray();
    mergedBytes = ByteUtil.merge(inputOffsetbytes,
        authOffsetBytes,
        outputOffsetbytes,
        bindingSig,
        coffsetBytes,
        spendCountBytes,
        input,
        spendCountBytes,
        spendAuthSig,
        recvCountBytes,
        output,
        recvCountBytes,
        c
    );
    return ByteArray.toHexString(mergedBytes);
  }

  /**
   * constructor.
   */
  public String encodeBurnParamsToHexString(GrpcAPI.ShieldedURC20Parameters parameters,
      BigInteger value,
      String transparentToAddress) {
    byte[] mergedBytes;
    byte[] payTo = new byte[32];
    byte[] transparentToAddressBytes = Commons.decodeFromBase58Check(transparentToAddress);
    System.arraycopy(transparentToAddressBytes, 0, payTo, 11, 21);
    ShieldContract.SpendDescription spendDesc = parameters.getSpendDescription(0);
    mergedBytes = ByteUtil.merge(
        spendDesc.getNullifier().toByteArray(),
        spendDesc.getAnchor().toByteArray(),
        spendDesc.getValueCommitment().toByteArray(),
        spendDesc.getRk().toByteArray(),
        spendDesc.getZkproof().toByteArray(),
        spendDesc.getSpendAuthoritySignature().toByteArray(),
        ByteUtil.bigIntegerToBytes(value, 32),
        parameters.getBindingSignature().toByteArray(),
        payTo
    );
    return ByteArray.toHexString(mergedBytes);
  }


  /**
   * constructor.
   */
  public byte[] longTo32Bytes(long value) {
    byte[] longBytes = ByteArray.fromLong(value);
    byte[] zeroBytes = new byte[24];
    return ByteUtil.merge(zeroBytes, longBytes);
  }

  /**
   * constructor.
   */
  public JSONArray getHttpShieldedReceivesJsonArray(JSONArray shieldReceives, Long value,
      String paymentAddress, String rcm) {
    JSONObject note = new JSONObject();
    note.put("value", value);
    note.put("payment_address", paymentAddress);
    note.put("rcm", rcm);
    JSONObject noteIndex = new JSONObject();
    noteIndex.put("note", note);
    shieldReceives.add(noteIndex);
    return shieldReceives;

  }


  /**
   * constructor.
   */
  public static HttpResponse createShieldContractParameters(String httpNode, Long fromAmount,
      JSONObject shieldAccountInfo, JSONArray shiledReceives) {
    try {
      final String requestUrl = "http://" + httpNode + "/wallet/createshieldedcontractparameters";

      JSONObject rawBody = new JSONObject();
      rawBody.put("ovk", "4364c875deeb663781a2f1530f9e4f87ea81cc3c757ca2a30fa4768940de2f98");
      rawBody.put("from_amount", fromAmount.toString());
      rawBody.put("shielded_receives", shiledReceives);
      rawBody.put("shielded_URC20_contract_address", shieldAddress);
      rawBody.put("visible", true);

      response = HttpMethed.createConnectForShieldUrc20(requestUrl, rawBody);

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
  public static HttpResponse createShieldContractParametersForBurn(String httpNode,
      JSONObject shieldAccountInfo, JSONArray shieldedSpends, String toAddress, Long toAmount) {
    return createShieldContractParametersForBurn(httpNode, shieldAccountInfo, shieldedSpends,
        toAddress, toAmount, null);

  }

  /**
   * constructor.
   */
  public static HttpResponse createShieldContractParametersForBurn(String httpNode,
      JSONObject shieldAccountInfo, JSONArray shieldedSpends, String toAddress, Long toAmount,
      JSONArray shieldedReceiver) {
    try {
      final String requestUrl = "http://" + httpNode + "/wallet/createshieldedcontractparameters";
      JSONObject rawBody = new JSONObject();
      rawBody.put("ovk", shieldAccountInfo.getString("ovk"));
      rawBody.put("ask", shieldAccountInfo.getString("ask"));
      rawBody.put("nsk", shieldAccountInfo.getString("nsk"));
      rawBody.put("shielded_spends", shieldedSpends);
      if (shieldedReceiver != null) {
        rawBody.put("shielded_receives", shieldedReceiver);
      }
      rawBody.put("shielded_URC20_contract_address", shieldAddress);
      rawBody.put("transparent_to_address", toAddress);
      rawBody.put("to_amount", toAmount.toString());
      rawBody.put("visible", true);

      response = HttpMethed.createConnectForShieldUrc20(requestUrl, rawBody);

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
  public static HttpResponse createShieldContractParametersWithoutAskForBurn(String httpNode,
      JSONObject shieldAccountInfo, JSONArray shieldedSpends, String toAddress, Long toAmount) {
    return createShieldContractParametersWithoutAskForBurn(httpNode, shieldAccountInfo,
        shieldedSpends, toAddress, toAmount, null);
  }

  /**
   * constructor.
   */
  public static HttpResponse createShieldContractParametersWithoutAskForBurn(String httpNode,
      JSONObject shieldAccountInfo, JSONArray shieldedSpends, String toAddress, Long toAmount,
      JSONArray shieldedReceiver) {
    try {
      final String requestUrl
          = "http://" + httpNode + "/wallet/createshieldedcontractparameterswithoutask";

      JSONObject rawBody = new JSONObject();
      rawBody.put("ovk", shieldAccountInfo.getString("ovk"));
      rawBody.put("ak", shieldAccountInfo.getString("ak"));
      rawBody.put("nsk", shieldAccountInfo.getString("nsk"));
      rawBody.put("shielded_spends", shieldedSpends);
      rawBody.put("shielded_URC20_contract_address", shieldAddress);
      rawBody.put("transparent_to_address", toAddress);
      rawBody.put("to_amount", toAmount.toString());
      rawBody.put("visible", true);
      if (shieldedReceiver != null) {
        rawBody.put("shielded_receives", shieldedReceiver);
      }

      response = HttpMethed.createConnectForShieldUrc20(requestUrl, rawBody);

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
  public static HttpResponse createShieldContractParametersForTransfer(String httpNode,
      JSONObject shieldAccountInfo, JSONArray shieldedSpends, JSONArray shieldedReceives) {
    try {
      final String requestUrl = "http://" + httpNode + "/wallet/createshieldedcontractparameters";
      JSONObject rawBody = new JSONObject();
      rawBody.put("ovk", shieldAccountInfo.getString("ovk"));
      rawBody.put("ask", shieldAccountInfo.getString("ask"));
      rawBody.put("nsk", shieldAccountInfo.getString("nsk"));
      rawBody.put("shielded_spends", shieldedSpends);
      rawBody.put("shielded_URC20_contract_address", shieldAddress);
      rawBody.put("shielded_receives", shieldedReceives);
      rawBody.put("visible", true);
      logger.info(rawBody.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, rawBody);
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
  public static HttpResponse createShieldContractParametersWithoutAskForTransfer(String httpNode,
      JSONObject shieldAccountInfo, JSONArray shieldedSpends, JSONArray shieldedReceives) {
    try {
      final String requestUrl = "http://" + httpNode
          + "/wallet/createshieldedcontractparameterswithoutask";
      JSONObject rawBody = new JSONObject();
      rawBody.put("ovk", shieldAccountInfo.getString("ovk"));
      rawBody.put("ak", shieldAccountInfo.getString("ak"));
      rawBody.put("nsk", shieldAccountInfo.getString("nsk"));
      rawBody.put("shielded_spends", shieldedSpends);
      rawBody.put("shielded_URC20_contract_address", shieldAddress);
      rawBody.put("shielded_receives", shieldedReceives);
      rawBody.put("visible", true);
      logger.info(rawBody.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, rawBody);
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
  public static JSONObject createSpendAuthSig(String httpNode,
      JSONObject shieldAccountInfo, String messageHash, String alpha) {
    try {
      final String requestUrl = "http://" + httpNode + "/wallet/createspendauthsig";
      JSONObject rawBody = new JSONObject();
      rawBody.put("ask", shieldAccountInfo.getString("ask"));
      rawBody.put("tx_hash", messageHash);
      rawBody.put("alpha", alpha);
      logger.info("createSpendAuthSig:" + rawBody.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, rawBody);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    return HttpMethed.parseResponseContent(response);
  }


  /**
   * constructor.
   */
  public static JSONArray scanShieldUrc20NoteByIvk(String httpNode,
      JSONObject shieldAddressInfo) {
    try {
      Long endScanNumber = HttpMethed.getNowBlockNum(httpNode);
      Long startScanNumer = endScanNumber > 99 ? endScanNumber - 90 : 1;

      final String requestUrl = "http://" + httpNode + "/wallet/scanshieldedurc20notesbyivk";
      JsonObject userBaseObj2 = new JsonObject();
      userBaseObj2.addProperty("start_block_index", startScanNumer);
      userBaseObj2.addProperty("end_block_index", endScanNumber);
      userBaseObj2.addProperty("shielded_URC20_contract_address", shieldAddress);
      userBaseObj2.addProperty("ivk", shieldAddressInfo.getString("ivk"));
      userBaseObj2.addProperty("ak", shieldAddressInfo.getString("ak"));
      userBaseObj2.addProperty("nk", shieldAddressInfo.getString("nk"));
      userBaseObj2.addProperty("visible", true);
      logger.info("scanShieldUrc20NoteByIvk:" + userBaseObj2.toString());
      response = HttpMethed.createConnect(requestUrl, userBaseObj2);

      responseContent = HttpMethed.parseResponseContent(response);
      HttpMethed.printJsonContent(responseContent);
      JSONArray jsonArray = responseContent.getJSONArray("noteTxs");

      return jsonArray;
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
  }


  /**
   * constructor.
   */
  public static JSONArray scanShieldUrc20NoteByIvkOnSolidity(String httpNode,
      JSONObject shieldAddressInfo) {
    try {
      Long endScanNumber = HttpMethed.getNowBlockNumOnSolidity(httpNode);
      Long startScanNumer = endScanNumber > 99 ? endScanNumber - 90 : 1;

      final String requestUrl =
          "http://" + httpNode + "/walletsolidity/scanshieldedurc20notesbyivk";
      JsonObject userBaseObj2 = new JsonObject();
      userBaseObj2.addProperty("start_block_index", startScanNumer);
      userBaseObj2.addProperty("end_block_index", endScanNumber);
      userBaseObj2.addProperty("shielded_URC20_contract_address", shieldAddress);
      userBaseObj2.addProperty("ivk", shieldAddressInfo.getString("ivk"));
      userBaseObj2.addProperty("ak", shieldAddressInfo.getString("ak"));
      userBaseObj2.addProperty("nk", shieldAddressInfo.getString("nk"));
      userBaseObj2.addProperty("visible", true);
      logger.info("scanShieldUrc20NoteByIvk:" + userBaseObj2.toString());
      response = HttpMethed.createConnect(requestUrl, userBaseObj2);

      responseContent = HttpMethed.parseResponseContent(response);
      HttpMethed.printJsonContent(responseContent);
      JSONArray jsonArray = responseContent.getJSONArray("noteTxs");

      return jsonArray;
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
  }

  /**
   * constructor.
   */
  public static JSONArray scanShieldUrc20NoteByIvkOnPbft(String httpPbftNode,
                                                             JSONObject shieldAddressInfo) {
    try {

      response = HttpMethed.getNowBlockFromPbft(httpPbftNode);
      Long endScanNumber = HttpMethed.parseResponseContent(response).getJSONObject("block_header")
          .getJSONObject("raw_data").getLong("number");
      Long startScanNumer = endScanNumber > 99 ? endScanNumber - 90 : 1;

      final String requestUrl =
              "http://" + httpPbftNode + "/walletpbft/scanshieldedurc20notesbyivk";
      JsonObject userBaseObj2 = new JsonObject();
      userBaseObj2.addProperty("start_block_index", startScanNumer);
      userBaseObj2.addProperty("end_block_index", endScanNumber);
      userBaseObj2.addProperty("shielded_URC20_contract_address", shieldAddress);
      userBaseObj2.addProperty("ivk", shieldAddressInfo.getString("ivk"));
      userBaseObj2.addProperty("ak", shieldAddressInfo.getString("ak"));
      userBaseObj2.addProperty("nk", shieldAddressInfo.getString("nk"));
      userBaseObj2.addProperty("visible", true);
      logger.info("scanShieldUrc20NoteByIvk:" + userBaseObj2.toString());
      response = HttpMethed.createConnect(requestUrl, userBaseObj2);

      responseContent = HttpMethed.parseResponseContent(response);
      HttpMethed.printJsonContent(responseContent);
      JSONArray jsonArray = responseContent.getJSONArray("noteTxs");

      return jsonArray;
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
  }


  /**
   * constructor.
   */
  public static JSONArray scanShieldUrc20NoteByOvk(String httpNode,
      JSONObject shieldAddressInfo) {
    try {
      Long endScanNumber = HttpMethed.getNowBlockNum(httpNode);
      Long startScanNumer = endScanNumber > 99 ? endScanNumber - 90 : 1;

      final String requestUrl = "http://" + httpNode + "/wallet/scanshieldedurc20notesbyovk";
      JsonObject userBaseObj2 = new JsonObject();
      userBaseObj2.addProperty("start_block_index", startScanNumer);
      userBaseObj2.addProperty("end_block_index", endScanNumber);
      userBaseObj2.addProperty("shielded_URC20_contract_address", shieldAddress);
      userBaseObj2.addProperty("ovk", shieldAddressInfo.getString("ovk"));
      userBaseObj2.addProperty("visible", true);
      logger.info("userBaseObj2:" + userBaseObj2.toString());
      response = HttpMethed.createConnect(requestUrl, userBaseObj2);

      responseContent = HttpMethed.parseResponseContent(response);
      HttpMethed.printJsonContent(responseContent);
      JSONArray jsonArray = responseContent.getJSONArray("noteTxs");

      return jsonArray;
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
  }


  /**
   * constructor.
   */
  public static JSONArray scanShieldUrc20NoteByOvkOnSolidity(String httpNode,
      JSONObject shieldAddressInfo) {
    try {
      Long endScanNumber = HttpMethed.getNowBlockNumOnSolidity(httpNode);
      Long startScanNumer = endScanNumber > 99 ? endScanNumber - 90 : 1;

      final String requestUrl =
          "http://" + httpNode + "/walletsolidity/scanshieldedurc20notesbyovk";
      JsonObject userBaseObj2 = new JsonObject();
      userBaseObj2.addProperty("start_block_index", startScanNumer);
      userBaseObj2.addProperty("end_block_index", endScanNumber);
      userBaseObj2.addProperty("shielded_URC20_contract_address", shieldAddress);
      userBaseObj2.addProperty("ovk", shieldAddressInfo.getString("ovk"));
      userBaseObj2.addProperty("visible", true);
      logger.info("userBaseObj2:" + userBaseObj2.toString());
      response = HttpMethed.createConnect(requestUrl, userBaseObj2);

      responseContent = HttpMethed.parseResponseContent(response);
      HttpMethed.printJsonContent(responseContent);
      JSONArray jsonArray = responseContent.getJSONArray("noteTxs");

      return jsonArray;
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
  }

  /**
   * constructor.
   */
  public static JSONArray scanShieldUrc20NoteByOvkOnPbft(String httpPbftNode,
                                                             JSONObject shieldAddressInfo) {
    try {
      response = HttpMethed.getNowBlockFromPbft(httpPbftNode);
      Long endScanNumber = HttpMethed.parseResponseContent(response).getJSONObject("block_header")
          .getJSONObject("raw_data").getLong("number");
      Long startScanNumer = endScanNumber > 99 ? endScanNumber - 90 : 1;

      final String requestUrl =
              "http://" + httpPbftNode + "/walletpbft/scanshieldedurc20notesbyovk";
      JsonObject userBaseObj2 = new JsonObject();
      userBaseObj2.addProperty("start_block_index", startScanNumer);
      userBaseObj2.addProperty("end_block_index", endScanNumber);
      userBaseObj2.addProperty("shielded_URC20_contract_address", shieldAddress);
      userBaseObj2.addProperty("ovk", shieldAddressInfo.getString("ovk"));
      userBaseObj2.addProperty("visible", true);
      logger.info("userBaseObj2:" + userBaseObj2.toString());
      response = HttpMethed.createConnect(requestUrl, userBaseObj2);

      responseContent = HttpMethed.parseResponseContent(response);
      HttpMethed.printJsonContent(responseContent);
      JSONArray jsonArray = responseContent.getJSONArray("noteTxs");

      return jsonArray;
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
  }

  /**
   * constructor.
   */
  public static String getRootAndPathByHttp(String httpNode, Integer position) {
    try {
      final String requestUrl = "http://" + httpNode + "/wallet/triggerconstantcontract";
      JsonObject userBaseObj2 = new JsonObject();

      userBaseObj2.addProperty("owner_address", zenUrc20TokenOwnerAddressString);
      userBaseObj2.addProperty("contract_address", shieldAddress);
      userBaseObj2.addProperty("function_selector", "getPath(uint256)");
      byte[] indexBytes = ByteArray.fromLong(position);
      String argsStr = ByteArray.toHexString(indexBytes);
      String parameter = "000000000000000000000000000000000000000000000000" + argsStr;
      userBaseObj2.addProperty("parameter", parameter);
      userBaseObj2.addProperty("fee_limit", maxFeeLimit);
      userBaseObj2.addProperty("visible", true);

      response = HttpMethed.createConnect(requestUrl, userBaseObj2);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    return HttpMethed.parseResponseContent(response).getJSONArray("constant_result").getString(0);
  }

  /**
   * constructor.
   */
  public static JSONArray createAndSetShieldedSpends(String httpNode,
      JSONArray shieldedSpends, JSONObject noteTxs) {
    JSONObject shieldedSpend = new JSONObject();
    shieldedSpend.put("note", noteTxs.getJSONObject("note"));
    shieldedSpend.put("alpha", noteTxs.getJSONObject("note").getString("rcm"));
    Integer position = noteTxs.containsKey("position") ? noteTxs.getInteger("position") : 0;
    String rootAndPath = getRootAndPathByHttp(httpNode, position);
    String root = rootAndPath.substring(0, 64);
    String path = rootAndPath.substring(64);
    shieldedSpend.put("root", root);
    shieldedSpend.put("path", path);
    shieldedSpend.put("pos", position);
    shieldedSpends.add(shieldedSpend);
    return shieldedSpends;
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


  /**
   * constructor.
   */
  public static Boolean isShieldedUrc20ContractNoteSpent(String httpNode,
      JSONObject accountInfo, JSONObject noteTxs) {
    try {
      final String requestUrl = "http://" + httpNode + "/wallet/isshieldedurc20contractnotespent";
      JSONObject userBaseObj2 = new JSONObject();
      userBaseObj2.put("note", noteTxs.getJSONObject("note"));
      userBaseObj2.put("ak", accountInfo.getString("ak"));
      userBaseObj2.put("nk", accountInfo.getString("nk"));
      userBaseObj2.put("position", noteTxs.containsKey("position")
          ? noteTxs.getInteger("position") : 0);
      userBaseObj2.put("visible", true);
      userBaseObj2.put("shielded_URC20_contract_address", shieldAddress);
      logger.info(userBaseObj2.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, userBaseObj2);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    return responseContent.containsKey("is_spent")
        ? responseContent.getBoolean("is_spent") : false;
  }

  /**
   * constructor.
   */
  public static Boolean isShieldedUrc20ContractNoteSpentOnSolidity(String httpNode,
      JSONObject accountInfo, JSONObject noteTxs) {
    try {
      final String requestUrl
          = "http://" + httpNode + "/walletsolidity/isshieldedurc20contractnotespent";
      JSONObject userBaseObj2 = new JSONObject();
      userBaseObj2.put("note", noteTxs.getJSONObject("note"));
      userBaseObj2.put("ak", accountInfo.getString("ak"));
      userBaseObj2.put("nk", accountInfo.getString("nk"));
      userBaseObj2.put("position", noteTxs.containsKey("position")
          ? noteTxs.getInteger("position") : 0);
      userBaseObj2.put("visible", true);
      userBaseObj2.put("shielded_URC20_contract_address", shieldAddress);
      logger.info(userBaseObj2.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, userBaseObj2);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    return responseContent.containsKey("is_spent") ? responseContent.getBoolean("is_spent") : false;
  }

  /**
   * constructor.
   */
  public static Boolean isShieldedUrc20ContractNoteSpentOnPbft(String httpPbftNode,
      JSONObject accountInfo, JSONObject noteTxs) {
    try {
      final String requestUrl
              = "http://" + httpPbftNode + "/walletpbft/isshieldedurc20contractnotespent";
      JSONObject userBaseObj2 = new JSONObject();
      userBaseObj2.put("note", noteTxs.getJSONObject("note"));
      userBaseObj2.put("ak", accountInfo.getString("ak"));
      userBaseObj2.put("nk", accountInfo.getString("nk"));
      userBaseObj2.put("position", noteTxs.containsKey("position")
              ? noteTxs.getInteger("position") : 0);
      userBaseObj2.put("visible", true);
      userBaseObj2.put("shielded_URC20_contract_address", shieldAddress);
      logger.info(userBaseObj2.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, userBaseObj2);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    return responseContent.containsKey("is_spent") ? responseContent.getBoolean("is_spent") : false;
  }

  /**
   * constructor.
   */
  public static HttpResponse getTriggerInputForShieldedUrc20Contract(String httpNode,
      JSONObject shieldedUrc20Parameters, JSONArray spendAuthoritySignature) {
    try {
      final String requestUrl = "http://" + httpNode
          + "/wallet/gettriggerinputforshieldedurc20contract";
      JSONObject userBaseObj2 = new JSONObject();
      userBaseObj2.put("shielded_URC20_Parameters", shieldedUrc20Parameters);
      userBaseObj2.put("spend_authority_signature", spendAuthoritySignature);

      logger.info("gettriggerinputforshieldedurc20contract:" + userBaseObj2.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, userBaseObj2);
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
  public static HttpResponse getTriggerInputForShieldedUrc20BurnContract(String httpNode,
      JSONObject shieldedUrc20Parameters, JSONArray spendAuthoritySignature, Long amount,
      String toAddress) {
    try {
      final String requestUrl = "http://"
          + httpNode + "/wallet/gettriggerinputforshieldedurc20contract";
      JSONObject userBaseObj2 = new JSONObject();
      userBaseObj2.put("shielded_URC20_Parameters", shieldedUrc20Parameters);
      userBaseObj2.put("spend_authority_signature", spendAuthoritySignature);
      userBaseObj2.put("amount", amount.toString());
      userBaseObj2.put("transparent_to_address", toAddress);
      userBaseObj2.put("visible", true);

      logger.info("gettriggerinputforshieldedurc20contract:" + userBaseObj2.toString());
      response = HttpMethed.createConnectForShieldUrc20(requestUrl, userBaseObj2);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    return response;
  }


}
