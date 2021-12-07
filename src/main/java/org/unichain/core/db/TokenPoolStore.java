package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "DB")
@Component
public class TokenPoolStore extends UnichainStoreWithRevoking<TokenPoolCapsule> {

  @Autowired
  protected TokenPoolStore(@Value("token-pool") String dbName) {
    super(dbName);
  }

  @Override
  public TokenPoolCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public Contract.TokenPage query(Protocol.TokenPoolQuery query){
    int pageSize = query.hasField(TOKEN_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
    int pageIndex = query.hasField(TOKEN_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
    Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

    List<Contract.CreateTokenContract> sorted;
    if(query.hasField(TOKEN_QUERY_FIELD_TOKEN_NAME))
    {
      sorted  = getAll().stream()
              .filter(Objects::nonNull)
              .map(item -> item.getInstance())
              .filter(item -> StringUtils.containsIgnoreCase(item.getName(), query.getTokenName()))
              .sorted(Comparator.comparing(Contract.CreateTokenContract::getName))
              .collect(Collectors.toList());
    }
    else{
      sorted = getAll().stream()
              .filter(Objects::nonNull)
              .map(item -> item.getInstance())
              .sorted(Comparator.comparing(Contract.CreateTokenContract::getName))
              .collect(Collectors.toList());
    }

    return Contract.TokenPage.newBuilder()
            .setPageSize(pageSize)
            .setPageIndex(pageIndex)
            .setTotal(sorted.size())
            .addAllTokens(Utils.paging(sorted, pageIndex, pageSize))
            .build();
  }
}
