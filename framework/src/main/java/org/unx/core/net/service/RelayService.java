package org.unx.core.net.service;

import com.google.protobuf.ByteString;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.core.ChainBaseManager;
import org.unx.core.config.args.Args;
import org.unx.core.net.UnxNetDelegate;
import org.unx.core.net.message.BlockMessage;
import org.unx.core.net.peer.Item;
import org.unx.core.net.peer.PeerConnection;
import org.unx.protos.Protocol;

@Slf4j(topic = "net")
@Component
public class RelayService {

  @Autowired
  private ChainBaseManager chainBaseManager;

  @Autowired
  private UnxNetDelegate unxNetDelegate;

  private int maxFastForwardNum = Args.getInstance().getMaxFastForwardNum();

  private Set<ByteString> getNextWitnesses(ByteString key, Integer count) {
    List<ByteString> list = chainBaseManager.getWitnessScheduleStore().getActiveWitnesses();
    int index = list.indexOf(key);
    if (index < 0) {
      return new HashSet<>(list);
    }
    Set<ByteString> set = new HashSet<>();
    for (; count > 0; count--) {
      set.add(list.get(++index % list.size()));
    }
    return set;
  }

  public void broadcast(BlockMessage msg) {
    Set<ByteString> witnesses = getNextWitnesses(
            msg.getBlockCapsule().getWitnessAddress(), maxFastForwardNum);
    Item item = new Item(msg.getBlockId(), Protocol.Inventory.InventoryType.BLOCK);
    List<PeerConnection> peers = unxNetDelegate.getActivePeer().stream()
            .filter(peer -> !peer.isNeedSyncFromPeer() && !peer.isNeedSyncFromUs())
            .filter(peer -> peer.getAdvInvReceive().getIfPresent(item) == null
                    && peer.getAdvInvSpread().getIfPresent(item) == null)
            .filter(peer -> peer.getAddress() != null && witnesses.contains(peer.getAddress()))
            .collect(Collectors.toList());

    peers.forEach(peer -> {
      peer.fastSend(msg);
      peer.getAdvInvSpread().put(item, System.currentTimeMillis());
      peer.setFastForwardBlock(msg.getBlockId());
    });
  }

}

