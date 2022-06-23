package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.protos.Protocol;

@Slf4j(topic = "capsule")
public class PosBridgeTransactionOriginCapsule implements ProtoCapsule<Protocol.PosBridgeTransactionOrigin>{

    private Protocol.PosBridgeTransactionOrigin instance;

    public PosBridgeTransactionOriginCapsule(byte[] data) {
        try {
            this.instance = Protocol.PosBridgeTransactionOrigin.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.debug(e.getMessage());
        }
    }

    public PosBridgeTransactionOriginCapsule(Protocol.PosBridgeTransactionOrigin instance) {
       this.instance = instance;
    }

    @Override
    public byte[] getData() {
        return instance.toByteArray();
    }

    @Override
    public Protocol.PosBridgeTransactionOrigin getInstance() {
        return instance;
    }
}
