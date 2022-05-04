package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.PosBridgeTokenMappingCapsule;

@Slf4j(topic = "DB")
@Component
public class PosBridgeTokenMapChild2RootStore extends UnichainStoreWithRevoking<PosBridgeTokenMappingCapsule> {

  @Autowired
  protected PosBridgeTokenMapChild2RootStore(@Value("posbridge-tokenmap-child2root") String dbName) {
    super(dbName);
  }

  @Override
  public PosBridgeTokenMappingCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public void put(PosBridgeTokenMappingCapsule capsule, byte[] key){
    put(key, capsule);
  }
}
