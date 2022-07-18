package org.unx.common.overlay.server;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.unx.common.overlay.discover.node.Node;
import org.unx.common.overlay.discover.node.NodeHandler;
import org.unx.common.overlay.discover.node.NodeManager;
import org.unx.common.overlay.discover.node.statistics.NodeStatistics;
import org.unx.common.overlay.message.DisconnectMessage;
import org.unx.common.overlay.message.HelloMessage;
import org.unx.common.overlay.message.MessageCodec;
import org.unx.common.overlay.message.StaticMessages;
import org.unx.core.db.ByteArrayWrapper;
import org.unx.core.exception.P2pException;
import org.unx.core.net.PbftHandler;
import org.unx.core.net.UnxNetHandler;
import org.unx.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
@Scope("prototype")
public class Channel {

  @Autowired
  protected MessageQueue msgQueue;
  protected NodeStatistics nodeStatistics;
  @Autowired
  private MessageCodec messageCodec;
  @Autowired
  private NodeManager nodeManager;
  @Autowired
  private StaticMessages staticMessages;
  @Autowired
  private WireTrafficStats stats;
  @Autowired
  private HandshakeHandler handshakeHandler;
  @Autowired
  private P2pHandler p2pHandler;
  @Autowired
  private UnxNetHandler unxNetHandler;
  @Autowired
  private PbftHandler pbftHandler;
  private ChannelManager channelManager;
  private ChannelHandlerContext ctx;
  private InetSocketAddress inetSocketAddress;
  private Node node;
  private long startTime;
  private UnxState unxState = UnxState.INIT;
  private boolean isActive;
  @Getter
  @Setter
  private ByteString address;

  private volatile boolean isDisconnect;

  @Getter
  private volatile long disconnectTime;

  private boolean isTrustPeer;

  private boolean isFastForwardPeer;

  public void init(ChannelPipeline pipeline, String remoteId, boolean discoveryMode,
      ChannelManager channelManager) {

    this.channelManager = channelManager;

    isActive = remoteId != null && !remoteId.isEmpty();

    startTime = System.currentTimeMillis();

    //TODO: use config here
    pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(60, TimeUnit.SECONDS));
    pipeline.addLast(stats.tcp);
    pipeline.addLast("protoPender", new ProtobufVarint32LengthFieldPrepender());
    pipeline.addLast("lengthDecode", new UnxProtobufVarint32FrameDecoder(this));

    //handshake first
    pipeline.addLast("handshakeHandler", handshakeHandler);

    messageCodec.setChannel(this);
    msgQueue.setChannel(this);
    handshakeHandler.setChannel(this, remoteId);
    p2pHandler.setChannel(this);
    unxNetHandler.setChannel(this);
    pbftHandler.setChannel(this);

    p2pHandler.setMsgQueue(msgQueue);
    unxNetHandler.setMsgQueue(msgQueue);
    pbftHandler.setMsgQueue(msgQueue);
  }

  public void publicHandshakeFinished(ChannelHandlerContext ctx, HelloMessage msg) {
    isTrustPeer = channelManager.getTrustNodes().getIfPresent(getInetAddress()) != null;
    isFastForwardPeer = channelManager.getFastForwardNodes().containsKey(getInetAddress());
    ctx.pipeline().remove(handshakeHandler);
    msgQueue.activate(ctx);
    ctx.pipeline().addLast("messageCodec", messageCodec);
    ctx.pipeline().addLast("p2p", p2pHandler);
    ctx.pipeline().addLast("data", unxNetHandler);
    ctx.pipeline().addLast("pbft", pbftHandler);
    setStartTime(msg.getTimestamp());
    setUnxState(UnxState.HANDSHAKE_FINISHED);
    getNodeStatistics().p2pHandShake.add();
    logger.info("Finish handshake with {}.", ctx.channel().remoteAddress());
  }

  /**
   * Set node and register it in NodeManager if it is not registered yet.
   */
  public void initNode(byte[] nodeId, int remotePort) {
    Node n = new Node(nodeId, inetSocketAddress.getHostString(), remotePort);
    NodeHandler handler = nodeManager.getNodeHandler(n);
    node = handler.getNode();
    nodeStatistics = handler.getNodeStatistics();
    handler.getNode().setId(nodeId);
  }

  public void disconnect(ReasonCode reason) {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();
    channelManager.processDisconnect(this, reason);
    DisconnectMessage msg = new DisconnectMessage(reason);
    logger.info("Send to {} online-time {}s, {}",
        ctx.channel().remoteAddress(),
        (System.currentTimeMillis() - startTime) / 1000,
        msg);
    getNodeStatistics().nodeDisconnectedLocal(reason);
    ctx.writeAndFlush(msg.getSendData()).addListener(future -> close());
  }

  public void processException(Throwable throwable) {
    Throwable baseThrowable = throwable;
    while (baseThrowable.getCause() != null) {
      baseThrowable = baseThrowable.getCause();
    }
    SocketAddress address = ctx.channel().remoteAddress();
    if (throwable instanceof ReadTimeoutException
        || throwable instanceof IOException) {
      logger.warn("Close peer {}, reason: {}", address, throwable.getMessage());
    } else if (baseThrowable instanceof P2pException) {
      logger.warn("Close peer {}, type: {}, info: {}",
          address, ((P2pException) baseThrowable).getType(), baseThrowable.getMessage());
    } else {
      logger.error("Close peer {}, exception caught", address, throwable);
    }
    close();
  }

  public void close() {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();
    p2pHandler.close();
    msgQueue.close();
    ctx.close();
  }

  public Node getNode() {
    return node;
  }

  public byte[] getNodeId() {
    return node == null ? null : node.getId();
  }

  public ByteArrayWrapper getNodeIdWrapper() {
    return node == null ? null : new ByteArrayWrapper(node.getId());
  }

  public String getPeerId() {
    return node == null ? "<null>" : node.getHexId();
  }

  public void setChannelHandlerContext(ChannelHandlerContext ctx) {
    this.ctx = ctx;
    this.inetSocketAddress = ctx == null ? null : (InetSocketAddress) ctx.channel().remoteAddress();
  }

  public InetAddress getInetAddress() {
    return ctx == null ? null : ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
  }

  public NodeStatistics getNodeStatistics() {
    return nodeStatistics;
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public void setUnxState(UnxState unxState) {
    this.unxState = unxState;
    logger.info("Peer {} status change to {}.", inetSocketAddress, unxState);
  }

  public boolean isActive() {
    return isActive;
  }

  public boolean isDisconnect() {
    return isDisconnect;
  }

  public boolean isTrustPeer() {
    return isTrustPeer;
  }

  public boolean isFastForwardPeer() {
    return isFastForwardPeer;
  }

  @Override
  public boolean equals(Object o) {

    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Channel channel = (Channel) o;
    if (inetSocketAddress != null ? !inetSocketAddress.equals(channel.inetSocketAddress)
        : channel.inetSocketAddress != null) {
      return false;
    }
    if (node != null ? !node.equals(channel.node) : channel.node != null) {
      return false;
    }
    return this == channel;
  }

  @Override
  public int hashCode() {
    int result = inetSocketAddress != null ? inetSocketAddress.hashCode() : 0;
    result = 31 * result + (node != null ? node.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format("%s | %s", inetSocketAddress, getPeerId());
  }

  public enum UnxState {
    INIT,
    HANDSHAKE_FINISHED,
    START_TO_SYNC,
    SYNCING,
    SYNC_COMPLETED,
    SYNC_FAILED
  }
}

