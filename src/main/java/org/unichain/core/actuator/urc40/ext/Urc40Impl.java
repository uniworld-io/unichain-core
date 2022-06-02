package org.unichain.core.actuator.urc40.ext;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.unichain.api.GrpcAPI;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.urc40.Urc40SpenderCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.ArrayList;
import java.util.Objects;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j
@Service
public class Urc40Impl implements Urc40 {
  @Autowired
  private Wallet wallet;

  @Autowired
  private Manager dbManager;

  @Override
  public GrpcAPI.NumberMessage allowance(Protocol.Urc40AllowanceQuery query) {
    var owner = query.getOwner().toByteArray();
    var contract = query.getAddress().toByteArray();
    var spender = query.getSpender().toByteArray();
    Assert.isTrue(Wallet.addressValid(owner) && Wallet.addressValid(contract) && Wallet.addressValid(spender), "Bad owner|contract|spender address");

    var spenderKey = Urc40SpenderCapsule.genKey(spender, contract);
    var spenderStore = dbManager.getUrc40SpenderStore();
    var avail = 0L;
    if(spenderStore.has(spenderKey)){
      var quota = spenderStore.get(spenderKey);
      avail = Objects.isNull(quota) ? 0L : quota.getQuota(owner);
    }

    return GrpcAPI.NumberMessage.newBuilder().setNum(avail).build();
  }

