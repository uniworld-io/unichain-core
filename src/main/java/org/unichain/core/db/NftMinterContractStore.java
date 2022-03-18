package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unichain.core.capsule.NftAccountTemplateRelationCapsule;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Component
@Slf4j(topic = "DB")
public class NftMinterContractStore extends UnichainStoreWithRevoking<NftAccountTemplateRelationCapsule> {

    @Autowired
    protected NftMinterContractStore(@Value("nft-minter-contract-relation") String dbName) {
        super(dbName);
    }

    @Override
    public NftAccountTemplateRelationCapsule get(byte[] key) {
        return super.getUnchecked(key);
    }

    public List<NftAccountTemplateRelationCapsule> getAll() {
        return Streams.stream(iterator())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }


}
