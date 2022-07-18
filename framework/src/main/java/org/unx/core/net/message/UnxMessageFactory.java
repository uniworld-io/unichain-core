package org.unx.core.net.message;

import org.apache.commons.lang3.ArrayUtils;
import org.unx.common.overlay.message.MessageFactory;
import org.unx.core.exception.P2pException;
import org.unx.core.metrics.MetricsKey;
import org.unx.core.metrics.MetricsUtil;

/**
 * msg factory.
 */
public class UnxMessageFactory extends MessageFactory {

  private static final String DATA_LEN = ", len=";

  @Override
  public UnxMessage create(byte[] data) throws Exception {
    boolean isException = false;
    try {
      byte type = data[0];
      byte[] rawData = ArrayUtils.subarray(data, 1, data.length);
      return create(type, rawData);
    } catch (final P2pException e) {
      isException = true;
      throw e;
    } catch (final Exception e) {
      isException = true;
      throw new P2pException(P2pException.TypeEnum.PARSE_MESSAGE_FAILED,
          "type=" + data[0] + DATA_LEN + data.length + ", error msg: " + e.getMessage());
    } finally {
      if (isException) {
        MetricsUtil.counterInc(MetricsKey.NET_ERROR_PROTO_COUNT);
      }
    }
  }

  private UnxMessage create(byte type, byte[] packed) throws Exception {
    MessageTypes receivedTypes = MessageTypes.fromByte(type);
    if (receivedTypes == null) {
      throw new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE,
          "type=" + type + DATA_LEN + packed.length);
    }
    switch (receivedTypes) {
      case UNX:
        return new TransactionMessage(packed);
      case BLOCK:
        return new BlockMessage(packed);
      case UNXS:
        return new TransactionsMessage(packed);
      case BLOCKS:
        return new BlocksMessage(packed);
      case INVENTORY:
        return new InventoryMessage(packed);
      case FETCH_INV_DATA:
        return new FetchInvDataMessage(packed);
      case SYNC_BLOCK_CHAIN:
        return new SyncBlockChainMessage(packed);
      case BLOCK_CHAIN_INVENTORY:
        return new ChainInventoryMessage(packed);
      case ITEM_NOT_FOUND:
        return new ItemNotFound();
      case FETCH_BLOCK_HEADERS:
        return new FetchBlockHeadersMessage(packed);
      case UNX_INVENTORY:
        return new TransactionInventoryMessage(packed);
      case PBFT_COMMIT_MSG:
        return new PbftCommitMessage(packed);
      default:
        throw new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE,
            receivedTypes.toString() + DATA_LEN + packed.length);
    }
  }
}
