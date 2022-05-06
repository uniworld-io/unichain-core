package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.TokenSymbolAddressIndexCapsule;

@Slf4j(topic = "DB")
@Component
public class TokenSymbolAddrIndexStore extends UnichainStoreWithRevoking<TokenSymbolAddressIndexCapsule> {

  @Autowired
  protected TokenSymbolAddrIndexStore(@Value("token-index-symboladdr") String dbName) {
    super(dbName);
  }

  @Override
  public TokenSymbolAddressIndexCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }
}
