package org.unichain.core.actuator.urc20.ext;

import org.unichain.api.GrpcAPI;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

public interface Urc20 {
  Protocol.Transaction createContract(Contract.Urc20CreateContract contract) throws ContractValidateException;
  Protocol.Transaction contributePoolFee(Contract.Urc20ContributePoolFeeContract contract) throws ContractValidateException;
  Protocol.Transaction updateParams(Contract.Urc20UpdateParamsContract contract) throws ContractValidateException;
  Protocol.Transaction mint(Contract.Urc20MintContract contract) throws ContractValidateException;
  Protocol.Transaction burn(Contract.Urc20BurnContract contract) throws ContractValidateException;
  Protocol.Transaction withdrawFuture(Contract.Urc20WithdrawFutureContract contract) throws ContractValidateException;
  Protocol.Transaction transferOwner(Contract.Urc20TransferOwnerContract contract) throws ContractValidateException;
  Protocol.Transaction exchange(Contract.Urc20ExchangeContract contract) throws ContractValidateException;
  Protocol.Transaction approve(Contract.Urc20ApproveContract contract) throws ContractValidateException;
  Protocol.Transaction transferFrom(Contract.Urc20TransferFromContract contract) throws ContractValidateException;
  Protocol.Transaction transfer(Contract.Urc20TransferContract contract) throws ContractValidateException;

  GrpcAPI.StringMessage allowance(Protocol.Urc20AllowanceQuery query);
  GrpcAPI.StringMessage balanceOf(Protocol.Urc20BalanceOfQuery query);
  GrpcAPI.NumberMessage decimals(Protocol.AddressMessage query);
  Protocol.AddressMessage getOwner(Protocol.AddressMessage query);
  GrpcAPI.StringMessage name(Protocol.AddressMessage query);
  GrpcAPI.StringMessage symbol(Protocol.AddressMessage query);
  GrpcAPI.StringMessage totalSupply(Protocol.AddressMessage query);
  Contract.Urc20ContractPage contractList(Protocol.Urc20ContractQuery query);
  Protocol.Urc20FutureTokenPack futureGet(Protocol.Urc20FutureTokenQuery query);
}
