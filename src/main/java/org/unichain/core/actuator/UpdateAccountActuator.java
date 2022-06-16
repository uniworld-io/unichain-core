package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.AccountUpdateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class UpdateAccountActuator extends AbstractActuator {

  public UpdateAccountActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    val fee = calcFee();
    try {
      val ctx = contract.unpack(AccountUpdateContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountStore = dbManager.getAccountStore();
      var accountIndexStore = dbManager.getAccountIndexStore();
      var account = accountStore.get(ownerAddress);

      account.setAccountName(ctx.getAccountName().toByteArray());
      accountStore.put(ownerAddress, account);
      accountIndexStore.put(account);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
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
      Assert.isTrue(this.contract.is(AccountUpdateContract.class), "Contract type error,expected type [AccountUpdateContract],real type[" + contract.getClass() + "]");

      val ctx = contract.unpack(AccountUpdateContract.class);

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountName = ctx.getAccountName().toByteArray();
      Assert.isTrue(TransactionUtil.validAccountName(accountName), "Invalid accountName");
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");

      var account = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(account, "Account has not existed");

      var accountNameExist = account.getAccountName() != null && !account.getAccountName().isEmpty()
              && dbManager.getDynamicPropertiesStore().getAllowUpdateAccountName() == 0;
      Assert.isTrue(!accountNameExist, "This account name already exist");

      var nameExist = dbManager.getAccountIndexStore().has(accountName)
              && dbManager.getDynamicPropertiesStore().getAllowUpdateAccountName() == 0;
      Assert.isTrue(!nameExist, "This name has existed");

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AccountUpdateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
