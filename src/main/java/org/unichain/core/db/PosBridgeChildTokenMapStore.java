package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeChildTokenMapCapsule;
import org.unichain.protos.Protocol;

@Slf4j(topic = "DB")
@Component
public class PosBridgeChildTokenMapStore extends UnichainStoreWithRevoking<PosBridgeChildTokenMapCapsule> {


    @Autowired
    protected PosBridgeChildTokenMapStore(@Value("posbridge-child-tokenmap") String dbName) {
        super(dbName);
    }

    @Override
    public PosBridgeChildTokenMapCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public void mapToken(int tokenType, String childToken, long rootChainId, String rootToken) {
        var capsule = new PosBridgeChildTokenMapCapsule(
                Protocol.PostBridgeChildTokenMap.newBuilder()
                        .setRootToken(rootToken)
                        .setChildToken(childToken)
                        .setRootChainId(rootChainId)
                        .setTokenType(tokenType)
                        .build()
        );
        this.unmap(childToken, rootChainId, rootToken);

        var keyRoot = PosBridgeUtil.makeTokenMapKey(rootChainId, rootToken);
        put(keyRoot.getBytes(), capsule);

        var keyChild = PosBridgeUtil.makeTokenMapKey(Wallet.getChainId(), childToken);
        put(keyChild.getBytes(), capsule);

    }

    public void unmap(String childToken, long rootChainId, String rootToken) {
        var keyRoot = PosBridgeUtil.makeTokenMapKey(rootChainId, rootToken);
        delete(keyRoot.getBytes());

        var keyChild = PosBridgeUtil.makeTokenMapKey(Wallet.getChainId(), childToken);
        delete(keyChild.getBytes());
    }
}
