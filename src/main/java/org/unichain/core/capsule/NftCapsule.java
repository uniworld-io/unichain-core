package org.unichain.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.protos.Contract.CreateNftContract;

@Slf4j(topic = "capsule")
public class NftCapsule implements ProtoCapsule<CreateNftContract> {

    private CreateNftContract createNftContract;

    public NftCapsule(byte[] data) {
        try {
            this.createNftContract = CreateNftContract.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            logger.debug(e.getMessage());
        }
    }

    public NftCapsule(CreateNftContract createNftContract) {
        this.createNftContract = createNftContract;
    }

    public String getSymbol() {
        return createNftContract.getSymbol();
    }

    public String getName() {
        return createNftContract.getName();
    }

    public long getTotalSupply() {
        return createNftContract.getTotalSupply();
    }

    public String getTokenIndex() {
        return createNftContract.getTokenIndex();
    }

    public ByteString getMinter() {
        return createNftContract.getMinter();
    }

    public long getLastOperationTime() {
        return createNftContract.getLastOperation();
    }

    public ByteString getOwnerAddress() {
        return createNftContract.getOwner();
    }

    public void setSymbol(String symbol) {
        String symbolUppercase = symbol.toUpperCase();
        this.createNftContract = this.createNftContract.toBuilder().setSymbol(symbolUppercase).build();
    }

    public void setName(String name) {
        this.createNftContract = this.createNftContract.toBuilder().setName(name).build();
    }

    public void setTotalSupply(long totalSupply) {
        this.createNftContract = this.createNftContract.toBuilder().setTotalSupply(totalSupply).build();
    }

    public void setTokenIndex(String tokenIndex) {
        this.createNftContract = this.createNftContract.toBuilder().setTokenIndex(tokenIndex).build();
    }

    public void setMinter(ByteString minter) {
        this.createNftContract = this.createNftContract.toBuilder().setMinter(minter).build();
    }

    public void setLastOperationTime(long timestamp) {
        this.createNftContract = this.createNftContract.toBuilder().setLastOperation(timestamp).build();
    }

    public void setOwnerAddress(ByteString ownerAddress) {
        this.createNftContract = this.createNftContract.toBuilder().setOwner(ownerAddress).build();
    }

    @Override
    public byte[] getData() {
        return this.createNftContract.toByteArray();
    }

    @Override
    public CreateNftContract getInstance() {
        return this.createNftContract;
    }
}
