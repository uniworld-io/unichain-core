package org.unichain.core.actuator.urc40.ext;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unichain.api.GrpcAPI;
import org.unichain.core.Wallet;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

@Slf4j
@Service
public class Urc40Impl implements Urc40 {
  @Autowired
  private Wallet wallet;

  @Override
  public GrpcAPI.NumberMessage allowance(Protocol.Urc40AllowanceQuery query) {
    return wallet.urc40Allowance(query);
  }

  @Override
  public Protocol.Transaction approve(Contract.Urc40ApproveContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40ApproveContract).getInstance();
  }

  @Override
  public GrpcAPI.NumberMessage balanceOf(Protocol.Urc40BalanceOfQuery query) {
    return wallet.urc40BalanceOf(query);
  }

  @Override
  public Protocol.Transaction burn(Contract.Urc40BurnContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40BurnContract).getInstance();
  }

  @Override
  public Contract.Urc40ContractPage contractList(Protocol.Urc40ContractQuery query) {
    return wallet.urc40ContractList(query);
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
    return wallet.urc40Decimals(query);
  }

  @Override
  public Protocol.Transaction exchange(Contract.Urc40ExchangeContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40ExchangeContract).getInstance();
  }

  @Override
  public Protocol.Urc40FutureTokenPack futureGet(Protocol.Urc40FutureTokenQuery query) {
    return wallet.urc40FutureGet(query);
  }

  @Override
  public Protocol.AddressMessage getOwner(Protocol.AddressMessage query) {
    return wallet.urc40GetOwner(query);
  }

  @Override
  public Protocol.Transaction mint(Contract.Urc40MintContract contract) throws ContractValidateException {
    return wallet.createTransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.Urc40MintContract).getInstance();
  }

  @Override
  public GrpcAPI.StringMessage name(Protocol.AddressMessage query) {
    return wallet.urc40Name(query);
  }

  @Override
  public GrpcAPI.StringMessage symbol(Protocol.AddressMessage query) {
    return wallet.urc40Symbol(query);
  }

  @Override
  public GrpcAPI.NumberMessage totalSupply(Protocol.AddressMessage query) {
    return wallet.urc40TotalSupply(query);
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
