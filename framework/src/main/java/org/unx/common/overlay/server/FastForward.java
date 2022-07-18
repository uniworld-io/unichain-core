package org.unx.common.overlay.server;

import com.google.protobuf.ByteString;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.unx.common.backup.BackupManager;
import org.unx.common.backup.BackupManager.BackupStatusEnum;
import org.unx.common.crypto.SignInterface;
import org.unx.common.crypto.SignUtils;
import org.unx.common.overlay.discover.node.Node;
import org.unx.common.overlay.message.HelloMessage;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.config.args.Args;
import org.unx.core.db.Manager;
import org.unx.core.store.WitnessScheduleStore;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
public class FastForward {

  @Autowired
  private ApplicationContext ctx;

  private Manager manager;

  private ChannelManager channelManager;

  private WitnessScheduleStore witnessScheduleStore;

  private BackupManager backupManager;

  private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

  private CommonParameter parameter = Args.getInstance();
  private List<Node> fastForwardNodes = parameter.getFastForwardNodes();
  private ByteString witnessAddress = ByteString
      .copyFrom(Args.getLocalWitnesses().getWitnessAccountAddress(CommonParameter.getInstance()
          .isECKeyCryptoEngine()));
  private int keySize = Args.getLocalWitnesses().getPrivateKeys().size();

  public void init() {
    manager = ctx.getBean(Manager.class);
    channelManager = ctx.getBean(ChannelManager.class);
    witnessScheduleStore = ctx.getBean(WitnessScheduleStore.class);
    backupManager = ctx.getBean(BackupManager.class);

    logger.info("Fast forward config, isWitness: {}, keySize: {}, fastForwardNodes: {}",
        parameter.isWitness(), keySize, fastForwardNodes.size());

    if (!parameter.isWitness() || keySize == 0 || fastForwardNodes.isEmpty()) {
      return;
    }

    executorService.scheduleWithFixedDelay(() -> {
      try {
        if (witnessScheduleStore.getActiveWitnesses().contains(witnessAddress)
            && backupManager.getStatus().equals(BackupStatusEnum.MASTER)) {
          connect();
        } else {
          disconnect();
        }
      } catch (Exception e) {
        logger.info("Execute failed.", e);
      }
    }, 30, 100, TimeUnit.SECONDS);
  }

  public void fillHelloMessage(HelloMessage message, Channel channel) {
    if (isActiveWitness()) {
      fastForwardNodes.forEach(node -> {
        InetAddress address = new InetSocketAddress(node.getHost(), node.getPort()).getAddress();
        if (address.equals(channel.getInetAddress())) {
          SignInterface cryptoEngine = SignUtils
              .fromPrivate(ByteArray.fromHexString(Args.getLocalWitnesses().getPrivateKey()),
                  Args.getInstance().isECKeyCryptoEngine());

          ByteString sig = ByteString.copyFrom(cryptoEngine.Base64toBytes(cryptoEngine
              .signHash(Sha256Hash.of(CommonParameter.getInstance()
                  .isECKeyCryptoEngine(), ByteArray.fromLong(message
                  .getTimestamp())).getBytes())));
          message.setHelloMessage(message.getHelloMessage().toBuilder()
              .setAddress(witnessAddress).setSignature(sig).build());
        }
      });
    }
  }

  public boolean checkHelloMessage(HelloMessage message, Channel channel) {
    if (!parameter.isFastForward()) {
      return true;
    }

    Protocol.HelloMessage msg = message.getHelloMessage();

    if (msg.getAddress() == null || msg.getAddress().isEmpty()) {
      logger.info("HelloMessage from {}, address is empty.", channel.getInetAddress());
      return false;
    }

    if (!witnessScheduleStore.getActiveWitnesses().contains(msg.getAddress())) {
      logger.error("HelloMessage from {}, {} is not a schedule witness.",
          channel.getInetAddress(),
          ByteArray.toHexString(msg.getAddress().toByteArray()));
      return false;
    }

    boolean flag;
    try {
      Sha256Hash hash = Sha256Hash.of(CommonParameter
          .getInstance().isECKeyCryptoEngine(), ByteArray.fromLong(msg.getTimestamp()));
      String sig =
          TransactionCapsule.getBase64FromByteString(msg.getSignature());
      byte[] sigAddress = SignUtils.signatureToAddress(hash.getBytes(), sig,
          Args.getInstance().isECKeyCryptoEngine());
      if (manager.getDynamicPropertiesStore().getAllowMultiSign() != 1) {
        flag = Arrays.equals(sigAddress, msg.getAddress().toByteArray());
      } else {
        byte[] witnessPermissionAddress = manager.getAccountStore()
            .get(msg.getAddress().toByteArray()).getWitnessPermissionAddress();
        flag = Arrays.equals(sigAddress, witnessPermissionAddress);
      }
      if (flag) {
        channelManager.getTrustNodes().put(channel.getInetAddress(), channel.getNode());
      }
      return flag;
    } catch (Exception e) {
      logger.error("Check hello message failed, msg: {}, {}", message, e);
      return false;
    }
  }

  private boolean isActiveWitness() {
    return parameter.isWitness()
        && keySize > 0
        && fastForwardNodes.size() > 0
        && witnessScheduleStore.getActiveWitnesses().contains(witnessAddress)
        && backupManager.getStatus().equals(BackupStatusEnum.MASTER);
  }

  private void connect() {
    fastForwardNodes.forEach(node -> {
      InetAddress address = new InetSocketAddress(node.getHost(), node.getPort()).getAddress();
      channelManager.getActiveNodes().put(address, node);
    });
  }

  private void disconnect() {
    fastForwardNodes.forEach(node -> {
      InetAddress address = new InetSocketAddress(node.getHost(), node.getPort()).getAddress();
      channelManager.getActiveNodes().remove(address);
      channelManager.getActivePeers().forEach(channel -> {
        if (channel.getInetAddress().equals(address)) {
          channel.disconnect(ReasonCode.RESET);
        }
      });
    });
  }
}
