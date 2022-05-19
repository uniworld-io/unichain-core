package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.protos.Protocol;

import java.util.Objects;

@Slf4j(topic = "capsule")
public class PosBridgeRootTokenMapCapsule implements ProtoCapsule<Protocol.PostBridgeRootTokenMap>{

    private Protocol.PostBridgeRootTokenMap tokenMap;

    public PosBridgeRootTokenMapCapsule(byte[] data) {
        try {
            this.tokenMap = Protocol.PostBridgeRootTokenMap.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.debug(e.getMessage());
        }
    }

    public PosBridgeRootTokenMapCapsule(Protocol.PostBridgeRootTokenMap tokenMap) {
        this.tokenMap = tokenMap;
    }

    @Override
    public byte[] getData() {
        return tokenMap.toByteArray();
    }

    @Override
    public Protocol.PostBridgeRootTokenMap getInstance() {
        return tokenMap;
    }

    public String getChildToken(){
        return tokenMap.getChildToken();
    }

    public String getRootToken(){
        return tokenMap.getRootToken();
    }

    public long getChildChainId(){
        return tokenMap.getChildChainId();
    }

    public int getTokenType() {
        return tokenMap.getTokenType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PosBridgeRootTokenMapCapsule that = (PosBridgeRootTokenMapCapsule) o;
        String thisKeyChild = PosBridgeUtil.makeTokenMapKey(this.getChildChainId(), this.getChildToken());
        String thisKeyRoot = PosBridgeUtil.makeTokenMapKey(Wallet.getChainId(), this.getRootToken());

        String thatKeyChild = PosBridgeUtil.makeTokenMapKey(that.getChildChainId(), that.getChildToken());
        String thatKeyRoot = PosBridgeUtil.makeTokenMapKey(Wallet.getChainId(), that.getRootToken());

        return thisKeyRoot.equals(thatKeyRoot) && thisKeyChild.equals(thatKeyChild);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenMap);
    }
}