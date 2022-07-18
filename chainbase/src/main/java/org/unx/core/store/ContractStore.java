package org.unx.core.store;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unx.core.capsule.ContractCapsule;
import org.unx.core.db.UnxStoreWithRevoking;

import java.util.Objects;

@Slf4j(topic = "DB")
@Component
public class ContractStore extends UnxStoreWithRevoking<ContractCapsule> {

  @Autowired
  private ContractStore(@Value("contract") String dbName) {
    super(dbName);
  }

  @Override
  public ContractCapsule get(byte[] key) {
    return getUnchecked(key);
  }

  @Override
  public void put(byte[] key, ContractCapsule item) {
    if (Objects.isNull(key) || Objects.isNull(item)) {
      return;
    }

    if (item.getInstance().hasAbi()) {
      item = new ContractCapsule(item.getInstance().toBuilder().clearAbi().build());
    }
    revokingDB.put(key, item.getData());
  }

  /**
   * get total transaction.
   */
  public long getTotalContracts() {
    return Streams.stream(revokingDB.iterator()).count();
  }

  /**
   * find a transaction  by it's id.
   */
  public byte[] findContractByHash(byte[] unxHash) {
    return revokingDB.getUnchecked(unxHash);
  }

}
