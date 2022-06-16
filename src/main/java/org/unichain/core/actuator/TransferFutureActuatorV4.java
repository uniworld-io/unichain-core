package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.ActuatorUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.FutureTransferContract;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

import static org.unichain.core.services.http.utils.Util.FUTURE_TRANSFER_FIELD_TO_ADDR;

@Slf4j(topic = "actuator")
public class TransferFutureActuatorV4 extends AbstractActuator {
  public TransferFutureActuatorV4(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(FutureTransferContract.class);
      var amount = ctx.getAmount();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      val selfLock = !ctx.hasField(FUTURE_TRANSFER_FIELD_TO_ADDR) || (Arrays.equals(ctx.getToAddress().toByteArray(), ownerAddress));
      var toAddress = selfLock ? ownerAddress : ctx.getToAddress().toByteArray();

      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (Objects.isNull(toAccount)) {
        var withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      chargeFee(ownerAddress, fee);
      dbManager.adjustBalance(ownerAddress, -amount);
      ActuatorUtil.addFutureDeal(dbManager, toAddress, amount, ctx.getExpireTime());
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (Exception e) {
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
      Assert.isTrue(contract.is(FutureTransferContract.class), "contract type error,expected type [FutureTransferContract],real type[" + contract.getClass() + "]");

      var fee = calcFee();
      val ctx = contract.unpack(FutureTransferContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      val selfLock = !ctx.hasField(FUTURE_TRANSFER_FIELD_TO_ADDR) || (Arrays.equals(ctx.getToAddress().toByteArray(), ownerAddress));
      var toAddress = selfLock ? ownerAddress : ctx.getToAddress().toByteArray();

      var amount = ctx.getAmount();
      Assert.isTrue(amount > 0, "Amount must greater than 0.");

      var maxExpireTime = Math.addExact(dbManager.getHeadBlockTimeStamp(), dbManager.getMaxFutureTransferTimeDurationUnwV3());
      Assert.isTrue((ctx.getExpireTime() > dbManager.getHeadBlockTimeStamp()) && (ctx.getExpireTime() <= maxExpireTime),
                      "expire time must greater current block time, lower than maximum timestamp:" + maxExpireTime);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");

      AccountCapsule ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "no OwnerAccount found");

      var balance = ownerAccount.getBalance();
      var toAccount = dbManager.getAccountStore().get(toAddress);
      
      if (toAccount == null) {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      //after UvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      var transferToSmartContract = (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1)
              && toAccount != null
              && toAccount.getType() == AccountType.Contract;
      Assert.isTrue(!transferToSmartContract, "Cannot transfer/self-lock unw to/by smartContract.");

      Assert.isTrue(balance >= Math.addExact(amount, fee), "Validate TransferContract error, balance is not sufficient");

      if ((toAccount != null) && !selfLock) {
        Math.addExact(toAccount.getBalance(), amount);//check if overflow
      }

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(FutureTransferContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return ChainConstant.TRANSFER_FEE;
  }

}