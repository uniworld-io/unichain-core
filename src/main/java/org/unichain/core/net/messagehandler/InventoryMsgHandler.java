package org.unichain.core.net.messagehandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.net.UnichainNetDelegate;
import org.unichain.core.net.message.InventoryMessage;
import org.unichain.core.net.message.UnichainMessage;
import org.unichain.core.net.peer.Item;
import org.unichain.core.net.peer.PeerConnection;
import org.unichain.core.net.service.AdvService;
import org.unichain.protos.Protocol.Inventory.InventoryType;

@Slf4j(topic = "net")
@Component
public class InventoryMsgHandler implements UnichainMsgHandler {

  @Autowired
  private UnichainNetDelegate unichainNetDelegate;

  @Autowired
  private AdvService advService;

  @Autowired
  private TransactionsMsgHandler transactionsMsgHandler;

  private int maxCountIn10s = 10_000;

  @Override
  public void processMessage(PeerConnection peer, UnichainMessage msg) {
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
      logger.warn("Drop inv: {} size: {} from Peer {}, syncFromUs: {}, syncFromPeer: {}.", type, size, peer.getInetAddress(), peer.isNeedSyncFromUs(), peer.isNeedSyncFromPeer());
      return false;
    }

    if (type.equals(InventoryType.UNW)) {
      int count = peer.getNodeStatistics().messageStatistics.unichainInUnxInventoryElement.getCount(10);
      if (count > maxCountIn10s) {
        logger.warn("Drop inv: {} size: {} from Peer {}, Inv count: {} is overload.",
            type, size, peer.getInetAddress(), count);
        return false;
      }

      if (transactionsMsgHandler.isBusy()) {
        logger.warn("Drop inv: {} size: {} from Peer {}, transactionsMsgHandler is busy.",
            type, size, peer.getInetAddress());
        return false;
      }
    }

    return true;
  }
}
