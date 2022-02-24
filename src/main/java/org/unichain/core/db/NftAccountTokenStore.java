package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftAccountTemplateRelationCapsule;
import org.unichain.core.capsule.NftAccountTokenRelationCapsule;
import org.unichain.core.capsule.NftTokenCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class NftAccountTokenStore extends UnichainStoreWithRevoking<NftAccountTokenRelationCapsule> {

  @Autowired
  protected NftAccountTokenStore(@Value("nft-acc-token-relation") String dbName) {
    super(dbName);
  }

  @Override
  public NftAccountTokenRelationCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<NftAccountTokenRelationCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  public void save(byte[] ownerAddr, NftTokenCapsule nftTokenCapsule){
    //@todo later
  }
}
