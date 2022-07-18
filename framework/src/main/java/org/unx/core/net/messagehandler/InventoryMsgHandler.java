package org.unx.core.net.messagehandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.config.args.Args;
import org.unx.core.net.UnxNetDelegate;
import org.unx.core.net.message.InventoryMessage;
import org.unx.core.net.message.UnxMessage;
import org.unx.core.net.peer.Item;
import org.unx.core.net.peer.PeerConnection;
import org.unx.core.net.service.AdvService;
import org.unx.protos.Protocol.Inventory.InventoryType;

@Slf4j(topic = "net")
@Component
public class InventoryMsgHandler implements UnxMsgHandler {

  @Autowired
  private UnxNetDelegate unxNetDelegate;

  @Autowired
  private AdvService advService;

  @Autowired
  private TransactionsMsgHandler transactionsMsgHandler;

  private int maxCountIn10s = 10_000;

  @Override
  public void processMessage(PeerConnection peer, UnxMessage msg) {
    InventoryMessage inventoryMessage = (InventoryMessage) msg;
    InventoryType type = inventoryMessage.getInventoryType();

    if (!check(peer, inventoryMessage)) {
      return;
    }

    for (Sha256Hash id : inventoryMessage.getHashList()) {
      Item item = new Item(id, type);
      peer.getAdvInvReceive().put(item, System.currentTimeMillis());
      advService.addInv(item);
    }
  }

  private boolean check(PeerConnection peer, InventoryMessage inventoryMessage) {
    InventoryType type = inventoryMessage.getInventoryType();
    int size = inventoryMessage.getHashList().size();

    if (peer.isNeedSyncFromPeer() || peer.isNeedSyncFromUs()) {
      logger.warn("Drop inv: {} size: {} from Peer {}, syncFromUs: {}, syncFromPeer: {}.",
          type, size, peer.getInetAddress(), peer.isNeedSyncFromUs(), peer.isNeedSyncFromPeer());
      return false;
    }

    if (type.equals(InventoryType.UNX)) {
      int count = peer.getNodeStatistics().messageStatistics.unxInUnxInventoryElement.getCount(10);
      if (count > maxCountIn10s) {
        logger.warn("Drop inv: {} size: {} from Peer {}, Inv count: {} is overload.",
            type, size, peer.getInetAddress(), count);
        if (Args.getInstance().isOpenPrintLog()) {
          logger.warn("[overload]Drop tx list is: {}", inventoryMessage.getHashList());
        }
        return false;
      }

      if (transactionsMsgHandler.isBusy()) {
        logger.warn("Drop inv: {} size: {} from Peer {}, transactionsMsgHandler is busy.",
            type, size, peer.getInetAddress());
        if (Args.getInstance().isOpenPrintLog()) {
          logger.warn("[isBusy]Drop tx list is: {}", inventoryMessage.getHashList());
        }
        return false;
      }
    }

    return true;
  }
}
