package org.unichain.core.db;

import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.unichain.common.utils.Utils;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.ArrayList;
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
    int pageSize, pageIndex;
    if(!query.hasField(TOKEN_QUERY_FIELD_PAGE_INDEX))
      pageIndex = 0;
    else
      pageIndex = query.getPageIndex();

    if(!query.hasField(TOKEN_QUERY_FIELD_PAGE_SIZE))
      pageSize = 10;
    else
      pageSize = query.getPageSize();

    Assert.isTrue(pageSize > 0, "invalid page size");
    Assert.isTrue(pageIndex >= 0, "invalid page index");

    List<TokenPoolCapsule> found;

    if(query.hasField(TOKEN_QUERY_FIELD_TOKEN_NAME))
    {
      found = new ArrayList<>();
      found.add(get(Util.stringAsBytesUppercase(query.getTokenName())));
    }
    else
      found = getAll();

    //then sorting & filter
    var sorted = found.stream()
            .filter(Objects::nonNull)
            .map(item -> item.getInstance())
            .sorted(Comparator.comparing(Contract.CreateTokenContract::getName))
            .collect(Collectors.toList());
    return Contract.TokenPage.newBuilder()
            .setPageSize(pageSize)
            .setPageIndex(pageIndex)
            .setTotal(sorted.size())
            .addAllTokens(Utils.paging(sorted, pageIndex, pageSize))
            .build();
  }
}
