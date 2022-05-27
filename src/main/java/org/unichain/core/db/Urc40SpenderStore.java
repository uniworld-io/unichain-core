package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.urc40.Urc40SpenderCapsule;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class Urc40SpenderStore extends UnichainStoreWithRevoking<Urc40SpenderCapsule> {

  @Autowired
  protected Urc40SpenderStore(@Value("urc40-spender") String dbName) {
    super(dbName);
  }

  @Override
  public Urc40SpenderCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<Urc40SpenderCapsule> getAllTokens() {
    return Streams.stream(iterator())
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
  }
}
