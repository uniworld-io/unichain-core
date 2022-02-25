package org.unichain.core.db;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftAccountTemplateRelationCapsule;
import org.unichain.core.capsule.NftTemplateCapsule;
import org.unichain.protos.Protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class NftAccountTemplateStore extends UnichainStoreWithRevoking<NftAccountTemplateRelationCapsule> {

  @Autowired
  protected NftAccountTemplateStore(@Value("nft-acc-template-relation") String dbName) {
    super(dbName);
  }

  @Override
  public NftAccountTemplateRelationCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<NftAccountTemplateRelationCapsule> getAll() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  public void save(byte[] ownerAddr, NftTemplateCapsule templateCapsule, boolean isMinter) {
    if(!has(ownerAddr)){
      //no template yet, create fist one
      var relation = Protocol.NftAccountTemplateRelation.newBuilder()
              .setTemplateId(ByteString.copyFrom(templateCapsule.getKey()))
              .setTotal(1L)
              .setIsMinter(isMinter)
              .clearPrev()
              .clearNext()
              .clearTail()
              .build();

      var relationCap = new NftAccountTemplateRelationCapsule(ownerAddr, relation);
      put(relationCap.getKey(), relationCap);
    }
    else{
      var headRelation = get(ownerAddr);
      if(!headRelation.hasTail()){
        //only head node exist
        var newRelation = Protocol.NftAccountTemplateRelation.newBuilder()
                .setTemplateId(ByteString.copyFrom(templateCapsule.getKey()))
                .setTotal(0L)
                .clearNext()
                .setPrev(ByteString.copyFrom(ownerAddr))
                .setIsMinter(isMinter)
                .clearTail()
                .build();
        var newRelationCap = new NftAccountTemplateRelationCapsule(generateKey(ownerAddr, templateCapsule.getKey()), newRelation);
        put(newRelationCap.getKey(), newRelationCap);

        headRelation.setTotal(Math.incrementExact(headRelation.getTotal()));
        headRelation.setNext(ByteString.copyFrom(newRelationCap.getKey()));
        headRelation.setTail(ByteString.copyFrom(newRelationCap.getKey()));
        put(ownerAddr, headRelation);
      }
      else {
        //head node & at-least another nodes
        var tailNode = get(headRelation.getTail().toByteArray());

        var newRelation = Protocol.NftAccountTemplateRelation.newBuilder()
                .setTemplateId(ByteString.copyFrom(templateCapsule.getKey()))
                .setTotal(0L)
                .clearNext()
                .setPrev(headRelation.getTail())
                .setIsMinter(isMinter)
                .clearTail()
                .build();
        var newRelationCap = new NftAccountTemplateRelationCapsule(generateKey(ownerAddr, templateCapsule.getKey()), newRelation);
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

  /**
   * generate key from account address and templateId
   */
  private byte[] generateKey(byte[] accountAddress, byte[] templateId) {
    return ArrayUtils.addAll(accountAddress, templateId);
  }
}
