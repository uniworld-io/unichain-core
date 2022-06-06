package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.ActuatorUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class TransferFutureDealActuator extends AbstractActuator {
  public TransferFutureDealActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Contract.FutureDealTransferContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var toAddress = ctx.getToAddress().toByteArray();
      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (Objects.isNull(toAccount)) {
        var withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      var dealId = ctx.getDealId();
      chargeFee(ownerAddress, fee);
      transferFutureDeal(ownerAddress, toAddress, dealId);
      ret.setStatus(fee, Protocol.Transaction.Result.code.SUCESS);
      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, Protocol.Transaction.Result.code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(Contract.FutureDealTransferContract.class), "Contract type error,expected type [FutureDealTransferContract], real type[" + contract.getClass() + "]");

      var ctx = this.contract.unpack(Contract.FutureDealTransferContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");

      var toAddress = ctx.getToAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");

      var maxExpireTime = Math.addExact(dbManager.getHeadBlockTimeStamp(), dbManager.getMaxFutureTransferTimeDurationUnwV3());
      Assert.isTrue((ctx.getDealId() > dbManager.getHeadBlockTimeStamp()) && (ctx.getDealId() <= maxExpireTime),
              "deal id must greater current block time, lower than maximum timestamp:" + maxExpireTime);

      Assert.isTrue(!Arrays.equals(toAddress, ownerAddress), "Cannot transfer unw to yourself");

      var ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "no OwnerAccount found");

      var toAccount = dbManager.getAccountStore().get(toAddress);
      var fee = calcFee();
      if (toAccount == null) {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      /*
      Check if owner doesn't have locked tick
      */
      var tickDay = Util.makeDayTick(ctx.getDealId());
      var tickKey = Util.makeFutureTransferIndexKey(ownerAddress, tickDay);
      var futureStore = dbManager.getFutureTransferStore();
      Assert.isTrue(futureStore.has(tickKey), "OwnerAddress doesn't have future locked with deal id " + ctx.getDealId());

      //after UvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      var transferToSmartContract = (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1)
              && toAccount != null
              && toAccount.getType() == Protocol.AccountType.Contract;
      Assert.isTrue(!transferToSmartContract, "Cannot transfer/self-lock unw to/by smartContract.");

      var balance = ownerAccount.getBalance();
      Assert.isTrue(balance > fee, "Validate TransferContract error, balance is not sufficient");

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Contract.FutureDealTransferContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }

  private void transferFutureDeal(byte[] ownerAddress, byte[] toAddress, long dealId) {
    var tickDay = Util.makeDayTick(dealId);
    var tickKey = Util.makeFutureTransferIndexKey(ownerAddress, tickDay);

    var futureStore = dbManager.getFutureTransferStore();
    var tick = futureStore.get(tickKey);

    ActuatorUtil.removeFutureDeal(dbManager, ownerAddress, tick);
    // change currentTick of ownerAddress to toAddress
    ActuatorUtil.addFutureDeal(dbManager, toAddress, tick.getBalance(), dealId);
    // remove old tickKey
    futureStore.delete(tickKey);
  }
}