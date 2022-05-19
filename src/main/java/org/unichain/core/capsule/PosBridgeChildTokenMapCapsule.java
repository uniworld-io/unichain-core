package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.protos.Protocol;

import java.util.Objects;

@Slf4j(topic = "capsule")
public class PosBridgeChildTokenMapCapsule implements ProtoCapsule<Protocol.PostBridgeChildTokenMap>{

    private Protocol.PostBridgeChildTokenMap tokenMap;

    public PosBridgeChildTokenMapCapsule(byte[] data) {
        try {
            this.tokenMap = Protocol.PostBridgeChildTokenMap.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.debug(e.getMessage());
        }
    }

    public PosBridgeChildTokenMapCapsule(Protocol.PostBridgeChildTokenMap tokenMap) {
        this.tokenMap = tokenMap;
    }

    @Override
    public byte[] getData() {
        return tokenMap.toByteArray();
    }

    @Override
    public Protocol.PostBridgeChildTokenMap getInstance() {
        return tokenMap;
    }

    public String getRootToken(){
        return tokenMap.getRootToken();
    }

    public long getRootChainId(){
        return tokenMap.getRootChainId();
    }

    public String getChildToken(){
        return tokenMap.getChildToken();
    }

    public int getTokenType(){
        return tokenMap.getTokenType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PosBridgeChildTokenMapCapsule that = (PosBridgeChildTokenMapCapsule) o;
        String thisKeyChild = PosBridgeUtil.makeTokenMapKey(Wallet.getChainId(), this.getChildToken());
        String thisKeyRoot = PosBridgeUtil.makeTokenMapKey(this.getRootChainId(), this.getRootToken());

        String thatKeyChild = PosBridgeUtil.makeTokenMapKey(Wallet.getChainId(), that.getChildToken());
        String thatKeyRoot = PosBridgeUtil.makeTokenMapKey(that.getRootChainId(), that.getRootToken());

        return thisKeyRoot.equals(thatKeyRoot) && thisKeyChild.equals(thatKeyChild);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenMap);
    }

}
