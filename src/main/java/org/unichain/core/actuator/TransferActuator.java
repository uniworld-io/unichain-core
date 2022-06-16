package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.storage.Deposit;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class TransferActuator extends AbstractActuator {

  public TransferActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(TransferContract.class);
      var amount = ctx.getAmount();
      var toAddress = ctx.getToAddress().toByteArray();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();

      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (Objects.isNull(toAccount)) {
        var withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      dbManager.adjustBalance(ownerAddress, -amount);
      dbManager.adjustBalance(toAddress, amount);
      return true;
    } catch (BalanceInsufficientException | ArithmeticException | InvalidProtocolBufferException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(TransferContract.class), "Contract type error,expected type [TransferContract], real type[" + contract.getClass() + "]");

      var fee = calcFee();
      var ctx = contract.unpack(TransferContract.class);

      var toAddress = ctx.getToAddress().toByteArray();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var amount = ctx.getAmount();
      Assert.isTrue(amount > 0, "Amount must greater than 0.");
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress!");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid ownerAddress!");
      Assert.isTrue(!Arrays.equals(toAddress, ownerAddress), "Cannot transfer unw to yourself!");

      AccountCapsule ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "Validate TransferContract error, no OwnerAccount!");

      var balance = ownerAccount.getBalance();
      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      //after UvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      var transferToSmartContract  = dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1
              && toAccount != null
              && toAccount.getType() == AccountType.Contract;
      Assert.isTrue(!transferToSmartContract, "Cannot transfer unw to smartContract");
      Assert.isTrue(balance >= Math.addExact(amount, fee), "Validate TransferContract error, balance is not sufficient.");

      if (toAccount != null) {
        Math.addExact(toAccount.getBalance(), amount); //check if overflow
      }

      return true;
    }
    catch (IllegalArgumentException | InvalidProtocolBufferException | ArithmeticException e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  public static boolean validateForSmartContract(Deposit deposit, byte[] ownerAddress, byte[] toAddress, long amount) throws ContractValidateException {
    Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
    Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");

    Assert.isTrue(!Arrays.equals(toAddress, ownerAddress), "Cannot transfer unw to yourself.");

    var ownerAccount = deposit.getAccount(ownerAddress);
    Assert.notNull(ownerAccount, "Validate InternalTransfer error, no OwnerAccount.");

    var toAccount = deposit.getAccount(toAddress);
    Assert.notNull(toAccount, "Validate InternalTransfer error, no ToAccount. And not allowed to create account in smart contract.");

    var balance = ownerAccount.getBalance();

    Assert.isTrue(amount >= 0, "Amount must greater than or equals 0.");

    try {
      Assert.isTrue(balance >= amount, "Validate InternalTransfer error, balance is not sufficient.");
      Math.addExact(toAccount.getBalance(), amount);
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(TransferContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return ChainConstant.TRANSFER_FEE;
  }
}
