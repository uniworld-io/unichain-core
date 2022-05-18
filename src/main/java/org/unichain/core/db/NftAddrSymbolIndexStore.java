package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.TokenAddressSymbolIndexCapsule;

@Slf4j(topic = "DB")
@Component
@Deprecated
public class NftAddrSymbolIndexStore extends UnichainStoreWithRevoking<TokenAddressSymbolIndexCapsule> {

  @Autowired
  protected NftAddrSymbolIndexStore(@Value("nft-index-addrsymbol") String dbName) {
    super(dbName);
  }

  @Override
  public TokenAddressSymbolIndexCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }
}
