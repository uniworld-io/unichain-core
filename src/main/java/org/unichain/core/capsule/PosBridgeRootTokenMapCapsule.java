package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.protos.Protocol;

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
}