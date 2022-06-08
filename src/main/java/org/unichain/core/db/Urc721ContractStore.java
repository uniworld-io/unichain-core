package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.urc721.Urc721ContractCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class Urc721ContractStore extends UnichainStoreWithRevoking<Urc721ContractCapsule> {

  @Autowired
  protected Urc721ContractStore(@Value("urc721-contract") String dbName) {
    super(dbName);
  }

  @Override
  public Urc721ContractCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<Urc721ContractCapsule> getAll() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  public void clearMinterOf(byte[] key) {
    var capsule = get(key);
    capsule.clearMinter();
    put(key, capsule);
  }
}
