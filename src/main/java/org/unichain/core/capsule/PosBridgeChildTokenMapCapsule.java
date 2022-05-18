package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.protos.Protocol;

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

}
