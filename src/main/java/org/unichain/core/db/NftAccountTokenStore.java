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

  /**
   * Node head determined by ownerAddress
   */
  public void save(byte[] ownerAddr, NftTokenCapsule nftTokenCapsule){
    var emptyNodes = !has(ownerAddr);
    if(emptyNodes){
      var data = Protocol.NftAccountTokenRelation.newBuilder()
              .setTokenId(ByteString.copyFrom(nftTokenCapsule.getKey()))
              .setTotal(1)
              .clearPrev()
              .clearTail()
              .clearNext()
              .build();

      var firstNode = new NftAccountTokenRelationCapsule(ownerAddr, data);
      put(firstNode.getKey(), firstNode);
      return;
    }

    var headNode = get(ownerAddr);
    var existsOnlyNode = !headNode.hasTail();

    if(existsOnlyNode){
      var data = Protocol.NftAccountTokenRelation.newBuilder()
              .setTotal(0)
              .setTokenId(ByteString.copyFrom(nftTokenCapsule.getKey()))
              .setPrev(ByteString.copyFrom(ownerAddr))
              .clearTail()
              .clearNext()
              .build();

      var nextNode = new NftAccountTokenRelationCapsule(generateKey(ownerAddr, nftTokenCapsule.getKey()), data);
      put(nextNode.getKey(), nextNode);

      headNode.setTotal(Math.incrementExact(headNode.getTotal()));
      headNode.setTail(ByteString.copyFrom(nextNode.getKey()));
      headNode.setNext(ByteString.copyFrom(nextNode.getKey()));
      put(ownerAddr, headNode);
    }else {
      var tailNode = get(headNode.getTail().toByteArray());

      var data = Protocol.NftAccountTokenRelation.newBuilder()
              .setTotal(0)
              .setTokenId(ByteString.copyFrom(nftTokenCapsule.getKey()))
              .setPrev(ByteString.copyFrom(tailNode.getKey()))
              .clearTail()
              .clearNext()
              .build();

      var nextNode = new NftAccountTokenRelationCapsule(generateKey(ownerAddr, nftTokenCapsule.getKey()), data);
      put(nextNode.getKey(), nextNode);

      tailNode.setNext(ByteString.copyFrom(nextNode.getKey()));
      put(tailNode.getKey(), tailNode);

      headNode.setTotal(Math.incrementExact(headNode.getTotal()));
      headNode.setTail(ByteString.copyFrom(nextNode.getKey()));
      put(headNode.getKey(), headNode);
    }
  }

  private byte[] generateKey(byte[] ownerAddress, byte[] tokenId){
    return ArrayUtils.addAll(ownerAddress, tokenId);
  }
}
