package org.unx.core.net.messagehandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.core.config.args.Args;
import org.unx.core.exception.P2pException;
import org.unx.core.exception.P2pException.TypeEnum;
import org.unx.core.net.UnxNetDelegate;
import org.unx.core.net.message.TransactionMessage;
import org.unx.core.net.message.TransactionsMessage;
import org.unx.core.net.message.UnxMessage;
import org.unx.core.net.peer.Item;
import org.unx.core.net.peer.PeerConnection;
import org.unx.core.net.service.AdvService;
import org.unx.protos.Protocol.Inventory.InventoryType;
import org.unx.protos.Protocol.ReasonCode;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;

@Slf4j(topic = "net")
@Component
public class TransactionsMsgHandler implements UnxMsgHandler {

  private static int MAX_UNX_SIZE = 50_000;
  private static int MAX_SMART_CONTRACT_SUBMIT_SIZE = 100;
  @Autowired
  private UnxNetDelegate unxNetDelegate;
  @Autowired
  private AdvService advService;

  private BlockingQueue<UnxEvent> smartContractQueue = new LinkedBlockingQueue(MAX_UNX_SIZE);

  private BlockingQueue<Runnable> queue = new LinkedBlockingQueue();

  private int threadNum = Args.getInstance().getValidateSignThreadNum();
  private ExecutorService unxHandlePool = new ThreadPoolExecutor(threadNum, threadNum, 0L,
      TimeUnit.MILLISECONDS, queue);

  private ScheduledExecutorService smartContractExecutor = Executors
      .newSingleThreadScheduledExecutor();

  public void init() {
    handleSmartContract();
  }

  public void close() {
    smartContractExecutor.shutdown();
  }

  public boolean isBusy() {
    return queue.size() + smartContractQueue.size() > MAX_UNX_SIZE;
  }

  @Override
  public void processMessage(PeerConnection peer, UnxMessage msg) throws P2pException {
    TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
    check(peer, transactionsMessage);
    for (Transaction unx : transactionsMessage.getTransactions().getTransactionsList()) {
      int type = unx.getRawData().getContract(0).getType().getNumber();
      if (type == ContractType.TriggerSmartContract_VALUE
          || type == ContractType.CreateSmartContract_VALUE) {
        if (!smartContractQueue.offer(new UnxEvent(peer, new TransactionMessage(unx)))) {
          logger.warn("Add smart contract failed, queueSize {}:{}", smartContractQueue.size(),
              queue.size());
        }
      } else {
        unxHandlePool.submit(() -> handleTransaction(peer, new TransactionMessage(unx)));
      }
    }
  }

  private void check(PeerConnection peer, TransactionsMessage msg) throws P2pException {
    for (Transaction unx : msg.getTransactions().getTransactionsList()) {
      Item item = new Item(new TransactionMessage(unx).getMessageId(), InventoryType.UNX);
      if (!peer.getAdvInvRequest().containsKey(item)) {
        throw new P2pException(TypeEnum.BAD_MESSAGE,
            "unx: " + msg.getMessageId() + " without request.");
      }
      peer.getAdvInvRequest().remove(item);
    }
  }

  private void handleSmartContract() {
    smartContractExecutor.scheduleWithFixedDelay(() -> {
      try {
        while (queue.size() < MAX_SMART_CONTRACT_SUBMIT_SIZE) {
          UnxEvent event = smartContractQueue.take();
          unxHandlePool.submit(() -> handleTransaction(event.getPeer(), event.getMsg()));
        }
      } catch (InterruptedException e) {
        logger.warn("Handle smart server interrupted.");
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        logger.error("Handle smart contract exception.", e);
      }
    }, 1000, 20, TimeUnit.MILLISECONDS);
  }

  private void handleTransaction(PeerConnection peer, TransactionMessage unx) {
    if (peer.isDisconnect()) {
      logger.warn("Drop unx {} from {}, peer is disconnect.", unx.getMessageId(),
          peer.getInetAddress());
      return;
    }

    if (advService.getMessage(new Item(unx.getMessageId(), InventoryType.UNX)) != null) {
      return;
    }

    try {
      unxNetDelegate.pushTransaction(unx.getTransactionCapsule());
      advService.broadcast(unx);
    } catch (P2pException e) {
      logger.warn("Unx {} from peer {} process failed. type: {}, reason: {}",
          unx.getMessageId(), peer.getInetAddress(), e.getType(), e.getMessage());
      if (e.getType().equals(TypeEnum.BAD_UNX)) {
        peer.disconnect(ReasonCode.BAD_TX);
      }
    } catch (Exception e) {
      logger.error("Unx {} from peer {} process failed.", unx.getMessageId(), peer.getInetAddress(),
          e);
    }
  }

  class UnxEvent {

    @Getter
    private PeerConnection peer;
    @Getter
    private TransactionMessage msg;
    @Getter
    private long time;

    public UnxEvent(PeerConnection peer, TransactionMessage msg) {
      this.peer = peer;
      this.msg = msg;
      this.time = System.currentTimeMillis();
    }
  }
}