package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.capsule.urc40.Urc40ContractCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "DB")
@Component
public class Urc40ContractStore extends UnichainStoreWithRevoking<Urc40ContractCapsule> {

  @Autowired
  protected Urc40ContractStore(@Value("urc40-contract") String dbName) {
    super(dbName);
  }

  @Override
  public Urc40ContractCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public Contract.Urc40ContractPage query(Protocol.Urc40ContractQuery query){
    int pageSize = query.hasField(URC40_CONTRACT_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
    int pageIndex = query.hasField(URC40_CONTRACT_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
    Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

    Predicate<Contract.Urc40CreateContract> filter =  ctx -> {
      return (!query.hasField(URC40_CONTRACT_QUERY_FIELD_TOKEN_SYMBOL) || StringUtils.containsIgnoreCase(ctx.getSymbol(), query.getSymbol()))
              && (!query.hasField(URC40_CONTRACT_QUERY_FIELD_TOKEN_ADDR) || Arrays.equals(ctx.getAddress().toByteArray(), query.getAddress().toByteArray()));
    };

    var sorted = getAll().stream()
            .filter(Objects::nonNull)
            .map(Urc40ContractCapsule::getInstance)
            .filter(filter)
            .map(item -> item.hasField(URC40_CREATE_FIELD_CREATE_ACC_FEE) ? item : item.toBuilder().setCreateAccFee(Parameter.ChainConstant.TOKEN_DEFAULT_CREATE_ACC_FEE).build())
            .sorted(Comparator.comparing(Contract.Urc40CreateContract::getName))
            .collect(Collectors.toList());

    return Contract.Urc40ContractPage.newBuilder()
            .setPageSize(pageSize)
            .setPageIndex(pageIndex)
            .setTotal(sorted.size())
            .addAllContracts(Utils.paging(sorted, pageIndex, pageSize))
            .build();
  }
}
