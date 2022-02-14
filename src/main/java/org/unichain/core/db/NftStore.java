package org.unichain.core.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftCapsule;

@Component
public class NftStore extends UnichainStoreWithRevoking<NftCapsule> {

    @Autowired
    protected NftStore(@Value("nft-index") String dbName) {
        super(dbName);
    }

    @Override
    public NftCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public void put(NftCapsule item) {
        byte[] lowerSymbol = item.getSymbol().toLowerCase().getBytes();
        super.put(lowerSymbol, item);
    }

    @Override
    public void close() {
        super.close();
    }
}
