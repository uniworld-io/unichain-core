package org.unx.core.net;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.common.overlay.message.Message;
import org.unx.common.overlay.server.ChannelManager;
import org.unx.core.exception.P2pException;
import org.unx.core.exception.P2pException.TypeEnum;
import org.unx.core.net.message.TransactionMessage;
import org.unx.core.net.message.UnxMessage;
import org.unx.core.net.messagehandler.BlockMsgHandler;
import org.unx.core.net.messagehandler.ChainInventoryMsgHandler;
import org.unx.core.net.messagehandler.FetchInvDataMsgHandler;
import org.unx.core.net.messagehandler.InventoryMsgHandler;
import org.unx.core.net.messagehandler.PbftDataSyncHandler;
import org.unx.core.net.messagehandler.SyncBlockChainMsgHandler;
import org.unx.core.net.messagehandler.TransactionsMsgHandler;
import org.unx.core.net.peer.PeerConnection;
import org.unx.core.net.peer.PeerStatusCheck;
import org.unx.core.net.service.AdvService;
import org.unx.core.net.service.FetchBlockService;
import org.unx.core.net.service.SyncService;
import org.unx.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
public class UnxNetService {

  @Autowired
  private ChannelManager channelManager;

  @Autowired
  private AdvService advService;

  @Autowired
  private SyncService syncService;

  @Autowired
  private PeerStatusCheck peerStatusCheck;

  @Autowired
  private SyncBlockChainMsgHandler syncBlockChainMsgHandler;

  @Autowired
  private ChainInventoryMsgHandler chainInventoryMsgHandler;

  @Autowired
  private InventoryMsgHandler inventoryMsgHandler;


  @Autowired
  private FetchInvDataMsgHandler fetchInvDataMsgHandler;

  @Autowired
  private BlockMsgHandler blockMsgHandler;

  @Autowired
  private TransactionsMsgHandler transactionsMsgHandler;

  @Autowired
  private PbftDataSyncHandler pbftDataSyncHandler;

  @Autowired
  private FetchBlockService fetchBlockService;

  public void start() {
    channelManager.init();
    advService.init();
    syncService.init();
    peerStatusCheck.init();
    transactionsMsgHandler.init();
    fetchBlockService.init();
    logger.info("UnxNetService start successfully.");
  }

  public void stop() {
    logger.info("UnxNetService closed start.");
    channelManager.close();
    advService.close();
    syncService.close();
    peerStatusCheck.close();
    transactionsMsgHandler.close();
    fetchBlockService.close();
    logger.info("UnxNetService closed successfully.");
  }

  public int fastBroadcastTransaction(TransactionMessage msg) {
    return advService.fastBroadcastTransaction(msg);
  }

  public void broadcast(Message msg) {
    advService.broadcast(msg);
  }

  protected void onMessage(PeerConnection peer, UnxMessage msg) {
    try {
      switch (msg.getType()) {
        case SYNC_BLOCK_CHAIN:
          syncBlockChainMsgHandler.processMessage(peer, msg);
          break;
        case BLOCK_CHAIN_INVENTORY:
          chainInventoryMsgHandler.processMessage(peer, msg);
          break;
        case INVENTORY:
          inventoryMsgHandler.processMessage(peer, msg);
          break;
        case FETCH_INV_DATA:
          fetchInvDataMsgHandler.processMessage(peer, msg);
          break;
        case BLOCK:
          blockMsgHandler.processMessage(peer, msg);
          break;
        case UNXS:
          transactionsMsgHandler.processMessage(peer, msg);
          break;
        case PBFT_COMMIT_MSG:
          pbftDataSyncHandler.processMessage(peer, msg);
          break;
        default:
          throw new P2pException(TypeEnum.NO_SUCH_MESSAGE, msg.getType().toString());
      }
    } catch (Exception e) {
      processException(peer, msg, e);
    }
  }

  private void processException(PeerConnection peer, UnxMessage msg, Exception ex) {
    ReasonCode code;

    if (ex instanceof P2pException) {
      TypeEnum type = ((P2pException) ex).getType();
      switch (type) {
        case BAD_UNX:
          code = ReasonCode.BAD_TX;
          break;
        case BAD_BLOCK:
          code = ReasonCode.BAD_BLOCK;
          break;
        case NO_SUCH_MESSAGE:
        case MESSAGE_WITH_WRONG_LENGTH:
        case BAD_MESSAGE:
          code = ReasonCode.BAD_PROTOCOL;
          break;
        case SYNC_FAILED:
          code = ReasonCode.SYNC_FAIL;
          break;
        case UNLINK_BLOCK:
          code = ReasonCode.UNLINKABLE;
          break;
        default:
          code = ReasonCode.UNKNOWN;
          break;
      }
      logger.error("Message from {} process failed, {} \n type: {}, detail: {}.",
          peer.getInetAddress(), msg, type, ex.getMessage());
    } else {
      code = ReasonCode.UNKNOWN;
      logger.error("Message from {} process failed, {}",
          peer.getInetAddress(), msg, ex);
    }

    peer.disconnect(code);
  }
}
