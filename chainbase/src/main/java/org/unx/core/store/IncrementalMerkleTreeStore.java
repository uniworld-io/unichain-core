package org.unx.core.store;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unx.core.capsule.IncrementalMerkleTreeCapsule;
import org.unx.core.db.UnxStoreWithRevoking;

@Slf4j(topic = "DB")
@Component
public class IncrementalMerkleTreeStore
    extends UnxStoreWithRevoking<IncrementalMerkleTreeCapsule> {

  @Autowired
  public IncrementalMerkleTreeStore(@Value("IncrementalMerkleTree") String dbName) {
    super(dbName);
  }

  @Override
  public IncrementalMerkleTreeCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new IncrementalMerkleTreeCapsule(value);
  }

  public boolean contain(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return !ArrayUtils.isEmpty(value);
  }

}
