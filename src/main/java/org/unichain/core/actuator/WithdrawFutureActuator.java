package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.FutureWithdrawContract;
import org.unichain.protos.Protocol.Account.Future;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.unichain.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;

@Slf4j(topic = "actuator")
public class WithdrawFutureActuator extends AbstractActuator {

  WithdrawFutureActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
      long fee = calcFee();
      final FutureWithdrawContract ctx;
      try {
        ctx = contract.unpack(FutureWithdrawContract.class);
      } catch (InvalidProtocolBufferException e) {
        logger.debug(e.getMessage(), e);
        ret.setStatus(fee, code.FAILED);
        throw new ContractExeException(e.getMessage());
      }

      byte[] ownerAddress = ctx.getOwnerAddress().toByteArray();
      AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      List<Future> remain = new ArrayList<>();
      long withdrawAmount = 0L;
      for(Future future : accountCapsule.getFutureSupplyList()){
        if (dbManager.getHeadBlockTimeStamp() >= future.getExpireTime()) {
          logger.info("withdraw future: " + future);
          withdrawAmount += future.getFutureBalance();
        }
        else
          remain.add(future);
      }

      accountCapsule.setFutureSupply(remain);
      accountCapsule.setBalance(accountCapsule.getBalance() + withdrawAmount);
      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      ret.setStatus(fee, code.SUCESS);
      return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
      if (this.contract == null) {
        throw new ContractValidateException("No contract!");
      }
      if (dbManager == null) {
        throw new ContractValidateException("No dbManager!");
      }
      if (!this.contract.is(FutureWithdrawContract.class)) {
        throw new ContractValidateException("contract type error,expected type [FutureWithdrawContract], real type[" + contract.getClass() + "]");
      }
      final FutureWithdrawContract ctx;
      try {
        ctx = this.contract.unpack(FutureWithdrawContract.class);
      } catch (InvalidProtocolBufferException e) {
        logger.debug(e.getMessage(), e);
        throw new ContractValidateException(e.getMessage());
      }
      byte[] ownerAddress = ctx.getOwnerAddress().toByteArray();
      if (!Wallet.addressValid(ownerAddress)) {
        throw new ContractValidateException("Invalid address");
      }

      AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      if (accountCapsule == null) {
        String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
        throw new ContractValidateException(ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] not exists");
      }

      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

      boolean isGP = Args.getInstance()
              .getGenesisBlock()
              .getWitnesses()
              .stream()
              .anyMatch(witness -> Arrays.equals(ownerAddress, witness.getAddress()));
      if (isGP) {
        throw new ContractValidateException(ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] is a guard representative and is not allowed to withdraw Balance");
      }

      if (accountCapsule.getFutureSupplyCount() <= 0) {
        throw new ContractValidateException("witnessAccount does not have any future balance");
      }

      return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(FutureWithdrawContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TRANSFER_FEE;
  }
}
