package org.unichain.core.db;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftAccountTokenRelationCapsule;
import org.unichain.core.capsule.NftTokenCapsule;
import org.unichain.protos.Protocol;

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

  public void save(byte[] ownerAddr, NftTokenCapsule nftTokenCapsule){
    if(!has(ownerAddr)){
      //no token yet, create fist one
      var relation = Protocol.NftAccountTokenRelation.newBuilder()
              .setTokenId(ByteString.copyFrom(nftTokenCapsule.getKey()))
              .clearApprovalAll()
              .setTotal(1L)
              .clearPrev()
              .clearNext()
              .clearTail()
              .build();

      var relationCap = new NftAccountTokenRelationCapsule(ArrayUtils.addAll(ownerAddr, nftTokenCapsule.getKey()), relation);
      put(relationCap.getKey(), relationCap);
    }
    else{
      var headRelation = get(ownerAddr);
      if(!headRelation.hasTail()){
        //only head node exist
        var newRelation = Protocol.NftAccountTokenRelation.newBuilder()
                .setTokenId(ByteString.copyFrom(nftTokenCapsule.getKey()))
                .clearApprovalAll()
                .setTotal(0L)
                .clearNext()
                .setPrev(ByteString.copyFrom(ownerAddr))
                .clearTail()
                .build();
        var newRelationCap = new NftAccountTokenRelationCapsule(ArrayUtils.addAll(ownerAddr, nftTokenCapsule.getKey()), newRelation);
        put(newRelationCap.getKey(), newRelationCap);

        headRelation.setTotal(Math.incrementExact(headRelation.getTotal()));
        headRelation.setNext(ByteString.copyFrom(newRelationCap.getKey()));
        headRelation.setTail(ByteString.copyFrom(newRelationCap.getKey()));
        put(ownerAddr, headRelation);
      }
      else {
        //head node & at-least another nodes
        var tailNode = get(headRelation.getTail().toByteArray());

        var newRelation = Protocol.NftAccountTokenRelation.newBuilder()
                .setTokenId(ByteString.copyFrom(nftTokenCapsule.getKey()))
                .clearApprovalAll()
                .setTotal(0L)
                .clearNext()
                .setPrev(headRelation.getTail())
                .clearTail()
                .build();
        var newRelationCap = new NftAccountTokenRelationCapsule(ArrayUtils.addAll(ownerAddr, nftTokenCapsule.getKey()), newRelation);
        put(newRelationCap.getKey(), newRelationCap);

        //update last tail
        tailNode.setNext(ByteString.copyFrom(newRelationCap.getKey()));
        put(tailNode.getKey(), tailNode);

        //update header node
        headRelation.setTotal(Math.incrementExact(headRelation.getTotal()));
        headRelation.setTail(ByteString.copyFrom(newRelationCap.getKey()));
        put(headRelation.getKey(), headRelation);
      }
    }
  }
}
