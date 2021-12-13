package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.AccountCreateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class CreateAccountActuator extends AbstractActuator {

  CreateAccountActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var accountCreateContract = contract.unpack(AccountCreateContract.class);
      var withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
      var accountCapsule = new AccountCapsule(accountCreateContract, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
      dbManager.getAccountStore().put(accountCreateContract.getAccountAddress().toByteArray(), accountCapsule);
      chargeFee(accountCreateContract.getOwnerAddress().toByteArray(), fee);
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
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(AccountCreateContract.class),"Contract type error,expected type [AccountCreateContract], real type[" + contract.getClass() + "]");

      val contract = this.contract.unpack(AccountCreateContract.class);
      //    if (contract.getAccountName().isEmpty()) {
      //      throw new ContractValidateException("AccountName is null");
      //    }
      var ownerAddress = contract.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      val fee = calcFee();
      Assert.isTrue(accountCapsule.getBalance() >= fee, "Validate CreateAccountActuator error, insufficient fee.");

      var accountAddress = contract.getAccountAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(accountAddress), "Invalid account address");

      //    if (contract.getType() == null) {
      //      throw new ContractValidateException("Type is null");
      //    }
      Assert.isTrue(!dbManager.getAccountStore().has(accountAddress), "Account has existed");

      return true;
    }
    catch (Exception e){
      logger.error(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AccountCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
  }
}
