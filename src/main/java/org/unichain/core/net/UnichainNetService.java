package org.unichain.core.net;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.overlay.message.Message;
import org.unichain.common.overlay.server.ChannelManager;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.P2pException;
import org.unichain.core.exception.P2pException.TypeEnum;
import org.unichain.core.net.message.BlockMessage;
import org.unichain.core.net.message.UnichainMessage;
import org.unichain.core.net.messagehandler.*;
import org.unichain.core.net.peer.PeerConnection;
import org.unichain.core.net.peer.PeerStatusCheck;
import org.unichain.core.net.service.AdvService;
import org.unichain.core.net.service.SyncService;
import org.unichain.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
public class UnichainNetService {

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
  private Manager manager;

  public void start() {
    manager.setUnichainNetService(this);
    channelManager.init();
    advService.init();
    syncService.init();
    peerStatusCheck.init();
    transactionsMsgHandler.init();
    logger.info("UnichainNetService start successfully.");
  }

  public void close() {
    channelManager.close();
    advService.close();
    syncService.close();
    peerStatusCheck.close();
    transactionsMsgHandler.close();
    logger.info("UnichainNetService closed successfully.");
  }

  public void broadcast(Message msg) {
    advService.broadcast(msg);
  }

  public void fastForward(BlockMessage msg) {
    advService.fastForward(msg);
  }

  protected void onMessage(PeerConnection peer, UnichainMessage msg) {
    try {
      switch (msg.getType()) {
        //handle chain summary of peer, send back confirmed block graph
        case SYNC_BLOCK_CHAIN:
          syncBlockChainMsgHandler.processMessage(peer, msg);
          break;
        //handle confirmed block graph, filter it again, buffer and send it to remote peer using other thread
        case BLOCK_CHAIN_INVENTORY:
          chainInventoryMsgHandler.processMessage(peer, msg);
          break;
        //handle inventory msg, ex: fast forward of block & tx
        case INVENTORY:
          inventoryMsgHandler.processMessage(peer, msg);
          break;

        //handle real block request graph that send back block data
        case FETCH_INV_DATA:
          fetchInvDataMsgHandler.processMessage(peer, msg);
          break;

        //handle any block broadcast includes sync block & fresh blocks generated from remote peer
        case BLOCK:
          blockMsgHandler.processMessage(peer, msg);
          break;

        //handle tx broadcasting
        case UNWS:
          transactionsMsgHandler.processMessage(peer, msg);
          break;
        default:
          throw new P2pException(TypeEnum.NO_SUCH_MESSAGE, msg.getType().toString());
      }
    } catch (Exception e) {
      processException(peer, msg, e);
    }
  }

  private void processException(PeerConnection peer, UnichainMessage msg, Exception ex) {
    ReasonCode code;

    if (ex instanceof P2pException) {
      TypeEnum type = ((P2pException) ex).getType();
      switch (type) {
        case BAD_UNW:
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
