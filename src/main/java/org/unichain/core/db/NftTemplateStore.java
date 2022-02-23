package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftTemplateCapsule;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class NftTemplateStore extends UnichainStoreWithRevoking<NftTemplateCapsule> {

  @Autowired
  protected NftTemplateStore(@Value("nft-template") String dbName) {
    super(dbName);
  }

  @Override
  public NftTemplateCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<NftTemplateCapsule> getAll() {
    return Streams.stream(iterator())
        .map(Entry::getValue)
        .collect(Collectors.toList());
  }

  public void deleteMinter(byte[] key) {
    NftTemplateCapsule capsule = get(key);
    capsule.setMinter(null);
    put(key, capsule);
  }
}
