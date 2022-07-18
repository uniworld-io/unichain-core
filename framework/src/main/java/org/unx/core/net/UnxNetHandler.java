package org.unx.core.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.unx.common.overlay.server.Channel;
import org.unx.common.overlay.server.MessageQueue;
import org.unx.core.net.message.UnxMessage;
import org.unx.core.net.peer.PeerConnection;

@Component
@Scope("prototype")
public class UnxNetHandler extends SimpleChannelInboundHandler<UnxMessage> {

  protected PeerConnection peer;

  private MessageQueue msgQueue;

  @Autowired
  private UnxNetService unxNetService;

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, UnxMessage msg) throws Exception {
    msgQueue.receivedMessage(msg);
    unxNetService.onMessage(peer, msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    peer.processException(cause);
  }

  public void setMsgQueue(MessageQueue msgQueue) {
    this.msgQueue = msgQueue;
  }

  public void setChannel(Channel channel) {
    this.peer = (PeerConnection) channel;
  }

}