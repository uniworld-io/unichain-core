package org.unichain.core.net.messagehandler;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.BlockCapsule.BlockId;
import org.unichain.core.config.args.Args;
import org.unichain.core.exception.P2pException;
import org.unichain.core.exception.P2pException.TypeEnum;
import org.unichain.core.net.UnichainNetDelegate;
import org.unichain.core.net.message.BlockMessage;
import org.unichain.core.net.message.UnichainMessage;
import org.unichain.core.net.peer.Item;
import org.unichain.core.net.peer.PeerConnection;
import org.unichain.core.net.service.AdvService;
import org.unichain.core.net.service.SyncService;
import org.unichain.core.services.WitnessProductBlockService;
import org.unichain.protos.Protocol.Inventory.InventoryType;

import static org.unichain.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;
import static org.unichain.core.config.Parameter.ChainConstant.BLOCK_SIZE;

@Slf4j(topic = "net")
@Component
public class BlockMsgHandler implements UnichainMsgHandler {

  @Autowired
  private UnichainNetDelegate unichainNetDelegate;

  @Autowired
  private AdvService advService;

  @Autowired
  private SyncService syncService;

  @Autowired
  private WitnessProductBlockService witnessProductBlockService;

  private int maxBlockSize = BLOCK_SIZE + 1000;

  private boolean fastForward = Args.getInstance().isFastForward();

  /**
   * Process block from peers
   * - include fresh generated blocks & sync-request block
   * - process sync block or fresh blocks
   */
  @Override
  public void processMessage(PeerConnection peer, UnichainMessage msg) throws P2pException {
    BlockMessage blockMessage = (BlockMessage) msg;
    BlockId blockId = blockMessage.getBlockId();

    if (!fastForward && !peer.isFastForwardPeer()) {
      check(peer, blockMessage);
    }

    if (peer.getSyncBlockRequested().containsKey(blockId)) {
      //if sync block: queue up block to process later & ignite next turn
      peer.getSyncBlockRequested().remove(blockId);
      syncService.processBlock(peer, blockMessage);
    } else {
      Long time = peer.getAdvInvRequest().remove(new Item(blockId, InventoryType.BLOCK));
      long now = System.currentTimeMillis();
      long interval = blockId.getNum() - unichainNetDelegate.getHeadBlockId().getNum();
      processBlock(peer, blockMessage.getBlockCapsule());
      logger.info("Receive block/interval {}/{} from {} fetch/delay {}/{}ms, txs/process {}/{}ms, witness: {}",
          blockId.getNum(),
          interval,
          peer.getInetAddress(),
          time == null ? 0 : now - time,
          now - blockMessage.getBlockCapsule().getTimeStamp(),
          ((BlockMessage) msg).getBlockCapsule().getTransactions().size(),
          System.currentTimeMillis() - now,
          Hex.toHexString(blockMessage.getBlockCapsule().getWitnessAddress().toByteArray()));
    }
  }

  private void check(PeerConnection peer, BlockMessage msg) throws P2pException {
    Item item = new Item(msg.getBlockId(), InventoryType.BLOCK);
    if (!peer.getSyncBlockRequested().containsKey(msg.getBlockId()) && !peer.getAdvInvRequest().containsKey(item)) {
      throw new P2pException(TypeEnum.BAD_MESSAGE, "no request");
    }
    BlockCapsule blockCapsule = msg.getBlockCapsule();
    if (blockCapsule.getInstance().getSerializedSize() > maxBlockSize) {
      throw new P2pException(TypeEnum.BAD_MESSAGE, "block size over limit");
    }
    long gap = blockCapsule.getTimeStamp() - System.currentTimeMillis();
    if (gap >= BLOCK_PRODUCED_INTERVAL) {
      throw new P2pException(TypeEnum.BAD_MESSAGE, "block time error");
    }
  }

  private void processBlock(PeerConnection peer, BlockCapsule block) throws P2pException {
    /**
     * @todo review: check hard-forked chain
     */
    val currentVersion = unichainNetDelegate.getRunningBlockVersion();
    var blockVersion = block.getInstance().getBlockHeader().getRawData().getVersion();
    if(blockVersion < currentVersion)
    {
      throw new P2pException(TypeEnum.HARD_FORKED, String.format("receive lower block version: %s, current version %s --> assume hardforked!", blockVersion, currentVersion));
    }

    BlockId blockId = block.getBlockId();
    if (!unichainNetDelegate.containBlock(block.getParentBlockId())) {
      logger.warn("Get unlink block {} from {}, head is {}.", blockId.getString(), peer.getInetAddress(), unichainNetDelegate.getHeadBlockId().getString());
      syncService.startSync(peer);
      return;
    }

    Item item = new Item(blockId, InventoryType.BLOCK);
    if (fastForward || peer.isFastForwardPeer()) {
      peer.getAdvInvReceive().put(item, System.currentTimeMillis());
      advService.addInvToCache(item);
    }

    if (fastForward) {
      if (block.getNum() < unichainNetDelegate.getHeadBlockId().getNum()) {
        logger.warn("Receive a low block {}, head {}", blockId.getString(), unichainNetDelegate.getHeadBlockId().getString());
        return;
      }
      if (unichainNetDelegate.validBlock(block)) {
        advService.fastForward(new BlockMessage(block));
        unichainNetDelegate.trustNode(peer);
      }
    }

    unichainNetDelegate.processBlock(block);
    witnessProductBlockService.validWitnessProductTwoBlock(block);
    unichainNetDelegate.getActivePeer().forEach(p -> {
      if (p.getAdvInvReceive().getIfPresent(blockId) != null) {
        p.setBlockBothHave(blockId);
      }
    });

    if (!fastForward) {
      advService.broadcast(new BlockMessage(block));
    }
  }
}
