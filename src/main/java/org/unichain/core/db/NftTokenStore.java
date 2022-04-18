package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftTokenCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

//@todo nft review
@Slf4j(topic = "DB")
@Component
public class NftTokenStore extends UnichainStoreWithRevoking<NftTokenCapsule> {

  @Autowired
  protected NftTokenStore(@Value("nft-token") String dbName) {
    super(dbName);
  }

  @Override
  public NftTokenCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<NftTokenCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
