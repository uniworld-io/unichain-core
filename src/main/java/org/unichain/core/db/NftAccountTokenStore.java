package org.unichain.core.db;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.core.capsule.NftAccountTokenRelationCapsule;
import org.unichain.core.capsule.NftTokenCapsule;
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

  public void save(byte[] ownerAddr, byte[] tokenId, boolean isApproval){
    if(!has(ownerAddr)){
      //no token yet, create fist one
      var relation = Protocol.NftAccountTokenRelation.newBuilder()
              .setTokenId(ByteString.copyFrom(tokenId))
              .clearApprovalAll()
              .setTotal(1L)
              .clearPrev()
              .clearNext()
              .clearTail()
              .setIsApproval(isApproval)
              .build();

      var relationCap = new NftAccountTokenRelationCapsule(ArrayUtils.addAll(ownerAddr, tokenId), relation);
      put(relationCap.getKey(), relationCap);
    }
    else{
      var headRelation = get(ownerAddr);
      if(!headRelation.hasTail()){
        //only head node exist
        var newRelation = Protocol.NftAccountTokenRelation.newBuilder()
                .setTokenId(ByteString.copyFrom(tokenId))
                .clearApprovalAll()
                .setTotal(0L)
                .clearNext()
                .setPrev(ByteString.copyFrom(ownerAddr))
                .clearTail()
                .setIsApproval(isApproval)
                .build();
        var newRelationCap = new NftAccountTokenRelationCapsule(ArrayUtils.addAll(ownerAddr, tokenId), newRelation);
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
                .setTokenId(ByteString.copyFrom(tokenId))
                .clearApprovalAll()
                .setTotal(0L)
                .clearNext()
                .setPrev(headRelation.getTail())
                .clearTail()
                .setIsApproval(isApproval)
                .build();
        var newRelationCap = new NftAccountTokenRelationCapsule(ArrayUtils.addAll(ownerAddr, tokenId), newRelation);
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

  public void remove(byte[] addr, byte[] tokenId, boolean isApproval){
    Assert.isTrue(has(addr), "not found any relation");

    var nodeKey =  ArrayUtils.addAll(addr, tokenId);
    if(has(nodeKey)){
      var foundNode = get(nodeKey);
      Assert.isTrue((foundNode.isApproval() && isApproval) || (!foundNode.isApproval() && !isApproval), "unmatched role: isApproval " + isApproval + "real: " + foundNode.isApproval());

      //update prev node
      var prev = foundNode.getPrev();
      var prevNode = get(prev.toByteArray());
      prevNode.setNext(foundNode.getNext());
      put(prev.toByteArray(), prevNode);

      //update next node
      if(foundNode.hasNext()){
        var next = foundNode.getNext().toByteArray();
        var nextNode = get(next);
        nextNode.setPrev(prev);
        put(next, nextNode);
      }

      //remove node
      delete(nodeKey);

      //update head
      var head = get(addr);
      head.setTotal(Math.decrementExact(head.getTotal()));
      if(Arrays.equals(head.getTail().toByteArray(), nodeKey)){
        //tail deleted, must update tail
        head.setTail(prev);
      }
      put(addr, head);
    }
    else {
      //update head node
      var head = get(addr);
      Assert.isTrue((head.isApproval() && isApproval) || (!head.isApproval() && !isApproval), "unmatched role: isApproval " + isApproval + "real: " + head.isApproval());
      Assert.isTrue(Arrays.equals(head.getTokenId().toByteArray(), tokenId), "unmatched token id");
      if(head.getTotal() == 1){
        //just delete
        delete(addr);
      }
      else {
        //update the header [header --> node 1 --> node N]
        var next = head.getNext();
        var nextNode = get(next.toByteArray());
        head.setTotal(Math.decrementExact(head.getTotal()));
        head.setNext(nextNode.getNext());
        head.setTokenId(nextNode.getTokenId());
        head.setApproval(nextNode.isApproval());
        if(head.getTotal() <= 1){
          head.setTail(ByteString.copyFrom(addr));
        }
        //put head
        put(addr, head);
        //delete next
        delete(next.toByteArray());
      }
    }
  }
}
