package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.urc40.Urc40FutureTokenCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class Urc40FutureTransferStore extends UnichainStoreWithRevoking<Urc40FutureTokenCapsule> {

  @Autowired
  protected Urc40FutureTransferStore(@Value("urc40-future") String dbName) {
    super(dbName);
  }

  @Override
  public Urc40FutureTokenCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<Urc40FutureTokenCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
