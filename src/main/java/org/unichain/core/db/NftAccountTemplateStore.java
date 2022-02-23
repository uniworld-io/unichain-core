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

  public void save(final byte[] accountAddress, final ByteString templateId, final long total) {
    NftAccountTemplateRelationCapsule capsule;
    var key = generateKey(accountAddress, templateId);
    if (has(key)) {
      var relationCapsules = getAll();
      ByteString prev = null;
      var relationCapsuleResult = relationCapsules.stream()
              .filter(relationCapsule -> {
                var keyExist = generateKey(relationCapsule.getAccountAddress(), relationCapsule.getTemplateId());
                return Objects.isNull(relationCapsule.getNext()) && Arrays.equals(key, keyExist);
              })
              .collect(Collectors.toList());

      if (relationCapsuleResult.size() == 1) { // contains only the first element
        relationCapsuleResult.get(0).setNext(templateId);
        prev = relationCapsules.get(0).getTemplateId();
      } else { // contains first and last element
        for (NftAccountTemplateRelationCapsule templateRelationCapsule: relationCapsuleResult) {
          if (Objects.nonNull(templateRelationCapsule.getPrev())) { // get last element
            templateRelationCapsule.setNext(templateId);
            prev = templateRelationCapsule.getPrev();
          }
        }
      }
      capsule = new NftAccountTemplateRelationCapsule(accountAddress, prev, templateId);
    } else {
      capsule = new NftAccountTemplateRelationCapsule(accountAddress, templateId, total);
    }
    put(key, capsule);
  }

  /**
   * generate key from account address and templateId
   */
  private byte[] generateKey(byte[] accountAddress, ByteString templateId) {
    return ArrayUtils.addAll(accountAddress, templateId.toByteArray());
  }
}
