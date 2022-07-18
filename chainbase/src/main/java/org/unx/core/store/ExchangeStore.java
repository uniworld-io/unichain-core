package org.unx.core.store;

import com.google.common.collect.Streams;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unx.core.capsule.ExchangeCapsule;
import org.unx.core.db.UnxStoreWithRevoking;
import org.unx.core.exception.ItemNotFoundException;

@Component
public class ExchangeStore extends UnxStoreWithRevoking<ExchangeCapsule> {

  @Autowired
  protected ExchangeStore(@Value("exchange") String dbName) {
    super(dbName);
  }

  @Override
  public ExchangeCapsule get(byte[] key) throws ItemNotFoundException {
    byte[] value = revokingDB.get(key);
    return new ExchangeCapsule(value);
  }

  /**
   * get all exchanges.
   */
  public List<ExchangeCapsule> getAllExchanges() {
    return Streams.stream(iterator())
        .map(Map.Entry::getValue)
        .sorted(
            (ExchangeCapsule a, ExchangeCapsule b) -> a.getCreateTime() <= b.getCreateTime() ? 1
                : -1)
        .collect(Collectors.toList());
  }
}