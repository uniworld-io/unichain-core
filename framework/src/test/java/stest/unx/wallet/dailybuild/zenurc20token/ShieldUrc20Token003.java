package stest.unx.wallet.dailybuild.zenurc20token;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.Note;
import org.unx.api.WalletGrpc;
import org.unx.api.WalletSolidityGrpc;
import org.unx.protos.Protocol.TransactionInfo;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.PublicMethed;
import stest.unx.wallet.common.client.utils.ShieldedAddressInfo;
import stest.unx.wallet.common.client.utils.ZenUrc20Base;

@Slf4j
public class ShieldUrc20Token003 extends ZenUrc20Base {

  private String fullnode = Configuration.getByPath("testng.conf")
      .getStringList("fullnode.ip.list").get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);
  Optional<ShieldedAddressInfo> senderShieldAddressInfo;
  Optional<ShieldedAddressInfo> receiverShieldAddressInfo;
  private BigInteger publicFromAmount;
  List<Note> shieldOutList = new ArrayList<>();
  List<ShieldedAddressInfo> inputShieldAddressList = new ArrayList<>();
  GrpcAPI.DecryptNotesURC20 senderNote;
  GrpcAPI.DecryptNotesURC20 receiverNote;
  long senderPosition;

  /**
   * constructor.
   */
  @BeforeClass(enabled = true)
  public void beforeClass() throws Exception {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
    publicFromAmount = getRandomAmount();

    //Generate new shiled account for sender and receiver
    senderShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    receiverShieldAddressInfo = getNewShieldedAddress(blockingStubFull);
    String memo = "Create a note for transfer test " + System.currentTimeMillis();
    String sendShieldAddress = senderShieldAddressInfo.get().getAddress();
    shieldOutList.clear();
    shieldOutList = addShieldUrc20OutputList(shieldOutList, sendShieldAddress,
        "" + publicFromAmount, memo, blockingStubFull);
    //Create mint parameters
    GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters
        = createShieldedUrc20Parameters(publicFromAmount,
        null, null, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity);
    String data = encodeMintParamsToHexString(shieldedUrc20Parameters, publicFromAmount);
    //Do mint transaction type
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        mint, data, true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
        zenUrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);

    //Scan sender note
    senderNote = scanShieldedUrc20NoteByIvk(senderShieldAddressInfo.get(),
        blockingStubFull);
    Assert.assertEquals(senderNote.getNoteTxs(0).getIsSpent(), false);
    logger.info("" + senderNote);
    senderPosition = senderNote.getNoteTxs(0).getPosition();
    Assert.assertEquals(senderNote.getNoteTxs(0).getNote().getValue(),
        publicFromAmount.longValue());


  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Shield URC20 transaction with type transfer")
  public void test01ShieldUrc20TransactionWithTypeTransfer() throws Exception {
    final Long beforeMintShieldContractBalance = getBalanceOfShieldUrc20(shieldAddress,
        zenUrc20TokenOwnerAddress, zenUrc20TokenOwnerKey, blockingStubFull);

    String transferMemo = "Transfer type test " + System.currentTimeMillis();
    String receiverShieldAddress = receiverShieldAddressInfo.get().getAddress();
    shieldOutList.clear();
    shieldOutList = addShieldUrc20OutputList(shieldOutList, receiverShieldAddress,
        "" + publicFromAmount, transferMemo, blockingStubFull);
    inputShieldAddressList.add(senderShieldAddressInfo.get());
    //inputNoteList.add(senderNote);
    //Create transfer parameters
    GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters
        = createShieldedUrc20Parameters(BigInteger.valueOf(0),
        senderNote, inputShieldAddressList, shieldOutList, "", 0L,
        blockingStubFull, blockingStubSolidity);

    String data = encodeTransferParamsToHexString(shieldedUrc20Parameters);
    //String data = shieldedUrc20Parameters.getTriggerContractInput();
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        transfer, data, true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
        zenUrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() > 300000);

    //Scan sender note
    receiverNote = scanShieldedUrc20NoteByIvk(receiverShieldAddressInfo.get(),
        blockingStubFull);

    logger.info("" + receiverNote);
    Assert.assertEquals(receiverNote.getNoteTxs(0).getTxid(), infoById.get().getId());
    Assert.assertEquals(receiverNote.getNoteTxs(0).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo));
    Assert.assertEquals(receiverNote.getNoteTxs(0).getNote().getValue(),
        publicFromAmount.longValue());
    Assert.assertEquals(receiverNote.getNoteTxs(0).getNote().getPaymentAddress(),
        receiverShieldAddressInfo.get().getAddress());

    logger.info("scanShieldedUrc20NoteByIvk + senderNote:" + senderNote);
    senderNote = scanShieldedUrc20NoteByIvk(senderShieldAddressInfo.get(),
        blockingStubFull);
    Assert.assertEquals(senderNote.getNoteTxs(0).getIsSpent(), true);

    senderNote = scanShieldedUrc20NoteByOvk(senderShieldAddressInfo.get(),
        blockingStubFull);
    logger.info("scanShieldedUrc20NoteByOvk + senderNote:" + senderNote);

    final Long afterMintShieldContractBalance = getBalanceOfShieldUrc20(shieldAddress,
        zenUrc20TokenOwnerAddress, zenUrc20TokenOwnerKey, blockingStubFull);
    Assert.assertEquals(beforeMintShieldContractBalance, afterMintShieldContractBalance);
  }

  /**
   * constructor.
   */
  @Test(enabled = true, description = "Shield URC20 transaction with type transfer without ask")
  public void test02ShieldUrc20TransactionWithTypeTransferWithoutAsk() throws Exception {
    PublicMethed.waitSolidityNodeSynFullNodeData(blockingStubFull, blockingStubSolidity);
    //Scan receiver note prepare for without type of transfer
    receiverNote = scanShieldedUrc20NoteByIvk(receiverShieldAddressInfo.get(),
        blockingStubFull);
    String transferMemo = "Transfer type without ask test " + System.currentTimeMillis();
    shieldOutList.clear();
    shieldOutList = addShieldUrc20OutputList(shieldOutList, senderShieldAddressInfo.get()
            .getAddress(),
        "" + publicFromAmount, transferMemo, blockingStubFull);
    inputShieldAddressList.clear();
    inputShieldAddressList.add(receiverShieldAddressInfo.get());

    //Create transfer parameters
    GrpcAPI.ShieldedURC20Parameters shieldedUrc20Parameters
        = createShieldedUrc20ParametersWithoutAsk(BigInteger.valueOf(0),
        receiverNote, inputShieldAddressList, shieldOutList, "", null, 0L,
        blockingStubFull, blockingStubSolidity);

    String data = encodeTransferParamsToHexString(shieldedUrc20Parameters);
    String txid = PublicMethed.triggerContract(shieldAddressByte,
        transfer, data, true, 0, maxFeeLimit, zenUrc20TokenOwnerAddress,
        zenUrc20TokenOwnerKey, blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    Optional<TransactionInfo> infoById = PublicMethed
        .getTransactionInfoById(txid, blockingStubFull);
    Assert.assertTrue(infoById.get().getReceipt().getResultValue() == 1);
    Assert.assertTrue(infoById.get().getReceipt().getEnergyUsageTotal() > 300000);

    senderNote = scanShieldedUrc20NoteByIvk(senderShieldAddressInfo.get(),
        blockingStubFull);

    logger.info("" + senderNote);
    Assert.assertEquals(senderNote.getNoteTxs(1).getTxid(), infoById.get().getId());
    Assert.assertEquals(senderNote.getNoteTxs(1).getNote().getMemo(),
        ByteString.copyFromUtf8(transferMemo));
    Assert.assertEquals(senderNote.getNoteTxs(1).getNote().getValue(),
        publicFromAmount.longValue());
    Assert.assertEquals(senderNote.getNoteTxs(1).getNote().getPaymentAddress(),
        senderShieldAddressInfo.get().getAddress());

    //logger.info("scanShieldedUrc20NoteByIvk + senderNote:" + senderNote);
    receiverNote = scanShieldedUrc20NoteByIvk(receiverShieldAddressInfo.get(),
        blockingStubFull);
    Assert.assertEquals(receiverNote.getNoteTxs(0).getIsSpent(), true);
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


