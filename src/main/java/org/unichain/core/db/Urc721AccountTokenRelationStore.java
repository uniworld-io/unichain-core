package org.unichain.core.db;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.urc721.Urc721AccountTokenRelationCapsule;
import org.unichain.protos.Protocol;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class Urc721AccountTokenRelationStore extends UnichainStoreWithRevoking<Urc721AccountTokenRelationCapsule> {

    @Autowired
    protected Urc721AccountTokenRelationStore(@Value("urc721-acc-token-relation") String dbName) {
        super(dbName);
    }

    @Override
    public Urc721AccountTokenRelationCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public List<Urc721AccountTokenRelationCapsule> getAllTokens() {
        return Streams.stream(iterator())
                .map(Entry::getValue)
                .collect(Collectors.toList());
    }

    public void disApproveForAll(byte[] ownerAddr, byte[] operatorAddr, byte[] contractAddr) {
        //update owner relation
        Urc721AccountTokenRelationCapsule ownerRelation;
        if (has(ownerAddr)) {
            ownerRelation = get(ownerAddr);
            Assert.isTrue(ownerRelation.isApprovedForAll(contractAddr, operatorAddr), "approved address miss-matched!");
            ownerRelation.clearApprovedForAll(contractAddr, operatorAddr);
            put(ownerAddr, ownerRelation);
        }

        Urc721AccountTokenRelationCapsule toRelation;
        if (has(operatorAddr)) {
            toRelation = get(operatorAddr);
            var ownerAddrBs = ByteString.copyFrom(ownerAddr);
            Assert.isTrue(toRelation.hasApproveAll(ownerAddr, contractAddr), "approve address miss-matched!");
            toRelation.removeApproveAll(ownerAddr, contractAddr);
            put(operatorAddr, toRelation);
        }
    }



    public void approveForAll(byte[] ownerAddr, byte[] operatorAddr, byte[] contractAddr) {
        //update owner relation
        Urc721AccountTokenRelationCapsule ownerRelation;
        if (has(ownerAddr)) {
            ownerRelation = get(ownerAddr);
            ownerRelation.setApprovedForAll(contractAddr, operatorAddr);
        } else {
            ownerRelation = new Urc721AccountTokenRelationCapsule(ownerAddr,
                    Protocol.Urc721AccountTokenRelation.newBuilder()
                            .setOwnerAddress(ByteString.copyFrom(ownerAddr))
                            .clearHead()
                            .clearTail()
                            .setTotal(0L)
                            .clearTotals()
                            .putApprovedForAlls(Wallet.encode58Check(contractAddr), Wallet.encode58Check(operatorAddr))
                            .build());
        }
        put(ownerAddr, ownerRelation);

        //update to relation
        Urc721AccountTokenRelationCapsule operatorRelation;
        if (has(operatorAddr)) {
            operatorRelation = get(operatorAddr);
            operatorRelation.addApproveAll(ownerAddr, contractAddr);
        } else {
            operatorRelation = new Urc721AccountTokenRelationCapsule(operatorAddr,
                    Protocol.Urc721AccountTokenRelation.newBuilder()
                            .setOwnerAddress(ByteString.copyFrom(operatorAddr))
                            .clearHead()
                            .clearTail()
                            .setTotal(0L)
                            .clearTotals()
                            .build());
            operatorRelation.addApproveAll(ownerAddr, contractAddr);
        }
        put(operatorAddr, operatorRelation);
    }
}
