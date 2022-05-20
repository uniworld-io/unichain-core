package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.urc721.Urc721AccountTemplateRelationCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class NftAccountTemplateStore extends UnichainStoreWithRevoking<Urc721AccountTemplateRelationCapsule> {

  @Autowired
  protected NftAccountTemplateStore(@Value("nft-acc-template-relation") String dbName) {
    super(dbName);
  }

  @Override
  public Urc721AccountTemplateRelationCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<Urc721AccountTemplateRelationCapsule> getAll() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
