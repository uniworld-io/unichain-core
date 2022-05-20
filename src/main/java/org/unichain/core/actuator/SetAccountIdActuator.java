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
import org.unichain.protos.Contract.SetAccountIdContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class SetAccountIdActuator extends AbstractActuator {

  public SetAccountIdActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    val fee = calcFee();
    try {
      val ctx = contract.unpack(SetAccountIdContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountStore = dbManager.getAccountStore();
      var accountIdIndexStore = dbManager.getAccountIdIndexStore();
      var ownerAccount = accountStore.get(ownerAddress);

      ownerAccount.setAccountId(ctx.getAccountId().toByteArray());
      accountStore.put(ownerAddress, ownerAccount);
      accountIdIndexStore.put(ownerAccount);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    }
    catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(SetAccountIdContract.class), "contract type error,expected type [SetAccountIdContract],real type[" + contract.getClass() + "]");

      val ctx = contract.unpack(SetAccountIdContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountId = ctx.getAccountId().toByteArray();
      Assert.isTrue(TransactionUtil.validAccountId(accountId), "Invalid accountId");
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");

      var account = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(account, "Account has not existed");

      var accountAlready = account.getAccountId() != null && !account.getAccountId().isEmpty();
      Assert.isTrue(!accountAlready, "This account id already set");
      Assert.isTrue(!dbManager.getAccountIdIndexStore().has(accountId), "This id has existed");

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(SetAccountIdContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
