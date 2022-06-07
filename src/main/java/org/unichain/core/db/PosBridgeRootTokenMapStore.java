package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeRootTokenMapCapsule;
import org.unichain.protos.Protocol;


@Slf4j(topic = "DB")
@Component
public class PosBridgeRootTokenMapStore extends UnichainStoreWithRevoking<PosBridgeRootTokenMapCapsule>{


    @Autowired
    protected PosBridgeRootTokenMapStore(@Value("posbridge-root-tokenmap") String dbName) {
        super(dbName);
    }

    @Override
    public PosBridgeRootTokenMapCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public boolean ensureNotMapped(long chainId, String token){
        String key = PosBridgeUtil.makeTokenMapKey(chainId, token);
        return !has(key.getBytes());
    }

    public void mapToken(int tokenType, String rootToken, long childChainId, String childToken){
        var capsule = new PosBridgeRootTokenMapCapsule(
                Protocol.PostBridgeRootTokenMap.newBuilder()
                        .setTokenType(tokenType)
                        .setRootToken(rootToken)
                        .setChildChainId(childChainId)
                        .setChildToken(childToken)
                        .build()
        );
        var keyRoot = PosBridgeUtil.makeTokenMapKey(childChainId, rootToken);
        var keyChild = PosBridgeUtil.makeTokenMapKey(childChainId, childToken);
        put(keyRoot.getBytes(), capsule);
        put(keyChild.getBytes(), capsule);
    }

    public void unmap(String rootToken, long childChainId, String childToken){
        var keyRoot = PosBridgeUtil.makeTokenMapKey(childChainId, rootToken);
        var keyChild = PosBridgeUtil.makeTokenMapKey(childChainId, childToken);

        if(has(keyRoot.getBytes())){
            var capsule = get(keyRoot.getBytes());
            delete(PosBridgeUtil.makeTokenMapKey(capsule.getChildChainId(), capsule.getChildToken()).getBytes());
            delete(PosBridgeUtil.makeTokenMapKey(capsule.getChildChainId(), capsule.getRootToken()).getBytes());
        }

        if(has(keyChild.getBytes())){
            var capsule = get(keyChild.getBytes());
            delete(PosBridgeUtil.makeTokenMapKey(capsule.getChildChainId(), capsule.getChildToken()).getBytes());
            delete(PosBridgeUtil.makeTokenMapKey(capsule.getChildChainId(), capsule.getRootToken()).getBytes());
        }
    }
}
