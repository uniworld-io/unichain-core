package org.unx.core.net.peer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.core.config.Parameter.NetConstants;
import org.unx.core.net.UnxNetDelegate;
import org.unx.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
public class PeerStatusCheck {

  @Autowired
  private UnxNetDelegate unxNetDelegate;

  private ScheduledExecutorService peerStatusCheckExecutor = Executors
      .newSingleThreadScheduledExecutor();

  private int blockUpdateTimeout = 30_000;

  public void init() {
    peerStatusCheckExecutor.scheduleWithFixedDelay(() -> {
      try {
        statusCheck();
      } catch (Exception e) {
        logger.error("", e);
      }
    }, 5, 2, TimeUnit.SECONDS);
  }

  public void close() {
    peerStatusCheckExecutor.shutdown();
  }

  public void statusCheck() {

    long now = System.currentTimeMillis();

    unxNetDelegate.getActivePeer().forEach(peer -> {

      boolean isDisconnected = false;

      if (peer.isNeedSyncFromPeer()
          && peer.getBlockBothHaveUpdateTime() < now - blockUpdateTimeout) {
        logger.warn("Peer {} not sync for a long time.", peer.getInetAddress());
        isDisconnected = true;
      }

      if (!isDisconnected) {
        isDisconnected = peer.getAdvInvRequest().values().stream()
            .anyMatch(time -> time < now - NetConstants.ADV_TIME_OUT);
      }

      if (!isDisconnected) {
        isDisconnected = peer.getSyncBlockRequested().values().stream()
            .anyMatch(time -> time < now - NetConstants.SYNC_TIME_OUT);
      }

      if (isDisconnected) {
        peer.disconnect(ReasonCode.TIME_OUT);
      }
    });
  }

}
