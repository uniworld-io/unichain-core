package org.unichain.core.db;


import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.capsule.PosBridgeTokenMapCapsule;
import org.unichain.protos.Protocol;

import java.util.Locale;

@Slf4j(topic = "DB")
@Component
public class PosBridgeTokenMapStore extends UnichainStoreWithRevoking<PosBridgeTokenMapCapsule>{

    @Autowired
    protected PosBridgeTokenMapStore(@Value("posbridge-tokenmap") String dbName) {
        super(dbName);
    }

    @Override
    public PosBridgeTokenMapCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public void mapRoot2Child(long rootChainId, String rootToken, long childChainId, String childToken, int type){
        var tokenMap = Protocol.PostBridgeTokenMap.newBuilder()
                .setRootChainId(rootChainId)
                .setRootToken(rootToken.toLowerCase(Locale.ROOT))
                .setChildChainId(childChainId)
                .setChildToken(childToken.toLowerCase(Locale.ROOT))
                .setAssetType(type)
                .build();
        String key = PosBridgeUtil.makeTokenMapKey(rootChainId, rootToken);
        put(key.getBytes(), new PosBridgeTokenMapCapsule(tokenMap));
    }

    public void mapChild2Root(long childChainId, String childToken, long rootChainId, String rootToken, int type){
        var tokenMap = Protocol.PostBridgeTokenMap.newBuilder()
                .setRootChainId(rootChainId)
                .setRootToken(rootToken.toLowerCase(Locale.ROOT))
                .setChildChainId(childChainId)
                .setChildToken(childToken.toLowerCase(Locale.ROOT))
                .setAssetType(type)
                .build();
        String key = PosBridgeUtil.makeTokenMapKey(childChainId, childToken);
        put(key.getBytes(), new PosBridgeTokenMapCapsule(tokenMap));
    }

    public boolean ensureNotMapped(long chainId, String token){
        String key = PosBridgeUtil.makeTokenMapKey(chainId, token);
        return !has(key.getBytes());
    }

    public void unmap(long rootChainId, String rootToken, long childChainId, String childToken){
        String keyRoot = PosBridgeUtil.makeTokenMapKey(rootChainId, rootToken);
        String keyChild = PosBridgeUtil.makeTokenMapKey(childChainId, childToken);
        delete(keyRoot.getBytes());
        delete(keyChild.getBytes());
    }
}
