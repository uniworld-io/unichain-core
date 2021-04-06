package org.unichain.common.overlay.discover.node.statistics;

import lombok.extern.slf4j.Slf4j;
import org.unichain.common.net.udp.message.UdpMessageTypeEnum;
import org.unichain.common.overlay.message.Message;
import org.unichain.core.net.message.FetchInvDataMessage;
import org.unichain.core.net.message.InventoryMessage;
import org.unichain.core.net.message.MessageTypes;
import org.unichain.core.net.message.TransactionsMessage;

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

  //tcp unichain
  public final MessageCount unichainInMessage = new MessageCount();
  public final MessageCount unichainOutMessage = new MessageCount();

  public final MessageCount unichainInSyncBlockChain = new MessageCount();
  public final MessageCount unichainOutSyncBlockChain = new MessageCount();
  public final MessageCount unichainInBlockChainInventory = new MessageCount();
  public final MessageCount unichainOutBlockChainInventory = new MessageCount();

  public final MessageCount unichainInUnxInventory = new MessageCount();
  public final MessageCount unichainOutUnxInventory = new MessageCount();
  public final MessageCount unichainInUnxInventoryElement = new MessageCount();
  public final MessageCount unichainOutUnxInventoryElement = new MessageCount();

  public final MessageCount unichainInBlockInventory = new MessageCount();
  public final MessageCount unichainOutBlockInventory = new MessageCount();
  public final MessageCount unichainInBlockInventoryElement = new MessageCount();
  public final MessageCount unichainOutBlockInventoryElement = new MessageCount();

  public final MessageCount unichainInUnxFetchInvData = new MessageCount();
  public final MessageCount unichainOutUnxFetchInvData = new MessageCount();
  public final MessageCount unichainInUnxFetchInvDataElement = new MessageCount();
  public final MessageCount unichainOutUnxFetchInvDataElement = new MessageCount();

  public final MessageCount unichainInBlockFetchInvData = new MessageCount();
  public final MessageCount unichainOutBlockFetchInvData = new MessageCount();
  public final MessageCount unichainInBlockFetchInvDataElement = new MessageCount();
  public final MessageCount unichainOutBlockFetchInvDataElement = new MessageCount();


  public final MessageCount unichainInUnx = new MessageCount();
  public final MessageCount unichainOutUnx = new MessageCount();
  public final MessageCount unichainInUnxs = new MessageCount();
  public final MessageCount unichainOutUnxs = new MessageCount();
  public final MessageCount unichainInBlock = new MessageCount();
  public final MessageCount unichainOutBlock = new MessageCount();
  public final MessageCount unichainOutAdvBlock = new MessageCount();

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
      unichainInMessage.add();
    } else {
      unichainOutMessage.add();
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
          unichainInSyncBlockChain.add();
        } else {
          unichainOutSyncBlockChain.add();
        }
        break;
      case BLOCK_CHAIN_INVENTORY:
        if (flag) {
          unichainInBlockChainInventory.add();
        } else {
          unichainOutBlockChainInventory.add();
        }
        break;
      case INVENTORY:
        InventoryMessage inventoryMessage = (InventoryMessage) msg;
        int inventorySize = inventoryMessage.getInventory().getIdsCount();
        if (flag) {
          if (inventoryMessage.getInvMessageType() == MessageTypes.UNW) {
            unichainInUnxInventory.add();
            unichainInUnxInventoryElement.add(inventorySize);
          } else {
            unichainInBlockInventory.add();
            unichainInBlockInventoryElement.add(inventorySize);
          }
        } else {
          if (inventoryMessage.getInvMessageType() == MessageTypes.UNW) {
            unichainOutUnxInventory.add();
            unichainOutUnxInventoryElement.add(inventorySize);
          } else {
            unichainOutBlockInventory.add();
            unichainOutBlockInventoryElement.add(inventorySize);
          }
        }
        break;
      case FETCH_INV_DATA:
        FetchInvDataMessage fetchInvDataMessage = (FetchInvDataMessage) msg;
        int fetchSize = fetchInvDataMessage.getInventory().getIdsCount();
        if (flag) {
          if (fetchInvDataMessage.getInvMessageType() == MessageTypes.UNW) {
            unichainInUnxFetchInvData.add();
            unichainInUnxFetchInvDataElement.add(fetchSize);
          } else {
            unichainInBlockFetchInvData.add();
            unichainInBlockFetchInvDataElement.add(fetchSize);
          }
        } else {
          if (fetchInvDataMessage.getInvMessageType() == MessageTypes.UNW) {
            unichainOutUnxFetchInvData.add();
            unichainOutUnxFetchInvDataElement.add(fetchSize);
          } else {
            unichainOutBlockFetchInvData.add();
            unichainOutBlockFetchInvDataElement.add(fetchSize);
          }
        }
        break;
      case UNWS:
        TransactionsMessage transactionsMessage = (TransactionsMessage) msg;
        if (flag) {
          unichainInUnxs.add();
          unichainInUnx.add(transactionsMessage.getTransactions().getTransactionsCount());
        } else {
          unichainOutUnxs.add();
          unichainOutUnx.add(transactionsMessage.getTransactions().getTransactionsCount());
        }
        break;
      case UNW:
        if (flag) {
          unichainInMessage.add();
        } else {
          unichainOutMessage.add();
        }
        break;
      case BLOCK:
        if (flag) {
          unichainInBlock.add();
        }
        unichainOutBlock.add();
        break;
      default:
        break;
    }
  }

}
