package org.unichain.core.db;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.BytesCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.db.KhaosDatabase.KhaosBlock;
import org.unichain.core.db2.common.TxCacheDB;
import org.unichain.core.exception.BadItemException;
import org.unichain.core.exception.StoreException;

@Slf4j
public class TransactionCache extends UnichainStoreWithRevoking<BytesCapsule> {

  @Autowired
  public TransactionCache(@Value("trans-cache") String dbName) {
    super(dbName, TxCacheDB.class);
  }
}
