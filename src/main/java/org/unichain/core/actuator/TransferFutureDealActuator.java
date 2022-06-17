package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.ActuatorUtil;
import org.unichain.core.Wallet;
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
  private static Descriptors.FieldDescriptor TRANSFER_FUTURE_DEAL_FIELD_AMT = Contract.FutureDealTransferContract.getDescriptor().findFieldByNumber(Contract.FutureDealTransferContract.AMOUNT_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor TRANSFER_FUTURE_DEAL_FIELD_DEAL_ID = Contract.FutureDealTransferContract.getDescriptor().findFieldByNumber(Contract.FutureDealTransferContract.DEAL_ID_FIELD_NUMBER);

  public TransferFutureDealActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var accountStore = dbManager.getAccountStore();
      var futureStore = dbManager.getFutureTransferStore();

      var ctx = contract.unpack(Contract.FutureDealTransferContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      var dealId = ctx.getDealId();

      //create toAddress
      if (!accountStore.has(toAddr)) {
        dbManager.createDefaultAccount(toAddr, Protocol.AccountType.Normal);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      //real amount
      var ownerDealKey = Util.makeFutureTransferIndexKey(ownerAddr, Util.makeDayTick(dealId));
      var futureDeal = futureStore.get(ownerDealKey);
      var amt = ctx.hasField(TRANSFER_FUTURE_DEAL_FIELD_AMT) ? ctx.getAmount() : futureDeal.getBalance();

      ActuatorUtil.cutFutureDeal(dbManager, ownerAddr, ownerDealKey, amt);
      ActuatorUtil.addFutureDeal(dbManager, toAddr, amt, dealId);

      chargeFee(ownerAddr, fee);
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
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      Assert.isTrue(ctx.hasField(TRANSFER_FUTURE_DEAL_FIELD_DEAL_ID) && ctx.getDealId() > 0, "Missing or invalid dealId");

      Assert.isTrue(Wallet.addressValid(ownerAddr), "Invalid ownerAddress");
      Assert.isTrue(Wallet.addressValid(toAddr), "Invalid toAddress");
      Assert.isTrue(!Arrays.equals(toAddr, ownerAddr), "Cannot transfer to yourself");

      var accountStore = dbManager.getAccountStore();
      Assert.isTrue(accountStore.has(ownerAddr), "Unrecognized owner account");

      var toAccount = accountStore.get(toAddr);

      var fee = calcFee();
      if (Objects.isNull(toAccount)) {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      else {
        Assert.isTrue(toAccount.getType() != Protocol.AccountType.Contract, "Can not transfer to contract account");
      }

      /**
        Check if owner doesn't have locked tick
      */
      var dealId = Util.makeFutureTransferIndexKey(ownerAddr, Util.makeDayTick(ctx.getDealId()));
      var futureStore = dbManager.getFutureTransferStore();
      Assert.isTrue(futureStore.has(dealId) && futureStore.get(dealId).getBalance() > 0, "No future deal or not enough future balance for dealId: " + ctx.getDealId());

      if(ctx.hasField(TRANSFER_FUTURE_DEAL_FIELD_AMT)){
        Assert.isTrue(ctx.getAmount() > 0
                && ctx.getAmount() >= 2 * 1000000L, "Invalid amount: at least 2 UNW required");
        Assert.isTrue(ctx.getAmount() <= futureStore.get(dealId).getBalance()
                , "Invalid amount: not enough future balance");
      }

      Assert.isTrue(accountStore.get(ownerAddr).getBalance() > fee, "Validate TransferContract error, balance is not sufficient");

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
}
