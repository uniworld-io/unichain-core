package org.unx.common.overlay.discover.node.statistics;

import lombok.extern.slf4j.Slf4j;
import org.unx.common.net.udp.message.UdpMessageTypeEnum;
import org.unx.common.overlay.message.Message;
import org.unx.core.net.message.FetchInvDataMessage;
import org.unx.core.net.message.InventoryMessage;
import org.unx.core.net.message.MessageTypes;
import org.unx.core.net.message.TransactionsMessage;

@Slf4j
public class MessageStatistics {

  //udp discovery
  public final MessageCount discoverInPing = new MessageCount();
  public final MessageCount discoverOutPing = new MessageCount();
  public final MessageCount discoverInPong = new MessageCount();
  public final MessageCount discoverOutPong = new MessageCount();
  public final MessageCount discoverInFindNode = new MessageCount();
  public final MessageCount discoverOutFindNode = new MessageCount();
  public final MessageCount discoverInNeighbours = new MessageCount();
  public final MessageCount discoverOutNeighbours = new MessageCount();

  //tcp p2p
  public final MessageCount p2pInHello = new MessageCount();
  public final MessageCount p2pOutHello = new MessageCount();
  public final MessageCount p2pInPing = new MessageCount();
  public final MessageCount p2pOutPing = new MessageCount();
  public final MessageCount p2pInPong = new MessageCount();
  public final MessageCount p2pOutPong = new MessageCount();
  public final MessageCount p2pInDisconnect = new MessageCount();
  public final MessageCount p2pOutDisconnect = new MessageCount();

  //tcp
  public final MessageCount unxInMessage = new MessageCount();
  public final MessageCount unxOutMessage = new MessageCount();

  public final MessageCount unxInSyncBlockChain = new MessageCount();
  public final MessageCount unxOutSyncBlockChain = new MessageCount();
  public final MessageCount unxInBlockChainInventory = new MessageCount();
  public final MessageCount unxOutBlockChainInventory = new MessageCount();

  public final MessageCount unxInUnxInventory = new MessageCount();
  public final MessageCount unxOutUnxInventory = new MessageCount();
  public final MessageCount unxInUnxInventoryElement = new MessageCount();
  public final MessageCount unxOutUnxInventoryElement = new MessageCount();

  public final MessageCount unxInBlockInventory = new MessageCount();
  public final MessageCount unxOutBlockInventory = new MessageCount();
  public final MessageCount unxInBlockInventoryElement = new MessageCount();
  public final MessageCount unxOutBlockInventoryElement = new MessageCount();

  public final MessageCount unxInUnxFetchInvData = new MessageCount();
  public final MessageCount unxOutUnxFetchInvData = new MessageCount();
  public final MessageCount unxInUnxFetchInvDataElement = new MessageCount();
  public final MessageCount unxOutUnxFetchInvDataElement = new MessageCount();

  public final MessageCount unxInBlockFetchInvData = new MessageCount();
  public final MessageCount unxOutBlockFetchInvData = new MessageCount();
  public final MessageCount unxInBlockFetchInvDataElement = new MessageCount();
  public final MessageCount unxOutBlockFetchInvDataElement = new MessageCount();


  public final MessageCount unxInUnx = new MessageCount();
  public final MessageCount unxOutUnx = new MessageCount();
  public final MessageCount unxInUnxs = new MessageCount();
  public final MessageCount unxOutUnxs = new MessageCount();
  public final MessageCount unxInBlock = new MessageCount();
  public final MessageCount unxOutBlock = new MessageCount();
  public final MessageCount unxOutAdvBlock = new MessageCount();

