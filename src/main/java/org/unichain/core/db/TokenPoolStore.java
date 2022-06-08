package org.unichain.core.db;

import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.capsule.urc30.Urc30TokenPoolCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "DB")
@Component
public class TokenPoolStore extends UnichainStoreWithRevoking<Urc30TokenPoolCapsule> {

  private static Descriptors.FieldDescriptor TOKEN_QUERY_FIELD_PAGE_INDEX= Protocol.TokenPoolQuery.getDescriptor().findFieldByNumber(Protocol.TokenPoolQuery.PAGE_INDEX_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor TOKEN_QUERY_FIELD_PAGE_SIZE= Protocol.TokenPoolQuery.getDescriptor().findFieldByNumber(Protocol.TokenPoolQuery.PAGE_SIZE_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor TOKEN_QUERY_FIELD_TOKEN_NAME= Protocol.TokenPoolQuery.getDescriptor().findFieldByNumber(Protocol.TokenPoolQuery.TOKEN_NAME_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor TOKEN_QUERY_FIELD_TOKEN_ADDR= Protocol.TokenPoolQuery.getDescriptor().findFieldByNumber(Protocol.TokenPoolQuery.TOKEN_ADDR_FIELD_NUMBER);

  @Autowired
  protected TokenPoolStore(@Value("token-pool") String dbName) {
    super(dbName);
  }

  @Override
  public Urc30TokenPoolCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public Contract.TokenPage query(Protocol.TokenPoolQuery query){
    int pageSize = query.hasField(TOKEN_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
    int pageIndex = query.hasField(TOKEN_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
    Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

    Predicate<Contract.CreateTokenContract> filter =  ctx -> {
      return (!query.hasField(TOKEN_QUERY_FIELD_TOKEN_NAME) || StringUtils.containsIgnoreCase(ctx.getName(), query.getTokenName()))
              && (!query.hasField(TOKEN_QUERY_FIELD_TOKEN_ADDR) || StringUtils.containsIgnoreCase(Hex.encodeHexString(ctx.getAddress().toByteArray()), query.getTokenAddr()));
    };

    var sorted = getAll().stream()
            .filter(Objects::nonNull)
            .map(Urc30TokenPoolCapsule::getInstance)
            .filter(filter)
            .map(item -> item.hasField(TOKEN_CREATE_FIELD_CREATE_ACC_FEE) ? item : item.toBuilder().setCreateAccFee(Parameter.ChainConstant.TOKEN_DEFAULT_CREATE_ACC_FEE).build())
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
