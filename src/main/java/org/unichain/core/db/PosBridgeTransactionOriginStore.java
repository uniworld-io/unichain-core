package org.unichain.core.db;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.PosBridgeTransactionOriginCapsule;

@Slf4j(topic = "DB")
@Component
public class PosBridgeTransactionOriginStore extends UnichainStoreWithRevoking<PosBridgeTransactionOriginCapsule>{

    @Autowired
    protected PosBridgeTransactionOriginStore(@Value("posbridge-tx-origin") String dbName) {
        super(dbName);
    }

    @Override
    public PosBridgeTransactionOriginCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public void put(long chainId, String tx, PosBridgeTransactionOriginCapsule item) {
        byte[] key = makeKey(chainId, tx);
        put(key, item);
    }

    public static byte[] makeKey(long chainId, String tx){
        return (Long.toHexString(chainId) + "_" + tx).getBytes();
    }

    public boolean has(long chainId, String tx) {
        return super.has(makeKey(chainId, tx));
    }
}
