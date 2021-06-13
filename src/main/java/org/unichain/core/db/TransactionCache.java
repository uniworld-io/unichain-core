package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.unichain.core.capsule.BytesCapsule;
import org.unichain.core.db2.common.TxCacheDB;

@Slf4j
public class TransactionCache extends UnichainStoreWithRevoking<BytesCapsule> {

  @Autowired
  public TransactionCache(@Value("trans-cache") String dbName) {
    super(dbName, TxCacheDB.class);
  }
}
