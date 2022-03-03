package org.unichain.core.db;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftTokenApproveRelationCapsule;
import org.unichain.core.capsule.NftTokenCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class NftTokenApproveRelationStore extends UnichainStoreWithRevoking<NftTokenApproveRelationCapsule> {

  @Autowired
  protected NftTokenApproveRelationStore(@Value("nft-token-approve") String dbName) {
    super(dbName);
  }

  @Override
  public NftTokenApproveRelationCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<NftTokenApproveRelationCapsule> getAllTokens() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  //@fixme
  public void disapprove(byte[] tokenId, byte[] toAddress){

  }
}
