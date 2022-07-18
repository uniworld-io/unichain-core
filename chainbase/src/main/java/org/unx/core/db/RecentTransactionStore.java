package org.unx.core.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unx.core.capsule.BytesCapsule;
import org.unx.core.exception.ItemNotFoundException;

@Component
public class RecentTransactionStore extends UnxStoreWithRevoking<BytesCapsule> {

  @Autowired
  private RecentTransactionStore(@Value("recent-transaction") String dbName) {
    super(dbName);
  }

  @Override
  public BytesCapsule get(byte[] key) throws ItemNotFoundException {
    byte[] value = revokingDB.get(key);
    return new BytesCapsule(value);
  }
}
