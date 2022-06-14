package org.unichain.core.actuator.urc20.ext;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.unichain.api.GrpcAPI;
import org.unichain.common.utils.AddressUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.urc20.Urc20SpenderCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j
@Service
public class Urc20Impl implements Urc20 {

  private static Descriptors.FieldDescriptor URC20_QR_FIELD_ADDR = Protocol.Urc20FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.Urc20FutureTokenQuery.ADDRESS_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC20_QR_FIELD_OWNER_ADDR = Protocol.Urc20FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.Urc20FutureTokenQuery.OWNER_ADDRESS_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC20_QR_FIELD_PAGE_SIZE = Protocol.Urc20FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.Urc20FutureTokenQuery.PAGE_SIZE_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC20_QR_FIELD_PAGE_INDEX = Protocol.Urc20FutureTokenQuery.getDescriptor().findFieldByNumber(Protocol.Urc20FutureTokenQuery.PAGE_INDEX_FIELD_NUMBER);

  @Autowired
  private Wallet wallet;

  @Autowired
  private Manager dbManager;

  @Override
  public GrpcAPI.StringMessage allowance(Protocol.Urc20AllowanceQuery query) {
    var owner = query.getOwner().toByteArray();
    var contract = query.getAddress().toByteArray();
    var spender = query.getSpender().toByteArray();
    Assert.isTrue(Wallet.addressValid(owner)
            && Wallet.addressValid(contract)
            && Wallet.addressValid(spender), "Bad owner|contract|spender address");

    var spenderKey = Urc20SpenderCapsule.genKey(spender, contract);
    var spenderStore = dbManager.getUrc20SpenderStore();
    var avail = BigInteger.ZERO;
    if(spenderStore.has(spenderKey)){
      var quota = spenderStore.get(spenderKey);
      avail = Objects.isNull(quota) ? BigInteger.ZERO : quota.getQuota(owner);
    }

    return GrpcAPI.StringMessage.newBuilder().setValue(avail.toString()).build();
  }