  @Override
  public Protocol.Transaction approve(Contract.Urc40ApproveContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40ApproveContract).getInstance();
  }

  @Override
  public GrpcAPI.NumberMessage balanceOf(Protocol.Urc40BalanceOfQuery query) {
    var owner = query.getOwnerAddress().toByteArray();
    var contract = query.getAddress().toByteArray();
    var contractBase58 = Wallet.encode58Check(contract);
    var accStore = dbManager.getAccountStore();
    var contractStore = dbManager.getContractStore();
    Assert.isTrue(accStore.has(owner), "Not found address: " + Wallet.encode58Check(owner));
    Assert.isTrue(contractStore.has(contract), "Not found contract: " + contractBase58);
    var ownerCap = accStore.get(owner);
    var futureSummary = ownerCap.getUrc40FutureTokenSummary(contractBase58);
    var avail = ownerCap.getUrc40TokenAvailable(contractBase58.toLowerCase());
    var future = (futureSummary == null ? 0L : futureSummary.getTotalValue());
    return GrpcAPI.NumberMessage.newBuilder().setNum(avail + future).build();
  }

  @Override
  public Protocol.Transaction burn(Contract.Urc40BurnContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40BurnContract).getInstance();
  }

  @Override
  public Contract.Urc40ContractPage contractList(Protocol.Urc40ContractQuery query) {
    return dbManager.getUrc40ContractStore().query(query);
  }

  @Override
  public Protocol.Transaction contributePoolFee(Contract.Urc40ContributePoolFeeContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40ContributePoolFeeContract).getInstance();
  }

  @Override
  public Protocol.Transaction createContract(Contract.Urc40CreateContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40CreateContract).getInstance();
  }

  @Override
  public GrpcAPI.NumberMessage decimals(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc40ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc40 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.NumberMessage.newBuilder()
            .setNum(contractStore.get(addr).getDecimals())
            .build();
  }

  @Override
  public Protocol.Transaction exchange(Contract.Urc40ExchangeContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40ExchangeContract).getInstance();
  }

  @Override
  public Protocol.Urc40FutureTokenPack futureGet(Protocol.Urc40FutureTokenQuery query) {
    Assert.isTrue(query.hasField(URC40_QR_FIELD_OWNER_ADDR), "Missing owner address");
    Assert.isTrue(query.hasField(URC40_QR_FIELD_ADDR), "Missing contract address");
    var ownerAddr = query.getOwnerAddress().toByteArray();
    var addr = query.getAddress().toByteArray();
    Assert.isTrue(Wallet.addressValid(ownerAddr), "Invalid owner address");
    Assert.isTrue(Wallet.addressValid(addr), "Invalid contract address");
    var acc = dbManager.getAccountStore().get(ownerAddr);
    Assert.notNull(acc, "Owner address not found: " + Wallet.encode58Check(ownerAddr));

    var addrBase58 = Wallet.encode58Check(addr);
    var contract = dbManager.getUrc40ContractStore().get(addr);
    Assert.notNull(contract, "Contract not found: " + addrBase58);

    if(!query.hasField(URC40_QR_FIELD_PAGE_SIZE))
    {
      query = query.toBuilder()
              .setPageSize(DEFAULT_PAGE_SIZE)
              .build();
    }

    if(!query.hasField(URC40_QR_FIELD_PAGE_INDEX))
    {
      query = query.toBuilder()
              .setPageIndex(DEFAULT_PAGE_INDEX)
              .build();
    }

    Assert.isTrue(query.getPageSize() > 0 &&  query.getPageIndex() >=0 && query.getPageSize() <= MAX_PAGE_SIZE, "invalid paging info");


    var summary = acc.getUrc40FutureTokenSummary(addrBase58.toLowerCase());

    //no deals
    if(Objects.isNull(summary) || (summary.getTotalDeal() <= 0)){
      return Protocol.Urc40FutureTokenPack.newBuilder()
              .setOwnerAddress(query.getOwnerAddress())
              .setAddress(query.getAddress())
              .setSymbol(contract.getSymbol())
              .setTotalDeal(0)
              .setTotalValue(0)
              .clearLowerBoundTime()
              .clearUpperBoundTime()
              .clearDeals()
              .build();
    }

    //validate query
    var deals = new ArrayList<Protocol.Urc40FutureToken>();

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
      var futureStore = dbManager.getUrc40FutureTransferStore();
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

    return Protocol.Urc40FutureTokenPack.newBuilder()
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
    var contractStore = dbManager.getUrc40ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc40 contract: " + Wallet.encode58Check(addr));
    return Protocol.AddressMessage.newBuilder()
            .setAddress(contractStore.get(addr).getOwnerAddress())
            .build();
  }

  @Override
  public Protocol.Transaction mint(Contract.Urc40MintContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40MintContract).getInstance();
  }

  @Override
  public GrpcAPI.StringMessage name(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc40ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc40 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.StringMessage.newBuilder()
            .setValue(contractStore.get(addr).getName())
            .build();
  }

  @Override
  public GrpcAPI.StringMessage symbol(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc40ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc40 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.StringMessage.newBuilder()
            .setValue(contractStore.get(addr).getSymbol())
            .build();
  }

  @Override
  public GrpcAPI.NumberMessage totalSupply(Protocol.AddressMessage query) {
    var addr = query.getAddress().toByteArray();
    var contractStore = dbManager.getUrc40ContractStore();
    Assert.isTrue(contractStore.has(addr), "Not found urc40 contract: " + Wallet.encode58Check(addr));
    return GrpcAPI.NumberMessage.newBuilder()
            .setNum(contractStore.get(addr).getTotalSupply())
            .build();
  }

  @Override
  public Protocol.Transaction transferFrom(Contract.Urc40TransferFromContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40TransferFromContract).getInstance();
  }

  @Override
  public Protocol.Transaction transferOwner(Contract.Urc40TransferOwnerContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40TransferOwnerContract).getInstance();
  }

  @Override
  public Protocol.Transaction transfer(Contract.Urc40TransferContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40TransferContract).getInstance();
  }

  @Override
  public Protocol.Transaction updateParams(Contract.Urc40UpdateParamsContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40UpdateParamsContract).getInstance();
  }

  @Override
  public Protocol.Transaction withdrawFuture(Contract.Urc40WithdrawFutureContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40WithdrawFutureContract).getInstance();
  }
}
