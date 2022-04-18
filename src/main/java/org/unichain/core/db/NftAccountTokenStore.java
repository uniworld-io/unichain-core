package org.unichain.core.db;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.capsule.NftAccountTokenRelationCapsule;
import org.unichain.protos.Protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class NftAccountTokenStore extends UnichainStoreWithRevoking<NftAccountTokenRelationCapsule> {

    @Autowired
    protected NftAccountTokenStore(@Value("nft-acc-token-relation") String dbName) {
        super(dbName);
    }

    @Override
    public NftAccountTokenRelationCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public List<NftAccountTokenRelationCapsule> getAllTokens() {
        return Streams.stream(iterator())
                .map(Entry::getValue)
                .collect(Collectors.toList());
    }

    public void disApproveForAll(byte[] ownerAddr, byte[] toAddr) {
        NftAccountTokenRelationCapsule ownerRelation;
        if (has(ownerAddr)) {
            ownerRelation = get(ownerAddr);
            Assert.isTrue(Arrays.equals(ownerRelation.getApprovedForAll(), toAddr), "approved address miss-matched!");
            ownerRelation.clearApprovedForAll();
            put(ownerAddr, ownerRelation);
        }

        NftAccountTokenRelationCapsule toRelation;
        if (has(toAddr)) {
            toRelation = get(toAddr);
            var ownerAddrBs = ByteString.copyFrom(ownerAddr);
            Assert.isTrue(toRelation.hasApproveAll(ownerAddrBs), "approve address miss-matched!");
            toRelation.removeApproveAll(ownerAddrBs);
            put(toAddr, toRelation);
        }
    }

    public void approveForAll(byte[] ownerAddr, byte[] toAddr) {
        NftAccountTokenRelationCapsule ownerRelation;
        if (has(ownerAddr)) {
            ownerRelation = get(ownerAddr);
            if (ownerRelation.hasApprovalForAll()) {
                disApproveForAll(ownerAddr, ownerRelation.getApprovedForAll());
            }
            ownerRelation.setApprovedForAll(ByteString.copyFrom(toAddr));
        } else {
            ownerRelation = new NftAccountTokenRelationCapsule(ownerAddr,
                    Protocol.NftAccountTokenRelation.newBuilder()
                            .setOwnerAddress(ByteString.copyFrom(ownerAddr))
                            .clearHead()
                            .clearTail()
                            .setTotal(0L)
                            .setApprovedForAll(ByteString.copyFrom(toAddr))
                            .build());
        }
        put(ownerAddr, ownerRelation);

        NftAccountTokenRelationCapsule toRelation;
        if (has(toAddr)) {
            toRelation = get(toAddr);
            toRelation.addApproveAll(ByteString.copyFrom(ownerAddr));
        } else {
            toRelation = new NftAccountTokenRelationCapsule(toAddr,
                    Protocol.NftAccountTokenRelation.newBuilder()
                            .setOwnerAddress(ByteString.copyFrom(toAddr))
                            .clearHead()
                            .clearTail()
                            .setTotal(0L)
                            .putApproveAll(ByteArray.toHexString(ownerAddr), true)
                            .build());
        }
        put(toAddr, toRelation);
    }
}
