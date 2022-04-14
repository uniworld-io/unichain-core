package org.unichain.core.db.store;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.db.ChaosDatabase;
import org.unichain.core.db.ChaosDatabase.KhaosBlock;
import org.unichain.core.exception.BadItemException;
import org.unichain.core.exception.StoreException;

import java.util.List;
import java.util.Objects;

@Slf4j(topic = "DB")
@Component
public class TransactionStore extends UnichainStoreWithRevoking<TransactionCapsule> {

  @Autowired
  private BlockStore blockStore;

  @Autowired
  private ChaosDatabase chaosDatabase;

  @Autowired
  private TransactionStore(@Value("trans") String dbName) {
    super(dbName);
  }

  @Override
  public void put(byte[] key, TransactionCapsule item) {
    if (Objects.isNull(item) || item.getBlockNum() == -1) {
      super.put(key, item);
    } else {
      revokingDB.put(key, ByteArray.fromLong(item.getBlockNum()));
    }

    if (Objects.nonNull(indexHelper)) {
      indexHelper.update(item.getInstance());
    }
  }

  private TransactionCapsule getTransactionFromBlockStore(byte[] key, long blockNum) {
    List<BlockCapsule> blocksList = blockStore.getLimitNumber(blockNum, 1);
    if (blocksList.size() != 0) {
      for (TransactionCapsule e : blocksList.get(0).getTransactions()) {
        if (e.getTransactionId().equals(Sha256Hash.wrap(key))) {
          return e;
        }
      }
    }
    return null;
  }

  private TransactionCapsule getTransactionFromKhaosDatabase(byte[] key, long high) {
    List<KhaosBlock> khaosBlocks = chaosDatabase.getMiniStore().getBlockByNum(high);
    for (KhaosBlock bl : khaosBlocks) {
      for (TransactionCapsule e : bl.getBlk().getTransactions()) {
        if (e.getTransactionId().equals(Sha256Hash.wrap(key))) {
          return e;
        }
      }
    }
    return null;
  }

  public long getBlockNumber(byte[] key) throws BadItemException {
    byte[] value = revokingDB.getUnchecked(key);
    if (ArrayUtils.isEmpty(value)) {
      return -1;
    }

    if (value.length == 8) {
      return ByteArray.toLong(value);
    }
    TransactionCapsule transactionCapsule = new TransactionCapsule(value);
    return transactionCapsule.getBlockNum();
  }

  @Override
  public TransactionCapsule get(byte[] key) throws BadItemException {
    byte[] value = revokingDB.getUnchecked(key);
    if (ArrayUtils.isEmpty(value)) {
      return null;
    }
    TransactionCapsule transactionCapsule = null;
    if (value.length == 8) {
      long blockHigh = ByteArray.toLong(value);
      transactionCapsule = getTransactionFromBlockStore(key, blockHigh);
      if (transactionCapsule == null) {
        transactionCapsule = getTransactionFromKhaosDatabase(key, blockHigh);
      }
    }

    return transactionCapsule == null ? new TransactionCapsule(value) : transactionCapsule;
  }

  @Override
  public TransactionCapsule getUnchecked(byte[] key) {
    try {
      return get(key);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * get total transaction.
   */
  @Deprecated
  public long getTotalTransactions() {
    return 0; //Streams.stream(iterator()).count();
  }

  @Override
  public void delete(byte[] key) {
    deleteIndex(key);
    super.delete(key);
  }

  private void deleteIndex(byte[] key) {
    if (Objects.nonNull(indexHelper)) {
      TransactionCapsule item;
      try {
        item = get(key);
        if (Objects.nonNull(item)) {
          indexHelper.remove(item.getInstance());
        }
      } catch (StoreException e) {
        logger.error("deleteIndex: ", e);
      }
    }
  }
}
