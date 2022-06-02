package org.unichain.core.actuator.urc40.ext;

import org.unichain.api.GrpcAPI;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

//@TODO: Need review
public interface Urc40 {
  GrpcAPI.NumberMessage allowance(Protocol.Urc40AllowanceQuery query);
  Protocol.Transaction approve(Contract.Urc40ApproveContract contract) throws ContractValidateException;
  GrpcAPI.NumberMessage balanceOf(Protocol.Urc40BalanceOfQuery query);
  Protocol.Transaction burn(Contract.Urc40BurnContract contract) throws ContractValidateException;
  Contract.Urc40ContractPage contractList(Protocol.Urc40ContractQuery query);
  Protocol.Transaction contributePoolFee(Contract.Urc40ContributePoolFeeContract contract) throws ContractValidateException;
  Protocol.Transaction createContract(Contract.Urc40CreateContract contract) throws ContractValidateException;
  GrpcAPI.NumberMessage decimals(Protocol.AddressMessage query);
  Protocol.Transaction exchange(Contract.Urc40ExchangeContract contract) throws ContractValidateException;
  Protocol.Urc40FutureTokenPack futureGet(Protocol.Urc40FutureTokenQuery query);
  Protocol.AddressMessage getOwner(Protocol.AddressMessage query);
  Protocol.Transaction mint(Contract.Urc40MintContract contract) throws ContractValidateException;
  GrpcAPI.StringMessage name(Protocol.AddressMessage query);
  GrpcAPI.StringMessage symbol(Protocol.AddressMessage query);
  GrpcAPI.NumberMessage totalSupply(Protocol.AddressMessage query);
  Protocol.Transaction transferFrom(Contract.Urc40TransferFromContract contract) throws ContractValidateException;
  Protocol.Transaction transferOwner(Contract.Urc40TransferOwnerContract contract) throws ContractValidateException;
  Protocol.Transaction transfer(Contract.Urc40TransferContract contract) throws ContractValidateException;
  Protocol.Transaction updateParams(Contract.Urc40UpdateParamsContract contract) throws ContractValidateException;
  Protocol.Transaction withdrawFuture(Contract.Urc40WithdrawFutureContract contract) throws ContractValidateException;
}
