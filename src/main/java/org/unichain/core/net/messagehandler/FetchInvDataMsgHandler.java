package org.unichain.core.net.messagehandler;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.overlay.discover.node.statistics.MessageCount;
import org.unichain.common.overlay.message.Message;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.capsule.BlockCapsule.BlockId;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.config.Parameter.NodeConstant;
import org.unichain.core.exception.P2pException;
import org.unichain.core.exception.P2pException.TypeEnum;
import org.unichain.core.net.UnichainNetDelegate;
import org.unichain.core.net.message.*;
import org.unichain.core.net.peer.Item;
import org.unichain.core.net.peer.PeerConnection;
import org.unichain.core.net.service.AdvService;
import org.unichain.core.net.service.SyncService;
import org.unichain.protos.Protocol.Inventory.InventoryType;
import org.unichain.protos.Protocol.ReasonCode;
import org.unichain.protos.Protocol.Transaction;

import java.util.List;

@Slf4j(topic = "net")
@Component
public class FetchInvDataMsgHandler implements UnichainMsgHandler {

  @Autowired
  private UnichainNetDelegate unichainNetDelegate;

  @Autowired
  private SyncService syncService;

  @Autowired
  private AdvService advService;

  private int MAX_SIZE = 1_000_000;

  @Override
  public void processMessage(PeerConnection peer, UnichainMessage msg) throws P2pException {

    FetchInvDataMessage fetchInvDataMsg = (FetchInvDataMessage) msg;

    check(peer, fetchInvDataMsg);

    InventoryType type = fetchInvDataMsg.getInventoryType();
    List<Transaction> transactions = Lists.newArrayList();

    int size = 0;

    for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
      Item item = new Item(hash, type);
      Message message = advService.getMessage(item);
      if (message == null) {
        try {
          message = unichainNetDelegate.getData(hash, type);
        } catch (Exception e) {
          logger.error("Fetch item {} failed. reason: {}", item, hash, e.getMessage());
          peer.disconnect(ReasonCode.FETCH_FAIL);
          return;
        }
      }

      if (type.equals(InventoryType.BLOCK)) {
        BlockId blockId = ((BlockMessage) message).getBlockCapsule().getBlockId();
        if (peer.getBlockBothHave().getNum() < blockId.getNum()) {
          peer.setBlockBothHave(blockId);
        }
        peer.sendMessage(message);
      } else {
        transactions.add(((TransactionMessage) message).getTransactionCapsule().getInstance());
        size += ((TransactionMessage) message).getTransactionCapsule().getInstance().getSerializedSize();
        if (size > MAX_SIZE) {
          peer.sendMessage(new TransactionsMessage(transactions));
          transactions = Lists.newArrayList();
          size = 0;
        }
      }
    }
    if (transactions.size() > 0) {
      peer.sendMessage(new TransactionsMessage(transactions));
    }
  }

  private void check(PeerConnection peer, FetchInvDataMessage fetchInvDataMsg) throws P2pException {
    MessageTypes type = fetchInvDataMsg.getInvMessageType();

    if (type == MessageTypes.UNW) {
      for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
        if (peer.getAdvInvSpread().getIfPresent(new Item(hash, InventoryType.UNW)) == null) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "not spread inv: {}" + hash);
        }
      }
      int fetchCount = peer.getNodeStatistics().messageStatistics.unichainInUnxFetchInvDataElement.getCount(10);
      int maxCount = advService.getUnxCount().getCount(60);
      if (fetchCount > maxCount) {
        throw new P2pException(TypeEnum.BAD_MESSAGE, "maxCount: " + maxCount + ", fetchCount: " + fetchCount);
      }
    } else {
      boolean isAdv = true;
      for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
        if (peer.getAdvInvSpread().getIfPresent(new Item(hash, InventoryType.BLOCK)) == null) {
          isAdv = false;
          break;
        }
      }
      if (isAdv) {
        MessageCount unichainOutAdvBlock = peer.getNodeStatistics().messageStatistics.unichainOutAdvBlock;
        unichainOutAdvBlock.add(fetchInvDataMsg.getHashList().size());
        int outBlockCountIn1min = unichainOutAdvBlock.getCount(60);
        int producedBlockIn2min = 120_000 / ChainConstant.BLOCK_PRODUCED_INTERVAL;
        if (outBlockCountIn1min > producedBlockIn2min) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "producedBlockIn2min: " + producedBlockIn2min + ", outBlockCountIn1min: " + outBlockCountIn1min);
        }
      } else {
        if (!peer.isNeedSyncFromUs()) {
          throw new P2pException(TypeEnum.BAD_MESSAGE, "no need sync");
        }
        for (Sha256Hash hash : fetchInvDataMsg.getHashList()) {
          long blockNum = new BlockId(hash).getNum();
          long minBlockNum = peer.getLastSyncBlockId().getNum() - 2 * NodeConstant.SYNC_FETCH_BATCH_NUM;
          if (blockNum < minBlockNum) {
            throw new P2pException(TypeEnum.BAD_MESSAGE, "minBlockNum: " + minBlockNum + ", blockNum: " + blockNum);
          }
          if (peer.getSyncBlockIdCache().getIfPresent(hash) != null) {
            throw new P2pException(TypeEnum.BAD_MESSAGE, new BlockId(hash).getString() + " is exist");
          }
          peer.getSyncBlockIdCache().put(hash, System.currentTimeMillis());
        }
      }
    }
  }
}
