package org.unx.core.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unx.core.capsule.MarketAccountOrderCapsule;
import org.unx.core.db.UnxStoreWithRevoking;
import org.unx.core.exception.ItemNotFoundException;

@Component
public class MarketAccountStore extends UnxStoreWithRevoking<MarketAccountOrderCapsule> {

  @Autowired
  protected MarketAccountStore(@Value("market_account") String dbName) {
    super(dbName);
  }

  @Override
  public MarketAccountOrderCapsule get(byte[] key) throws ItemNotFoundException {
    byte[] value = revokingDB.get(key);
    return new MarketAccountOrderCapsule(value);
  }

}