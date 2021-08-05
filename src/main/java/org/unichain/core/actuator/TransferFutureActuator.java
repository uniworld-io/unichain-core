package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.storage.Deposit;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.FutureTransferContract;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class TransferFutureActuator extends AbstractActuator {

  TransferFutureActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      FutureTransferContract ctx = contract.unpack(FutureTransferContract.class);
      long amount = ctx.getAmount();
      byte[] toAddress = ctx.getToAddress().toByteArray();
      byte[] ownerAddress = ctx.getOwnerAddress().toByteArray();
      AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        boolean withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      dbManager.adjustBalance(ownerAddress, -amount);
      dbManager.addFutureBalance(toAddress, amount, ctx.getExpireTime());
      return true;
    } catch (BalanceInsufficientException | ArithmeticException | InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(Contract.FutureTransferContract.class)) {
      throw new ContractValidateException("contract type error,expected type [FutureTransferContract],real type[" + contract.getClass() + "]");
    }
    long fee = calcFee();
    final FutureTransferContract ctx;
    try {
      ctx = contract.unpack(FutureTransferContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    byte[] toAddress = ctx.getToAddress().toByteArray();
    byte[] ownerAddress = ctx.getOwnerAddress().toByteArray();
    long amount = ctx.getAmount();
    if (amount <= 0)
      throw new ContractValidateException("Amount must greater than 0.");

    if (ctx.getExpireTime() <= dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("expire time should be greater than HeadBlockTime");

    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }
    if (!Wallet.addressValid(toAddress)) {
      throw new ContractValidateException("Invalid toAddress");
    }

    if (Arrays.equals(toAddress, ownerAddress)) {
      throw new ContractValidateException("Cannot transfer unw to yourself.");
    }

    AccountCapsule ownerAccount = dbManager.getAccountStore().get(ownerAddress);
    if (ownerAccount == null)
      throw new ContractValidateException("Validate TransferContract error, no OwnerAccount.");

    long balance = ownerAccount.getBalance();
    try {
      AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      //after TvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      if (dbManager.getDynamicPropertiesStore().getAllowTvmSolidity059() == 1
              && toAccount != null
              && toAccount.getType() == AccountType.Contract) {
        throw new ContractValidateException("Cannot transfer unw to smartContract.");
      }

      if (balance < Math.addExact(amount, fee)) {
        throw new ContractValidateException("Validate TransferContract error, balance is not sufficient.");
      }

      if (toAccount != null) {
        Math.addExact(toAccount.getBalance(), amount);
      }
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(FutureTransferContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return ChainConstant.FUTURE_TRANSFER_FEE;
  }
}