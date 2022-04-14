package org.unichain.core.db.store;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.FutureTokenCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;


@Slf4j(topic = "DB")
@Component
public class FutureTokenStore extends UnichainStoreWithRevoking<FutureTokenCapsule> {

  @Autowired
  protected FutureTokenStore(@Value("token-future") String dbName) {
    super(dbName);
  }

  @Override
  public FutureTokenCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<FutureTokenCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
