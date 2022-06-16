package org.unichain.core.db;

import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.capsule.urc20.Urc20ContractCapsule;
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
public class Urc20ContractStore extends UnichainStoreWithRevoking<Urc20ContractCapsule> {

  private static Descriptors.FieldDescriptor URC20_CONTRACT_QUERY_FIELD_PAGE_INDEX= Protocol.Urc20ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc20ContractQuery.PAGE_INDEX_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC20_CONTRACT_QUERY_FIELD_PAGE_SIZE= Protocol.Urc20ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc20ContractQuery.PAGE_SIZE_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC20_CONTRACT_QUERY_FIELD_TOKEN_ADDR= Protocol.Urc20ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc20ContractQuery.ADDRESS_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC20_CONTRACT_QUERY_FIELD_TOKEN_SYMBOL= Protocol.Urc20ContractQuery.getDescriptor().findFieldByNumber(Protocol.Urc20ContractQuery.SYMBOL_FIELD_NUMBER);

  @Autowired
  protected Urc20ContractStore(@Value("urc20-contract") String dbName) {
    super(dbName);
  }

  @Override
  public Urc20ContractCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public Contract.Urc20ContractPage query(Protocol.Urc20ContractQuery query){
    int pageSize = query.hasField(URC20_CONTRACT_QUERY_FIELD_PAGE_SIZE) ? query.getPageSize() : DEFAULT_PAGE_SIZE;
    int pageIndex = query.hasField(URC20_CONTRACT_QUERY_FIELD_PAGE_INDEX) ? query.getPageIndex() : DEFAULT_PAGE_INDEX;
    Assert.isTrue(pageSize > 0 && pageIndex >= 0 && pageSize <= MAX_PAGE_SIZE, "Invalid paging info");

    Predicate<Contract.Urc20CreateContract> filter =  ctx -> {
      return (!query.hasField(URC20_CONTRACT_QUERY_FIELD_TOKEN_SYMBOL) || StringUtils.containsIgnoreCase(ctx.getSymbol(), query.getSymbol()))
              && (!query.hasField(URC20_CONTRACT_QUERY_FIELD_TOKEN_ADDR) || Arrays.equals(ctx.getAddress().toByteArray(), query.getAddress().toByteArray()));
    };

    var sorted = getAll().stream()
            .filter(Objects::nonNull)
            .map(Urc20ContractCapsule::getInstance)
            .filter(filter)
            .map(item -> item.hasField(Urc20ContractCapsule.URC20_CREATE_FIELD_CREATE_ACC_FEE) ? item : item.toBuilder().setCreateAccFee(Parameter.ChainConstant.TOKEN_DEFAULT_CREATE_ACC_FEE).build())
            .sorted(Comparator.comparing(Contract.Urc20CreateContract::getName))
            .collect(Collectors.toList());

    return Contract.Urc20ContractPage.newBuilder()
            .setPageSize(pageSize)
            .setPageIndex(pageIndex)
            .setTotal(sorted.size())
            .addAllContracts(Utils.paging(sorted, pageIndex, pageSize))
            .build();
  }
}
