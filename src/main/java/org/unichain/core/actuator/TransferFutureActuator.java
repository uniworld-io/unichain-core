package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.FutureTransferCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.FutureTransferContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class TransferFutureActuator extends AbstractActuator {
  TransferFutureActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      var ctx = contract.unpack(FutureTransferContract.class);
      var amount = ctx.getAmount();
      var toAddress = ctx.getToAddress().toByteArray();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (Objects.isNull(toAccount)) {
        boolean withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
        fee += dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      dbManager.adjustBalance(ownerAddress, -amount);
      addFutureBalance(toAddress, amount, ctx.getExpireTime());
      return true;
    } catch (Exception e) {
      logger.error("exec transfer future got error", e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.isTrue(contract != null, "No contract!");
      Assert.isTrue(dbManager != null, "No dbManager!");
      Assert.isTrue(contract.is(FutureTransferContract.class), "contract type error,expected type [FutureTransferContract],real type[" + contract.getClass() + "]");

      var fee = calcFee();
      val ctx = contract.unpack(FutureTransferContract.class);

      var toAddress = ctx.getToAddress().toByteArray();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var amount = ctx.getAmount();
      Assert.isTrue(amount > 0, "Amount must greater than 0.");

      long maxExpireTime = dbManager.getHeadBlockTimeStamp() + dbManager.getMaxFutureTransferTimeDurationUnw();
      Assert.isTrue((ctx.getExpireTime() > dbManager.getHeadBlockTimeStamp()) && (ctx.getExpireTime() <= maxExpireTime),
                      "expire time must greater current block time, lower than maximum timestamp:" + maxExpireTime);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");
      Assert.isTrue(!Arrays.equals(toAddress, ownerAddress), "Cannot transfer unw to yourself");

      AccountCapsule ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(ownerAccount != null, "no OwnerAccount found");

      var balance = ownerAccount.getBalance();
      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      //after TvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      if (dbManager.getDynamicPropertiesStore().getAllowTvmSolidity059() == 1
              && toAccount != null
              && toAccount.getType() == AccountType.Contract) {
        throw new ContractValidateException("Cannot transfer unw to smartContract.");
      }

      Assert.isTrue(balance >= Math.addExact(amount, fee), "Validate TransferContract error, balance is not sufficient");

      if (toAccount != null) {
        Math.addExact(toAccount.getBalance(), amount);
      }

      return true;
    }
    catch (Exception e){
      logger.error("validate TransferFutureActuator got error --> ", e);
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

  private void addFutureBalance(byte[] toAddress, long amount, long availableTime){
    var tickDay = Util.makeDayTick(availableTime);
    var tickKey = Util.makeFutureTransferIndexKey(toAddress, tickDay);

    var futureStore = dbManager.getFutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var toAcc = accountStore.get(toAddress);
    var summary = toAcc.getFutureSummary();

    /**
     * tick exist: the fasted way!
     */
    if(futureStore.has(tickKey)){
      //save tick
      var tick = futureStore.get(tickKey);
      tick.addBalance(amount);
      futureStore.put(tickKey, tick);

      //save summary
      summary = summary.toBuilder()
              .setTotalBalance(summary.getTotalBalance() + amount)
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /**
     * first tick ever: add new tick, add summary
     */
    if(Objects.isNull(summary)){
      //save new tick
      var tick = Protocol.Future.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .clearPrevTick()
              .build();
      futureStore.put(tickKey, new FutureTransferCapsule(tick));

      //save summary
      summary = Protocol.FutureSummary.newBuilder()
              .setTotalBalance(amount)
              .setTotalDeal(1)
              .setUpperTime(tickDay)
              .setLowerTime(tickDay)
              .setLowerTick(ByteString.copyFrom(tickKey))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /**
     * other ticks exist
     */
    var headKey = summary.getLowerTick().toByteArray();
    var head = futureStore.get(headKey);
    var headTime = head.getExpireTime();

    /**
     * if new tick is head
     */
    if(tickDay < headTime){
      //save old head
      head.setPrevTick(ByteString.copyFrom(tickKey));
      futureStore.put(headKey, head);

      //save new head
      var newHead = Protocol.Future.newBuilder()
              .setExpireTime(tickDay)
              .setFutureBalance(amount)
              .setNextTick(summary.getLowerTick())
              .clearPrevTick()
              .build();
      futureStore.put(tickKey, new FutureTransferCapsule(newHead));

      //save summary
      summary = summary.toBuilder()
              .setLowerTime(tickDay)
              .setTotalDeal(summary.getTotalDeal() +1)
              .setTotalBalance(summary.getTotalBalance() + amount)
              .setLowerTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
      return ;
    }

    /**
     * if new tick is tail
     */
    if(tickDay > headTime){
      //save new tail
      var oldTailKeyBs = summary.getUpperTick();
      var newTail = Protocol.Future.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .setPrevTick(oldTailKeyBs)
              .build();
      futureStore.put(tickKey, new FutureTransferCapsule(newTail));

      //save old tail
      var oldTail = futureStore.get(oldTailKeyBs.toByteArray());
      oldTail.setNextTick(ByteString.copyFrom(tickKey));
      futureStore.put(oldTailKeyBs.toByteArray(), oldTail);

      //save summary
      summary = summary.toBuilder()
              .setTotalDeal(summary.getTotalDeal() + 1)
              .setTotalBalance(summary.getTotalBalance() + amount)
              .setUpperTick(ByteString.copyFrom(tickKey))
              .setUpperTime(tickDay)
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /**
     * otherwise: lookup slot to insert
     */
    var searchKeyBs = summary.getUpperTick();
    while (true){
      var searchTick = futureStore.get(searchKeyBs.toByteArray());
      if(searchTick.getExpireTime() < tickDay)
      {
        var oldNextTickKey = searchTick.getNextTick();

        //save new tick
        var newTick = Protocol.Future.newBuilder()
                .setExpireTime(tickDay)
                .setFutureBalance(amount)
                .setPrevTick(searchKeyBs)
                .setNextTick(oldNextTickKey)
                .build();
        futureStore.put(tickKey, new FutureTransferCapsule(newTick));

        //save prev
        searchTick.setNextTick(ByteString.copyFrom(tickKey));
        futureStore.put(searchKeyBs.toByteArray(), searchTick);

        //save next
        var oldNextTick = futureStore.get(oldNextTickKey.toByteArray());
        oldNextTick.setPrevTick(ByteString.copyFrom(tickKey));
        futureStore.put(oldNextTickKey.toByteArray(), oldNextTick);

        //save summary
        summary = summary.toBuilder()
                .setTotalBalance(summary.getTotalBalance() + amount)
                .setTotalDeal(summary.getTotalDeal() +1)
                .build();

        toAcc.setFutureSummary(summary);
        accountStore.put(toAddress, toAcc);
        return;
      }
      else {
        searchKeyBs = searchTick.getPrevTick();
        continue;
      }
    }
  }
}