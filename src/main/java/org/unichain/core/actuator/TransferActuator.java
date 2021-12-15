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

  TransferActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      TransferContract transferContract = contract.unpack(TransferContract.class);
      long amount = transferContract.getAmount();
      byte[] toAddress = transferContract.getToAddress().toByteArray();
      byte[] ownerAddress = transferContract.getOwnerAddress().toByteArray();

      AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
      if (Objects.isNull(toAccount)) {
        boolean withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
        fee += dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      dbManager.adjustBalance(ownerAddress, -amount);
      dbManager.adjustBalance(toAddress, amount);
      return true;
    } catch (BalanceInsufficientException | ArithmeticException | InvalidProtocolBufferException e) {
      logger.error(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(TransferContract.class), "contract type error,expected type [TransferContract], real type[" + contract.getClass() + "]");

      var fee = calcFee();
      TransferContract transferContract = contract.unpack(TransferContract.class);

      byte[] toAddress = transferContract.getToAddress().toByteArray();
      byte[] ownerAddress = transferContract.getOwnerAddress().toByteArray();
      long amount = transferContract.getAmount();
      Assert.isTrue(amount > 0, "Amount must greater than 0.");
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress!");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid ownerAddress!");
      Assert.isTrue(!Arrays.equals(toAddress, ownerAddress), "Cannot transfer unw to yourself!");

      AccountCapsule ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "Validate TransferContract error, no OwnerAccount!");

      long balance = ownerAccount.getBalance();

      AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        fee += dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      //after UvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      boolean transferToSmartContract  = dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1
              && toAccount != null
              && toAccount.getType() == AccountType.Contract;
      Assert.isTrue(!transferToSmartContract, "Cannot transfer unw to smartContract");
      Assert.isTrue(balance >= Math.addExact(amount, fee), "Validate TransferContract error, balance is not sufficient.");

      if (toAccount != null) {
        Math.addExact(toAccount.getBalance(), amount);
      }

      return true;
    }
    catch (IllegalArgumentException | InvalidProtocolBufferException | ArithmeticException e){
      throw new ContractValidateException(e.getMessage());
    }
  }

  public static boolean validateForSmartContract(Deposit deposit, byte[] ownerAddress, byte[] toAddress, long amount) throws ContractValidateException {
    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }
    if (!Wallet.addressValid(toAddress)) {
      throw new ContractValidateException("Invalid toAddress");
    }

    if (Arrays.equals(toAddress, ownerAddress)) {
      throw new ContractValidateException("Cannot transfer unw to yourself.");
    }

    AccountCapsule ownerAccount = deposit.getAccount(ownerAddress);
    if (ownerAccount == null) {
      throw new ContractValidateException("Validate InternalTransfer error, no OwnerAccount.");
    }

    AccountCapsule toAccount = deposit.getAccount(toAddress);
    if (toAccount == null) {
      throw new ContractValidateException("Validate InternalTransfer error, no ToAccount. And not allowed to create account in smart contract.");
    }

    long balance = ownerAccount.getBalance();

    if (amount < 0) {
      throw new ContractValidateException("Amount must greater than or equals 0.");
    }

    try {
      if (balance < amount) {
        throw new ContractValidateException("Validate InternalTransfer error, balance is not sufficient.");
      }
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