  public void addUdpInMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, true);
  }

  public void addUdpOutMessage(UdpMessageTypeEnum type) {
    addUdpMessage(type, false);
  }

  public void addTcpInMessage(Message msg) {
    addTcpMessage(msg, true);
  }

  public void addTcpOutMessage(Message msg) {
    addTcpMessage(msg, false);
  }

  private void addUdpMessage(UdpMessageTypeEnum type, boolean flag) {
    switch (type) {
      case DISCOVER_PING:
        if (flag) {
          discoverInPing.add();
        } else {
          discoverOutPing.add();
        }
        break;
      case DISCOVER_PONG:
        if (flag) {
          discoverInPong.add();
        } else {
          discoverOutPong.add();
        }
        break;
      case DISCOVER_FIND_NODE:
        if (flag) {
          discoverInFindNode.add();
        } else {
          discoverOutFindNode.add();
        }
        break;
      case DISCOVER_NEIGHBORS:
        if (flag) {
          discoverInNeighbours.add();
        } else {
          discoverOutNeighbours.add();
        }
        break;
      default:
        break;
    }
  }

  private void addTcpMessage(Message msg, boolean flag) {

    if (flag) {
      unxInMessage.add();
    } else {
      unxOutMessage.add();
    }

    switch (msg.getType()) {
      case P2P_HELLO:
        if (flag) {
          p2pInHello.add();
        } else {
          p2pOutHello.add();
        }
        break;
      case P2P_PING:
        if (flag) {
          p2pInPing.add();
        } else {
          p2pOutPing.add();
        }
        break;
      case P2P_PONG:
        if (flag) {
          p2pInPong.add();
        } else {
          p2pOutPong.add();
        }
        break;
      case P2P_DISCONNECT:
        if (flag) {
          p2pInDisconnect.add();
        } else {
          p2pOutDisconnect.add();
        }
        break;
      case SYNC_BLOCK_CHAIN:
        if (flag) {
          unxInSyncBlockChain.add();
        } else {
          unxOutSyncBlockChain.add();
        }
        break;
      case BLOCK_CHAIN_INVENTORY:
        if (flag) {
          unxInBlockChainInventory.add();
        } else {
          unxOutBlockChainInventory.add();
        }
        break;
      case INVENTORY:
        InventoryMessage inventoryMessage = (InventoryMessage) msg;
        int inventorySize = inventoryMessage.getInventory().getIdsCount();
        messageProcess(inventoryMessage.getInvMessageType(),
                unxInUnxInventory, unxInUnxInventoryElement, unxInBlockInventory,
                unxInBlockInventoryElement, unxOutUnxInventory, unxOutUnxInventoryElement,
                unxOutBlockInventory, unxOutBlockInventoryElement,
                flag, inventorySize);
        break;
      case FETCH_INV_DATA:
        FetchInvDataMessage fetchInvDataMessage = (FetchInvDataMessage) msg;
        int fetchSize = fetchInvDataMessage.getInventory().getIdsCount();
        messageProcess(fetchInvDataMessage.getInvMessageType(),
                unxInUnxFetchInvData, unxInUnxFetchInvDataElement, unxInBlockFetchInvData,
                unxInBlockFetchInvDataElement, unxOutUnxFetchInvData, unxOutUnxFetchInvDataElement,
                unxOutBlockFetchInvData, unxOutBlockFetchInvDataElement,
                flag, fetchSize);
        break;
      case UNXS:
        TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
        if (flag) {
          unxInUnxs.add();
          unxInUnx.add(transactionsMessage.getTransactions().getTransactionsCount());
        } else {
          unxOutUnxs.add();
          unxOutUnx.add(transactionsMessage.getTransactions().getTransactionsCount());
        }
        break;
      case UNX:
        if (flag) {
          unxInMessage.add();
        } else {
          unxOutMessage.add();
        }
        break;
      case BLOCK:
        if (flag) {
          unxInBlock.add();
        }
        unxOutBlock.add();
        break;
      default:
        break;
    }
  }
  
  
  private void messageProcess(MessageTypes messageType,
                              MessageCount inUnx,
                              MessageCount inUnxEle,
                              MessageCount inBlock,
                              MessageCount inBlockEle,
                              MessageCount outUnx,
                              MessageCount outUnxEle,
                              MessageCount outBlock,
                              MessageCount outBlockEle,
                              boolean flag, int size) {
    if (flag) {
      if (messageType == MessageTypes.UNX) {
        inUnx.add();
        inUnxEle.add(size);
      } else {
        inBlock.add();
        inBlockEle.add(size);
      }
    } else {
      if (messageType == MessageTypes.UNX) {
        outUnx.add();
        outUnxEle.add(size);
      } else {
        outBlock.add();
        outBlockEle.add(size);
      }
    }
  }

}
