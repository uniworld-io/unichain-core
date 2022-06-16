package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.urc20.Urc20FutureTokenCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class Urc20FutureTransferStore extends UnichainStoreWithRevoking<Urc20FutureTokenCapsule> {

  @Autowired
  protected Urc20FutureTransferStore(@Value("urc20-future") String dbName) {
    super(dbName);
  }

  @Override
  public Urc20FutureTokenCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<Urc20FutureTokenCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
