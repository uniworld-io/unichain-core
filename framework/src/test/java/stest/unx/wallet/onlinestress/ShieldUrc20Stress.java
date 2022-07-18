package stest.unx.wallet.onlinestress;

import com.alibaba.fastjson.JSONObject;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.Note;
import org.unx.api.WalletGrpc;
import org.unx.api.WalletSolidityGrpc;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.HttpMethed;
import stest.unx.wallet.common.client.utils.PublicMethed;
import stest.unx.wallet.common.client.utils.ShieldedAddressInfo;
import stest.unx.wallet.common.client.utils.ZenUrc20Base;

@Slf4j
public class ShieldUrc20Stress extends ZenUrc20Base {

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String fullnode1 = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(1);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  Optional<ShieldedAddressInfo> sendShieldAddressInfo;
  private BigInteger publicFromAmount;
  List<Note> shieldOutList = new ArrayList<>();

  private String httpnode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(0);


  private AtomicInteger finishMintNumber = new AtomicInteger(0);
  private AtomicInteger finishCreateParameterNumber = new AtomicInteger(0);
  private AtomicInteger finishTriggerNumber = new AtomicInteger(0);
  private AtomicInteger noteNumber = new AtomicInteger(0);
  private AtomicInteger dataNumber = new AtomicInteger(0);
  private AtomicLong startTriggerNum = new AtomicLong(0);
  private AtomicLong endTriggerNum = new AtomicLong(0);
  private AtomicLong startmintNum = new AtomicLong(0);
  private AtomicLong endmintNum = new AtomicLong(0);
  private Integer thread = 40;

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);

    publicFromAmount = getRandomAmount();
    //startQureyNum = HttpMethed.getNowBlockNum(httpnode);
    startmintNum.addAndGet(blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber());
  }

  /**
   * wqs constructor.
   */
  @Test(enabled = false, threadPoolSize = 50, invocationCount = 50)
  public void test01ScanAndCreateThenTrigger() throws Exception {
    ManagedChannel channelFull = null;
    WalletGrpc.WalletBlockingStub blockingStubFull = null;
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    ManagedChannel channelFull1 = null;
    WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    BigInteger publicFromAmount = getRandomAmount();
    Optional<ShieldedAddressInfo> sendShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    Optional<ShieldedAddressInfo> receiverShieldAddressInfo = getNewShieldedAddress(
        blockingStubFull);
    String memo = "Shield urc20 from T account to shield account in" + System.currentTimeMillis();
    String sendShieldAddress = sendShieldAddressInfo.get().getAddress();

    List<Note> shieldOutList = new ArrayList<>();
    shieldOutList.clear();
    shieldOutList = addShieldUrc20OutputList(shieldOutList, sendShieldAddress,
        "" + publicFromAmount, memo, blockingStubFull);

    //Create shiled urc20 parameters
    GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters
        = createShieldedUrc20Parameters(publicFromAmount,
        null, null, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity
    );

    String data = encodeMintParamsToHexString(shieldedUrc20Parameters, publicFromAmount);

    //Do mint transaction type
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        mint, data, true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
        zenUrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    sendShieldAddress = sendShieldAddressInfo.get().getAddress();
    List<ShieldedAddressInfo> inputShieldAddressList = new ArrayList<>();
    GrpcAPI.DecryptNotesURC20 sendNote;
    List<GrpcAPI.DecryptNotesURC20> inputList = new ArrayList<>();
    inputShieldAddressList.add(sendShieldAddressInfo.get());

    sendNote = scanShieldedUrc20NoteByIvk(sendShieldAddressInfo.get(),
        blockingStubFull1);

    while (sendNote.getNoteTxsCount() == 0) {
      sendNote = scanShieldedUrc20NoteByIvk(sendShieldAddressInfo.get(),
          blockingStubFull1);
    }

    Integer times = 20;
    while (times-- > 0) {
      //receiverShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
      //Scan sender note
      /*sendNote = scanShieldedUrc20NoteByIvk(sendShieldAddressInfo.get(),
          blockingStubFull1);

      while (sendNote.getNoteTxsCount() == 0) {
        sendNote = scanShieldedUrc20NoteByIvk(sendShieldAddressInfo.get(),
            blockingStubFull1);
      }*/

      sendNote = scanShieldedUrc20NoteByIvk(sendShieldAddressInfo.get(),
          blockingStubFull1);

      String transferMemo = "Transfer type test " + System.currentTimeMillis();

      shieldOutList.clear();
      shieldOutList = addShieldUrc20OutputList(shieldOutList, sendShieldAddress,
          "" + publicFromAmount, transferMemo, blockingStubFull);

      //logger.info("send note size:" + sendNote.getNoteTxsCount());

      //Create transfer parameters
      try {
        GrpcAPI.DecryptNotesURC20 inputNoteFor2to2 = GrpcAPI.DecryptNotesURC20.newBuilder()
            .addNoteTxs(sendNote.getNoteTxs(sendNote.getNoteTxsCount() - 1)).build();
        shieldedUrc20Parameters
            = createShieldedUrc20Parameters(BigInteger.valueOf(0),
            inputNoteFor2to2, inputShieldAddressList, shieldOutList, "", 0L,
            blockingStubFull1, blockingStubSolidity);
      } catch (Exception e) {
        throw e;
      }

      Integer exit = 7;
      if (exit == 1) {
        continue;
      }

      data = encodeTransferParamsToHexString(shieldedUrc20Parameters);
      txid = PublicMethed.triggerContract(shieldAddressByte,
          transfer, data, true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
          zenUrc20TokenOwnerKey, blockingStubFull);

      //sendShieldAddressInfo = receiverShieldAddressInfo;
    }

  }


  /**
   * constructor.
   */
  @Test(enabled = false, threadPoolSize = 40, invocationCount = 40)
  public void test02FirstScanCreateParameterThenCreateTrigger() throws Exception {
    ManagedChannel channelFull = null;
    WalletGrpc.WalletBlockingStub blockingStubFull = null;
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    ManagedChannel channelFull1 = null;
    WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    Optional<ShieldedAddressInfo> sendShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    Optional<ShieldedAddressInfo> receiverShieldAddressInfo = getNewShieldedAddress(
        blockingStubFull);

    Integer mintNumber = 50;

    while (--mintNumber >= 0) {
      BigInteger publicFromAmount = getRandomAmount();

      String memo = "Shield urc20 from T account to shield account in" + System.currentTimeMillis();
      String sendShieldAddress = sendShieldAddressInfo.get().getAddress();

      List<Note> shieldOutList = new ArrayList<>();
      shieldOutList.clear();
      shieldOutList = addShieldUrc20OutputList(shieldOutList, sendShieldAddress,
          "" + publicFromAmount, memo, blockingStubFull);

      //Create shiled urc20 parameters
      GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters
          = createShieldedUrc20Parameters(publicFromAmount,
          null, null, shieldOutList, "",
          0L, blockingStubFull, blockingStubSolidity
      );
      String data = "";
      try {
        data = encodeMintParamsToHexString(shieldedUrc20Parameters, publicFromAmount);
      } catch (Exception e) {
        try {
          data = encodeMintParamsToHexString(shieldedUrc20Parameters, publicFromAmount);
        } catch (Exception e1) {
          continue;
        }

      }

      String txid = PublicMethed.triggerContract(shieldAddressByte,
          mint, data, true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
          zenUrc20TokenOwnerKey, blockingStubFull);
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    finishMintNumber.addAndGet(1);
    endmintNum.getAndAdd(blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber());

    while (finishMintNumber.get() != thread) {
      try {
        Thread.sleep(3000);
        if (finishMintNumber.get() % 10 == 0) {
          logger.info(
              "Wait all thread finished mint,current finished thread is :" + finishMintNumber
                  .get());
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    Long endMintNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber();

    GrpcAPI.DecryptNotesURC20 sendNote = scanShieldedUrc20NoteByIvkWithRange(
        sendShieldAddressInfo.get(),
        startmintNum.get(), endMintNum, blockingStubFull1);

    noteNumber.addAndGet(sendNote.getNoteTxsCount());

    logger.info("sendNote size :" + sendNote.getNoteTxsCount());

    List<Note> shieldOutList = new ArrayList<>();

    List<ShieldedAddressInfo> inputShieldAddressList = new ArrayList<>();

    inputShieldAddressList.add(sendShieldAddressInfo.get());

    List<String> dataList = new ArrayList<>();
    for (int i = 0; i < sendNote.getNoteTxsCount() - 1; i = i + 2) {
      GrpcAPI.DecryptNotesURC20 inputNoteFor2to2 = GrpcAPI.DecryptNotesURC20.newBuilder()
          .addNoteTxs(sendNote.getNoteTxs(i))
          .addNoteTxs(sendNote.getNoteTxs(i + 1))
          .build();

      String transferMemo1 = "Transfer1 type test " + getRandomLongAmount() + getRandomLongAmount();
      String transferMemo2 = "Transfer2 type test " + getRandomLongAmount() + getRandomLongAmount();
      shieldOutList.clear();
      shieldOutList = addShieldUrc20OutputList(shieldOutList,
          receiverShieldAddressInfo.get().getAddress(),
          "" + sendNote.getNoteTxs(i).getNote().getValue(), transferMemo1, blockingStubFull);
      shieldOutList = addShieldUrc20OutputList(shieldOutList,
          receiverShieldAddressInfo.get().getAddress(),
          "" + sendNote.getNoteTxs(i + 1).getNote().getValue(), transferMemo2, blockingStubFull);

      GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters = null;
      if (i % 2 == 0) {
        try {
          shieldedUrc20Parameters
              = createShieldedUrc20Parameters(BigInteger.valueOf(0),
              inputNoteFor2to2, inputShieldAddressList, shieldOutList, "",
              0L, blockingStubFull1, blockingStubSolidity);
        } catch (Exception e) {
          try {
            shieldedUrc20Parameters
                = createShieldedUrc20Parameters(BigInteger.valueOf(0),
                inputNoteFor2to2, inputShieldAddressList, shieldOutList, "",
                0L, blockingStubFull, blockingStubSolidity);
          } catch (Exception e1) {
            throw e1;
          }

        }

      } else {
        try {
          shieldedUrc20Parameters
              = createShieldedUrc20Parameters(BigInteger.valueOf(0),
              inputNoteFor2to2, inputShieldAddressList, shieldOutList, "",
              0L, blockingStubFull, blockingStubSolidity);
        } catch (Exception e) {
          try {
            shieldedUrc20Parameters
                = createShieldedUrc20Parameters(BigInteger.valueOf(0),
                inputNoteFor2to2, inputShieldAddressList, shieldOutList, "",
                0L, blockingStubFull1, blockingStubSolidity);
          } catch (Exception e2) {
            throw e2;
          }


        }
      }

      dataList.add(encodeTransferParamsToHexString(shieldedUrc20Parameters));
      //logger.info("dataList size:" + dataList.size());

    }

    finishCreateParameterNumber.addAndGet(1);
    dataNumber.addAndGet(dataList.size());
    while (finishCreateParameterNumber.get() != thread) {
      try {
        Thread.sleep(3000);
        if (finishCreateParameterNumber.get() % 10 == 0) {
          logger.info("Wait all thread finished create parameter ,current finished thread is :"
              + finishCreateParameterNumber.get());
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    startTriggerNum
        .addAndGet(blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
            .getBlockHeader().getRawData().getNumber());

    for (int i = 0; i < dataList.size(); i++) {
      if (i % 2 == 0) {
        PublicMethed.triggerContract(shieldAddressByte,
            transfer, dataList.get(i), true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
            zenUrc20TokenOwnerKey, blockingStubFull);
      } else {
        PublicMethed.triggerContract(shieldAddressByte,
            transfer, dataList.get(i), true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
            zenUrc20TokenOwnerKey, blockingStubFull1);
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    finishTriggerNumber.addAndGet(1);

    while (finishTriggerNumber.get() != thread) {
      try {
        Thread.sleep(3000);
        if (finishTriggerNumber.get() % 10 == 0) {
          logger.info(
              "Wait all thread finished trigger ,current finished thread is :" + finishTriggerNumber
                  .get());
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }


  }

  /**
   * constructor.
   */
  @Test(enabled = true, threadPoolSize = 40, invocationCount = 40)
  public void test03BurnStress() throws Exception {
    ManagedChannel channelFull = null;
    WalletGrpc.WalletBlockingStub blockingStubFull = null;
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    ManagedChannel channelFull1 = null;
    WalletGrpc.WalletBlockingStub blockingStubFull1 = null;
    channelFull1 = ManagedChannelBuilder.forTarget(fullnode1)
        .usePlaintext(true)
        .build();
    blockingStubFull1 = WalletGrpc.newBlockingStub(channelFull1);

    Optional<ShieldedAddressInfo> sendShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    Optional<ShieldedAddressInfo> receiverShieldAddressInfo = getNewShieldedAddress(
        blockingStubFull);

    Integer mintNumber = 25;

    while (--mintNumber >= 0) {
      BigInteger publicFromAmount = getRandomAmount();

      String memo = "Shield urc20 from T account to shield account in" + System.currentTimeMillis();
      String sendShieldAddress = sendShieldAddressInfo.get().getAddress();

      List<Note> shieldOutList = new ArrayList<>();
      shieldOutList.clear();
      shieldOutList = addShieldUrc20OutputList(shieldOutList, sendShieldAddress,
          "" + publicFromAmount, memo, blockingStubFull);

      //Create shiled urc20 parameters
      GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters
          = createShieldedUrc20Parameters(publicFromAmount,
          null, null, shieldOutList, "",
          0L, blockingStubFull, blockingStubSolidity
      );
      String data = "";
      try {
        data = encodeMintParamsToHexString(shieldedUrc20Parameters, publicFromAmount);
      } catch (Exception e) {
        try {
          data = encodeMintParamsToHexString(shieldedUrc20Parameters, publicFromAmount);
        } catch (Exception e1) {
          continue;
        }

      }

      String txid = PublicMethed.triggerContract(shieldAddressByte,
          mint, data, true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
          zenUrc20TokenOwnerKey, blockingStubFull);
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    finishMintNumber.addAndGet(1);
    endmintNum.getAndAdd(blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber());

    while (finishMintNumber.get() != thread) {
      try {
        Thread.sleep(3000);
        if (finishMintNumber.get() % 10 == 0) {
          logger.info(
              "Wait all thread finished mint,current finished thread is :" + finishMintNumber
                  .get());
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);

    Long endMintNum = blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber();

    GrpcAPI.DecryptNotesURC20 sendNote = scanShieldedUrc20NoteByIvkWithRange(
        sendShieldAddressInfo.get(),
        startmintNum.get(), endMintNum, blockingStubFull1);

    noteNumber.addAndGet(sendNote.getNoteTxsCount());

    logger.info("sendNote size :" + sendNote.getNoteTxsCount());

    List<Note> shieldOutList = new ArrayList<>();

    List<ShieldedAddressInfo> inputShieldAddressList = new ArrayList<>();

    inputShieldAddressList.add(sendShieldAddressInfo.get());

    List<String> dataList = new ArrayList<>();
    for (int i = 0; i < sendNote.getNoteTxsCount(); i++) {
      String burnnMemo1 = "burnnMemo1 type test " + getRandomLongAmount() + getRandomLongAmount();
      GrpcAPI.DecryptNotesURC20 burnInput = GrpcAPI.DecryptNotesURC20.newBuilder()
          .addNoteTxs(sendNote.getNoteTxs(i))
          .build();

      GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters = null;
      createShieldedUrc20Parameters(BigInteger.valueOf(0),
          burnInput, inputShieldAddressList, null, zenUrc20TokenOwnerAddressString,
          burnInput.getNoteTxs(0).getNote().getValue(), blockingStubFull, blockingStubSolidity);

      if (i % 2 == 0) {
        try {
          shieldedUrc20Parameters = createShieldedUrc20Parameters(BigInteger.valueOf(0),
              burnInput, inputShieldAddressList, null, zenUrc20TokenOwnerAddressString,
              burnInput.getNoteTxs(0).getNote().getValue(), blockingStubFull, blockingStubSolidity);
        } catch (Exception e) {
          try {
            shieldedUrc20Parameters = createShieldedUrc20Parameters(BigInteger.valueOf(0),
                burnInput, inputShieldAddressList, null, zenUrc20TokenOwnerAddressString,
                burnInput.getNoteTxs(0).getNote().getValue(), blockingStubFull1,
                blockingStubSolidity);
          } catch (Exception e1) {
            throw e1;
          }

        }

      } else {
        try {
          shieldedUrc20Parameters = createShieldedUrc20Parameters(BigInteger.valueOf(0),
              burnInput, inputShieldAddressList, null, zenUrc20TokenOwnerAddressString,
              burnInput.getNoteTxs(0).getNote().getValue(), blockingStubFull, blockingStubSolidity);
        } catch (Exception e) {
          try {
            shieldedUrc20Parameters = createShieldedUrc20Parameters(BigInteger.valueOf(0),
                burnInput, inputShieldAddressList, null, zenUrc20TokenOwnerAddressString,
                burnInput.getNoteTxs(0).getNote().getValue(), blockingStubFull1,
                blockingStubSolidity);
          } catch (Exception e1) {
            throw e1;
          }


        }
      }

      dataList.add(shieldedUrc20Parameters.getTriggerContractInput());

    }

    finishCreateParameterNumber.addAndGet(1);
    dataNumber.addAndGet(dataList.size());
    while (finishCreateParameterNumber.get() != thread) {
      try {
        Thread.sleep(3000);
        if (finishCreateParameterNumber.get() % 10 == 0) {
          logger.info("Wait all thread finished create parameter ,current finished thread is :"
              + finishCreateParameterNumber.get());
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    startTriggerNum
        .addAndGet(blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
            .getBlockHeader().getRawData().getNumber());

    for (int i = 0; i < dataList.size(); i++) {
      if (i % 2 == 0) {
        PublicMethed.triggerContract(shieldAddressByte,
            burn, dataList.get(i), true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
            zenUrc20TokenOwnerKey, blockingStubFull);
      } else {
        PublicMethed.triggerContract(shieldAddressByte,
            burn, dataList.get(i), true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
            zenUrc20TokenOwnerKey, blockingStubFull1);
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    finishTriggerNumber.addAndGet(1);

    while (finishTriggerNumber.get() != thread) {
      try {
        Thread.sleep(3000);
        if (finishTriggerNumber.get() % 10 == 0) {
          logger.info(
              "Wait all thread finished trigger ,current finished thread is :" + finishTriggerNumber
                  .get());
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }


  }

  /**
   * constructor.
   */
  @Test(enabled = true, threadPoolSize = 1, invocationCount = 1)
  public void test04QueryResult() throws Exception {

    endTriggerNum.set(blockingStubFull.getNowBlock(GrpcAPI.EmptyMessage.newBuilder().build())
        .getBlockHeader().getRawData().getNumber());
    Long endmintnum = endmintNum.longValue() / thread;
    Long starttriggernum = startTriggerNum.longValue() / thread;
    logger.info("Start trigger block number: " + starttriggernum);
    logger.info("end   trigger block number: " + endTriggerNum.get());
    logger.info("Start mint block number: " + startmintNum.get());
    logger.info("End   mint block number: " + endmintnum);

    Integer success = 0;
    Integer failed = 0;
    Integer outOfTime = 0;
    Integer notMintContract = 0;
    startmintNum.getAndAdd(-5);
    endmintnum = endmintnum + 5;

    startmintNum.set(2016);
    endmintnum = 2180L;
    starttriggernum = 3040L;
    endTriggerNum.set(3060);

    while (startmintNum.get() < endmintnum) {
      HttpResponse response = HttpMethed
          .getTransactionInfoByBlocknum(httpnode, startmintNum.getAndAdd(1));
      List<JSONObject> responseContentByBlocknum = HttpMethed
          .parseResponseContentArray(response);
      for (int i = 0; i < responseContentByBlocknum.size(); i++) {
        String result = responseContentByBlocknum.get(i).getJSONObject("receipt")
            .getString("result");
        logger.info(result);
        if (result == null) {
          notMintContract++;
          continue;
        }
        if (result.equals("SUCCESS")) {
          success++;
          continue;
        }
        if (result.equals("OUT_OF_TIME")) {
          outOfTime++;
          continue;
        }
        failed++;
      }
    }

    Integer triggerSuccess = 0;
    Integer triggerFailed = 0;
    Integer triggerOutOfTime = 0;
    Integer notTriggerContract = 0;
    startTriggerNum.getAndAdd(-5);
    endTriggerNum.getAndAdd(5);

    while (starttriggernum < endTriggerNum.get()) {
      HttpResponse response = HttpMethed.getTransactionInfoByBlocknum(httpnode, starttriggernum++);
      List<JSONObject> responseContentByBlocknum = HttpMethed
          .parseResponseContentArray(response);
      for (int i = 0; i < responseContentByBlocknum.size(); i++) {
        String result = responseContentByBlocknum.get(i).getJSONObject("receipt")
            .getString("result");
        logger.info(result);
        if (result == null) {
          notTriggerContract++;
          continue;
        }
        if (result.equals("SUCCESS")) {
          triggerSuccess++;
          continue;
        }
        if (result.equals("OUT_OF_TIME")) {
          triggerOutOfTime++;
          continue;
        }
        triggerFailed++;
      }
    }

    logger.info("Mint Success mint times:" + success);
    logger.info("Mint Failed  mint times:" + failed);
    logger.info("Mint Out of times  mint times:" + outOfTime);
    logger.info("Not mint times:" + notMintContract);

    logger.info("Success trigger times:" + triggerSuccess);
    logger.info("Failed  trigger times:" + triggerFailed);
    logger.info("Out of times  trigger times:" + triggerOutOfTime);
    logger.info("Not trigger times:" + notTriggerContract);

    logger.info("note size:" + noteNumber.get());
    logger.info("data size:" + dataNumber.get());


  }

  /**
   * constructor.
   */
  @AfterClass
  public void shutdown() throws InterruptedException {
    //endNum = HttpMethed.getNowBlockNum(httpnode);
    //logger.info("startNum:" + startNum);
    //logger.info("endNum:" + endNum);
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


