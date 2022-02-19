package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftAccountTemplateRelationCapsule;
import org.unichain.core.capsule.NftTemplateCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

//@todo nft review
@Slf4j(topic = "DB")
@Component
public class NftAccountTemplateStore extends UnichainStoreWithRevoking<NftAccountTemplateRelationCapsule> {

  @Autowired
  protected NftAccountTemplateStore(@Value("nft-acc-template-relation") String dbName) {
    super(dbName);
  }

  @Override
  public NftAccountTemplateRelationCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<NftAccountTemplateRelationCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