  @Override
  public Protocol.Transaction approve(Contract.Urc20ApproveContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20ApproveContract).getInstance();
  }

  @Override
  public GrpcAPI.StringMessage balanceOf(Protocol.Urc20BalanceOfQuery query) {
    var owner = query.getOwnerAddress().toByteArray();
    var contract = query.getAddress().toByteArray();
    var contractBase58 = Wallet.encode58Check(contract);
    var accStore = dbManager.getAccountStore();
    var contractStore = dbManager.getUrc20ContractStore();
    Assert.isTrue(accStore.has(owner), "Not found address: " + Wallet.encode58Check(owner));
    Assert.isTrue(contractStore.has(contract), "Not found contract: " + contractBase58);
    var ownerCap = accStore.get(owner);
    var futureSummary = ownerCap.getUrc20FutureTokenSummary(contractBase58);
    var avail = ownerCap.getUrc20TokenAvailable(contractBase58);
    var future = (futureSummary == null ? BigInteger.ZERO : new BigInteger(futureSummary.getTotalValue()));
    return GrpcAPI.StringMessage.newBuilder().setValue(avail.add(future).toString()).build();
  }

  @Override
  public Protocol.Transaction burn(Contract.Urc20BurnContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20BurnContract).getInstance();
  }

  @Override
  public Contract.Urc20ContractPage contractList(Protocol.Urc20ContractQuery query) {
    return dbManager.getUrc20ContractStore().query(query);
  }

  @Override
  public Protocol.Transaction contributePoolFee(Contract.Urc20ContributePoolFeeContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20ContributePoolFeeContract).getInstance();
  }

  @Override
  public Protocol.Transaction createContract(Contract.Urc20CreateContract contract) throws ContractValidateException {
    /**
     * Critical: generate address
     */
    contract = contract.toBuilder()
            .setAddress(ByteString.copyFrom(AddressUtil.generateRandomAddress()))
            .build();
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20CreateContract).getInstance();
  }

  @Override
  public GrpcAPI.NumberMessage decimals(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc20ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc20 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.NumberMessage.newBuilder()
            .setNum(contractStore.get(addr).getDecimals())
            .build();
  }

  @Override
  public Protocol.Transaction exchange(Contract.Urc20ExchangeContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20ExchangeContract).getInstance();
  }

  @Override
  public Protocol.Urc20FutureTokenPack futureGet(Protocol.Urc20FutureTokenQuery query) {
    Assert.isTrue(query.hasField(URC20_QR_FIELD_OWNER_ADDR), "Missing owner address");
    Assert.isTrue(query.hasField(URC20_QR_FIELD_ADDR), "Missing contract address");
    var ownerAddr = query.getOwnerAddress().toByteArray();
    var addr = query.getAddress().toByteArray();
    Assert.isTrue(Wallet.addressValid(ownerAddr), "Invalid owner address");
    Assert.isTrue(Wallet.addressValid(addr), "Invalid contract address");
    var acc = dbManager.getAccountStore().get(ownerAddr);
    Assert.notNull(acc, "Owner address not found: " + Wallet.encode58Check(ownerAddr));

    var addrBase58 = Wallet.encode58Check(addr);
    var contract = dbManager.getUrc20ContractStore().get(addr);
    Assert.notNull(contract, "Contract not found: " + addrBase58);

    if(!query.hasField(URC20_QR_FIELD_PAGE_SIZE))
    {
      query = query.toBuilder()
              .setPageSize(DEFAULT_PAGE_SIZE)
              .build();
    }

    if(!query.hasField(URC20_QR_FIELD_PAGE_INDEX))
    {
      query = query.toBuilder()
              .setPageIndex(DEFAULT_PAGE_INDEX)
              .build();
    }

    Assert.isTrue(query.getPageSize() > 0 &&  query.getPageIndex() >=0 && query.getPageSize() <= MAX_PAGE_SIZE, "invalid paging info");


    var summary = acc.getUrc20FutureTokenSummary(addrBase58);

    //no deals
    if(Objects.isNull(summary) || (summary.getTotalDeal() <= 0)){
      return Protocol.Urc20FutureTokenPack.newBuilder()
              .setOwnerAddress(query.getOwnerAddress())
              .setAddress(query.getAddress())
              .setSymbol(contract.getSymbol())
              .setTotalDeal(0)
              .setTotalValue(BigInteger.ZERO.toString())
              .clearLowerBoundTime()
              .clearUpperBoundTime()
              .clearDeals()
              .build();
    }

    //validate query
    var deals = new ArrayList<Protocol.Urc20FutureToken>();

    int pageSize = query.getPageSize();
    int pageIndex = query.getPageIndex();
    long start = (long) pageIndex * pageSize;
    long end = start + pageSize;
    if(start >= summary.getTotalDeal()){
      //empty deals
    }
    else {
      if(end >= summary.getTotalDeal())
        end = summary.getTotalDeal();

      //load sublist from [start -> end)
      var futureStore = dbManager.getUrc20FutureTransferStore();
      var tmpTickKeyBs = summary.getLowerTick();
      int index = 0;
      while (true){
        var tmpTick = futureStore.get(tmpTickKeyBs.toByteArray());
        if(index >= start && index < end)
        {
          deals.add(tmpTick.getInstance());
        }
        if(index >= end)
          break;
        tmpTickKeyBs = tmpTick.getNextTick();
        index ++;
      }
    }

    return Protocol.Urc20FutureTokenPack.newBuilder()
            .setOwnerAddress(query.getOwnerAddress())
            .setAddress(query.getAddress())
            .setSymbol(contract.getSymbol())
            .setTotalDeal(summary.getTotalDeal())
            .setTotalValue(summary.getTotalValue())
            .setLowerBoundTime(summary.getLowerBoundTime())
            .setUpperBoundTime(summary.getUpperBoundTime())
            .addAllDeals(deals)
            .build();
  }

  @Override
  public Protocol.AddressMessage getOwner(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc20ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc20 contract: " + Wallet.encode58Check(addr));
    return Protocol.AddressMessage.newBuilder()
            .setAddress(contractStore.get(addr).getOwnerAddress())
            .build();
  }

  @Override
  public Protocol.Transaction mint(Contract.Urc20MintContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20MintContract).getInstance();
  }

  @Override
  public GrpcAPI.StringMessage name(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc20ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc20 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.StringMessage.newBuilder()
            .setValue(contractStore.get(addr).getName())
            .build();
  }

  @Override
  public GrpcAPI.StringMessage symbol(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc20ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc20 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.StringMessage.newBuilder()
            .setValue(contractStore.get(addr).getSymbol())
            .build();
  }

  @Override
  public GrpcAPI.StringMessage totalSupply(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc20ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc20 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.StringMessage.newBuilder()
            .setValue(contractStore.get(addr).getTotalSupply().toString())
            .build();
  }

  @Override
  public Protocol.Transaction transferFrom(Contract.Urc20TransferFromContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20TransferFromContract).getInstance();
  }

  @Override
  public Protocol.Transaction transferOwner(Contract.Urc20TransferOwnerContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20TransferOwnerContract).getInstance();
  }

  @Override
  public Protocol.Transaction transfer(Contract.Urc20TransferContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20TransferContract).getInstance();
  }

  @Override
  public Protocol.Transaction updateParams(Contract.Urc20UpdateParamsContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20UpdateParamsContract).getInstance();
  }

  @Override
  public Protocol.Transaction withdrawFuture(Contract.Urc20WithdrawFutureContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc20WithdrawFutureContract).getInstance();
  }
}
