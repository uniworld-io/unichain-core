package org.unx.common.overlay.server;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.unx.common.overlay.message.Message;
import org.unx.common.overlay.message.PingMessage;
import org.unx.common.overlay.message.PongMessage;
import org.unx.common.prometheus.MetricKeys;
import org.unx.common.prometheus.MetricLabels;
import org.unx.common.prometheus.Metrics;
import org.unx.consensus.pbft.message.PbftBaseMessage;
import org.unx.core.metrics.MetricsKey;
import org.unx.core.metrics.MetricsUtil;
import org.unx.core.net.message.InventoryMessage;
import org.unx.core.net.message.TransactionsMessage;
import org.unx.protos.Protocol.Inventory.InventoryType;
import org.unx.protos.Protocol.ReasonCode;

@Slf4j(topic = "net")
@Component
@Scope("prototype")
public class MessageQueue {

  private static ScheduledExecutorService sendTimer =
      Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "sendTimer"));
  private volatile boolean sendMsgFlag = false;
  private volatile long sendTime;
  private volatile long sendPing;
  private Thread sendMsgThread;
  private Channel channel;
  private ChannelHandlerContext ctx = null;
  private Queue<MessageRoundTrip> requestQueue = new ConcurrentLinkedQueue<>();
  private BlockingQueue<Message> msgQueue = new LinkedBlockingQueue<>();
  private ScheduledFuture<?> sendTask;


  public void activate(ChannelHandlerContext ctx) {

    this.ctx = ctx;

    sendMsgFlag = true;

    sendTask = sendTimer.scheduleAtFixedRate(() -> {
      try {
        if (sendMsgFlag) {
          send();
        }
      } catch (Exception e) {
        logger.error("Unhandled exception", e);
      }
    }, 10, 10, TimeUnit.MILLISECONDS);

    sendMsgThread = new Thread(() -> {
      while (sendMsgFlag) {
        try {
          if (msgQueue.isEmpty()) {
            Thread.sleep(10);
            continue;
          }
          Message msg = msgQueue.take();
          ctx.writeAndFlush(msg.getSendData()).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess() && !channel.isDisconnect()) {
              logger.warn("Failed to send to {}, {}", ctx.channel().remoteAddress(), msg);
            }
          });
        } catch (InterruptedException e) {
          logger.warn("Send message server interrupted.");
          Thread.currentThread().interrupt();
        } catch (Exception e) {
          logger.error("Failed to send to {}, error info: {}", ctx.channel().remoteAddress(),
              e.getMessage());
        }
      }
    });
    sendMsgThread.setName("sendMsgThread-" + ctx.channel().remoteAddress());
    sendMsgThread.start();
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public void fastSend(Message msg) {
    logger.info("Fast send to {}, {} ", ctx.channel().remoteAddress(), msg);
    ctx.writeAndFlush(msg.getSendData()).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess() && !channel.isDisconnect()) {
        logger.error("Fast send to {} failed, {}", ctx.channel().remoteAddress(), msg);
      }
    });
  }

  public boolean sendMessage(Message msg) {
    long now = System.currentTimeMillis();
    if (msg instanceof PingMessage) {
      if (now - sendTime < 10_000 && now - sendPing < 60_000) {
        return false;
      }
      sendPing = now;
    }
    if (needToLog(msg)) {
      logger.info("Send to {}, {} ", ctx.channel().remoteAddress(), msg);
    }
    channel.getNodeStatistics().messageStatistics.addTcpOutMessage(msg);
    int length = msg.getSendData().readableBytes();
    MetricsUtil.meterMark(MetricsKey.NET_TCP_OUT_TRAFFIC, length);
    Metrics.histogramObserve(MetricKeys.Histogram.TCP_BYTES, length,
        MetricLabels.Histogram.TRAFFIC_OUT);

    sendTime = System.currentTimeMillis();
    if (msg.getAnswerMessage() != null) {
      requestQueue.add(new MessageRoundTrip(msg));
    } else {
      msgQueue.offer(msg);
    }
    return true;
  }

  public void receivedMessage(Message msg) {
    if (needToLog(msg)) {
      logger.info("Receive from {}, {}", ctx.channel().remoteAddress(), msg);
    }
    channel.getNodeStatistics().messageStatistics.addTcpInMessage(msg);
    MessageRoundTrip rt = requestQueue.peek();
    if (rt != null && rt.getMsg().getAnswerMessage() == msg.getClass()) {
      requestQueue.remove();
      if (rt.getMsg() instanceof PingMessage) {
        channel.getNodeStatistics().pingMessageLatency
            .add(System.currentTimeMillis() - rt.getTime());
      }
    }
  }

  public void close() {
    sendMsgFlag = false;
    if (sendTask != null && !sendTask.isCancelled()) {
      sendTask.cancel(false);
      sendTask = null;
    }
    if (sendMsgThread != null) {
      try {
        sendMsgThread.join(20);
        sendMsgThread = null;
      } catch (InterruptedException e) {
        logger.warn("Send message join interrupted.");
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        logger.warn("Join send thread failed, peer {}", ctx.channel().remoteAddress());
      }
    }
  }

  private boolean needToLog(Message msg) {
    if (msg instanceof PingMessage
        || msg instanceof PongMessage
        || msg instanceof TransactionsMessage
        || msg instanceof PbftBaseMessage) {
      return false;
    }

    if (msg instanceof InventoryMessage
        && ((InventoryMessage) msg).getInventoryType().equals(InventoryType.UNX)) {
      return false;
    }

    return true;
  }

  private void send() {
    MessageRoundTrip rt = requestQueue.peek();
    if (!sendMsgFlag || rt == null) {
      return;
    }
    if (rt.getRetryTimes() > 0 && !rt.hasToRetry()) {
      return;
    }
    if (rt.getRetryTimes() > 0) {
      channel.getNodeStatistics().nodeDisconnectedLocal(ReasonCode.PING_TIMEOUT);
      logger.warn("Wait {} timeout. close channel {}.",
          rt.getMsg().getAnswerMessage(), ctx.channel().remoteAddress());
      channel.close();
      return;
    }

    Message msg = rt.getMsg();

    ctx.writeAndFlush(msg.getSendData()).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess()) {
        logger.warn("Fail send to {}, {}", ctx.channel().remoteAddress(), msg);
      }
    });

    rt.incRetryTimes();
    rt.saveTime();
  }

}
