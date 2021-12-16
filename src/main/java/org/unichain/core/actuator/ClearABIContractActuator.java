package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.common.runtime.config.VMConfig;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.ContractCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.AccountStore;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.ClearABIContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ClearABIContractActuator extends AbstractActuator {

  ClearABIContractActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var usContract = contract.unpack(ClearABIContract.class);
      var contractAddress = usContract.getContractAddress().toByteArray();
      var ownerAddress = usContract.getOwnerAddress().toByteArray();
      var deployedContract = dbManager.getContractStore().get(contractAddress);
      deployedContract.clearABI();
      dbManager.getContractStore().put(contractAddress, deployedContract);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.error(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (!VMConfig.allowTvmConstantinople()) {
      throw new ContractValidateException("contract type error,unexpected type [ClearABIContract]");
    }

    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(ClearABIContract.class)) {
      throw new ContractValidateException("contract type error,expected type [ClearABIContract],real type[" + contract.getClass() + "]");
    }
    final ClearABIContract contract;
    try {
      contract = this.contract.unpack(ClearABIContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    if (!Wallet.addressValid(contract.getOwnerAddress().toByteArray())) {
      throw new ContractValidateException("Invalid address");
    }
    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    AccountStore accountStore = dbManager.getAccountStore();
    AccountCapsule accountCapsule = accountStore.get(ownerAddress);
    if (accountCapsule == null) {
      throw new ContractValidateException("Account[" + readableOwnerAddress + "] not exists");
    }

    byte[] contractAddress = contract.getContractAddress().toByteArray();
    ContractCapsule deployedContract = dbManager.getContractStore().get(contractAddress);

    if (deployedContract == null) {
      throw new ContractValidateException("Contract not exists");
    }

    byte[] deployedContractOwnerAddress = deployedContract.getInstance().getOriginAddress().toByteArray();

    if (!Arrays.equals(ownerAddress, deployedContractOwnerAddress)) {
      throw new ContractValidateException("Account[" + readableOwnerAddress + "] is not the owner of the contract");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ClearABIContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
