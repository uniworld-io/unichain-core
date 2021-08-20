package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.FutureTransferCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;


@Slf4j(topic = "DB")
@Component
public class FutureTransferStore extends UnichainStoreWithRevoking<FutureTransferCapsule> {

  @Autowired
  protected FutureTransferStore(@Value("future-transfer") String dbName) {
    super(dbName);
  }

  @Override
  public FutureTransferCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<FutureTransferCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }
}
