package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.urc721.Urc721AccountContractRelationCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class Urc721AccountContractRelationStore extends UnichainStoreWithRevoking<Urc721AccountContractRelationCapsule> {

  @Autowired
  protected Urc721AccountContractRelationStore(@Value("urc721-acc-contract-relation") String dbName) {
    super(dbName);
  }

  @Override
  public Urc721AccountContractRelationCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<Urc721AccountContractRelationCapsule> getAll() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
