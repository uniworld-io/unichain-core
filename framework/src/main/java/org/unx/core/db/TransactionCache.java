package org.unx.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.unx.core.capsule.BytesCapsule;
import org.unx.core.db2.common.TxCacheDB;

@Slf4j
public class TransactionCache extends UnxStoreWithRevoking<BytesCapsule> {

  @Autowired
  public TransactionCache(@Value("trans-cache") String dbName,
                          RecentTransactionStore recentTransactionStore) {
    super(new TxCacheDB(dbName, recentTransactionStore));
  }
}
