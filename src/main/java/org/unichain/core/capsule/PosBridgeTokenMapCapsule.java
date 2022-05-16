package org.unichain.core.capsule;


import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.protos.Protocol;


@Slf4j(topic = "capsule")
public class PosBridgeTokenMapCapsule implements ProtoCapsule<Protocol.PostBridgeTokenMap>{

    private Protocol.PostBridgeTokenMap tokenMap;

    public PosBridgeTokenMapCapsule(byte[] data) {
        try {
            this.tokenMap = Protocol.PostBridgeTokenMap.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.debug(e.getMessage());
        }
    }

    public PosBridgeTokenMapCapsule(Protocol.PostBridgeTokenMap tokenMap) {
        this.tokenMap = tokenMap;
    }

    @Override
    public byte[] getData() {
        return tokenMap.toByteArray();
    }

    @Override
    public Protocol.PostBridgeTokenMap getInstance() {
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

    public long getRootChainId(){
        return tokenMap.getRootChainId();
    }

    public int getAssetType() {
        return tokenMap.getAssetType();
    }
}
