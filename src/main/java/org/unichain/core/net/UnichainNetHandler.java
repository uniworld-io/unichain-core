package org.unichain.core.net;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.unichain.common.overlay.server.Channel;
import org.unichain.common.overlay.server.MessageQueue;
import org.unichain.core.net.message.UnichainMessage;
import org.unichain.core.net.peer.PeerConnection;

@Component
@Scope("prototype")
public class UnichainNetHandler extends SimpleChannelInboundHandler<UnichainMessage> {

  protected PeerConnection peer;

  private MessageQueue msgQueue;

  @Autowired
  private UnichainNetService unichainNetService;

//  @Autowired
//  private UnichainNetHandler (final ApplicationContext ctx){
//    unichainNetService = ctx.getBean(UnichainNetService.class);
//  }

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, UnichainMessage msg) throws Exception {
    msgQueue.receivedMessage(msg);
    unichainNetService.onMessage(peer, msg);
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