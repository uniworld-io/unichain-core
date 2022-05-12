package org.unichain.core.db;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.protos.Protocol;

@Slf4j(topic = "DB")
@Component
public class PosBridgeConfigStore extends UnichainStoreWithRevoking<PosBridgeConfigCapsule> {

  @Autowired
  protected PosBridgeConfigStore(@Value("posbridge-config") String dbName) {
    super(dbName);
    if(!has())
    {
      /**
       * initialize default config
       */
      var config = Protocol.PosBridgeConfig.newBuilder()
              .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_GENESIS_ADMIN_WALLET)))
              .setMinValidator(1)
              .setConsensusRate(75)
              .setInitialized(false)
              .build();
      put(new PosBridgeConfigCapsule(config));
    }
  }

  @Override
  public PosBridgeConfigCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public PosBridgeConfigCapsule get(){
    return get(PosBridgeConfigCapsule.DEFAULT_KEY);
  }

  public boolean has(){
    return has(PosBridgeConfigCapsule.DEFAULT_KEY);
  }

  public void put(PosBridgeConfigCapsule capsule){
    put(PosBridgeConfigCapsule.DEFAULT_KEY, capsule);
  }
}
