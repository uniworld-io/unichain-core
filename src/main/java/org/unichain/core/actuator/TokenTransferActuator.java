/*
 * unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * unichain-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.unichain.core.actuator;

import com.google.common.math.LongMath;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.FutureTokenCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.TransferTokenContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class TokenTransferActuator extends AbstractActuator {

  TokenTransferActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      TransferTokenContract ctx = contract.unpack(TransferTokenContract.class);
      logger.info("exec TokenTransferActuator from {} to {} token {} amount {}  ...", ctx.getOwnerAddress(), ctx.getToAddress(), ctx.getTokenName(), ctx.getAmount());
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddr);
      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      var tokenPoolOwnerAddr = tokenPool.getOwnerAddress().toByteArray();
      var toAddress = ctx.getToAddress().toByteArray();
      //if account with to_address does not exist, create it first.
      AccountCapsule toAccountCap = dbManager.getAccountStore().get(toAddress);
      if (toAccountCap == null) {
        boolean withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        toAccountCap = new AccountCapsule(ByteString.copyFrom(toAddress), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      //transfer token
      if(Arrays.equals(ownerAddr, tokenPoolOwnerAddr)){
        //owner of token transfer, don't charge fee
        ownerAccountCap.burnToken(tokenKey, ctx.getAmount());
        dbManager.getAccountStore().put(ownerAddr, ownerAccountCap);

        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addToken(tokenKey, ctx.getAmount());
          dbManager.getAccountStore().put(toAddress, toAccountCap);
        }
        else
         addFutureTokenV2(toAddress, tokenKey, ctx.getAmount(), ctx.getAvailableTime());
      }
      else {
        var tokenFee = tokenPool.getFee() + LongMath.divide(ctx.getAmount() * tokenPool.getExtraFeeRate(), 100, RoundingMode.CEILING);
        var tokenPoolOwnerCap = dbManager.getAccountStore().get(tokenPoolOwnerAddr);
        tokenPoolOwnerCap.mineToken(tokenKey, tokenFee);
        dbManager.getAccountStore().put(tokenPoolOwnerAddr, tokenPoolOwnerCap);

        ownerAccountCap.burnToken(tokenKey, (tokenFee + ctx.getAmount()));
        dbManager.getAccountStore().put(ownerAddr, ownerAccountCap);

        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addToken(tokenKey, ctx.getAmount());
          dbManager.getAccountStore().put(toAddress, toAccountCap);
        }
        else
          addFutureTokenV2(toAddress, tokenKey, ctx.getAmount(), ctx.getAvailableTime());

      }

      //charge pool fee
      tokenPool.setFeePool(tokenPool.getFeePool() - fee);
      tokenPool.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getTokenPoolStore().put(tokenKey, tokenPool);
      dbManager.burnFee(fee);

      ret.setStatus(fee, code.SUCESS);
      logger.info("exec TokenTransferActuator from {} to {} token {} amount {} ... DONE!", ctx.getOwnerAddress(), ctx.getToAddress(), ctx.getTokenName(), ctx.getAmount());
      return true;
    } catch (InvalidProtocolBufferException | ArithmeticException | BalanceInsufficientException e) {
      logger.error("exec TokenTransferActuator {}" , e.getMessage(), e);
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

    if (!this.contract.is(TransferTokenContract.class)) {
      throw new ContractValidateException("contract type error, expected type [TransferTokenContract],real type[" + contract.getClass() + "]");
    }

    long fee = calcFee();

    final TransferTokenContract ctx;
    try {
      ctx = this.contract.unpack(TransferTokenContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    var ownerAddress = ctx.getOwnerAddress().toByteArray();
    var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
    if(Objects.isNull(ownerAccountCap))
      throw new ContractValidateException("Owner account not found");

    var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
    var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
    if(Objects.isNull(tokenPool))
      throw new ContractValidateException("Token pool not found: " + ctx.getTokenName());

    if(dbManager.getHeadBlockTimeStamp() >= tokenPool.getEndTime())
      throw new ContractValidateException("Token expired at: "+ Utils.formatDateLong(tokenPool.getEndTime()));

    if(dbManager.getHeadBlockTimeStamp() < tokenPool.getStartTime())
      throw new ContractValidateException("Token pending to start at: "+ Utils.formatDateLong(tokenPool.getStartTime()));

    if(ctx.getAvailableTime() > 0)
    {
      if(ctx.getAmount() < tokenPool.getLot())
        throw new ContractValidateException("future transfer require at least amount of token : "+ tokenPool.getLot());
    }

    var toAddress = ctx.getToAddress().toByteArray();
    if(Arrays.equals(ownerAddress, toAddress))
      throw new ContractValidateException("Transfer to itself not allowed");
    if (!Wallet.addressValid(toAddress)) {
      throw new ContractValidateException("Invalid toAddress");
    }

    var toAccountCap = dbManager.getAccountStore().get(toAddress);
    if (toAccountCap == null) {
      fee += dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
    }

    if(tokenPool.getFeePool() < fee)
      throw new ContractValidateException("not enough token pool fee balance");

    if(ctx.getAmount() <= 0)
      throw new ContractValidateException("invalid transfer amount, expect positive number");

    //estimate new fee
    long tokenFee;
    if(Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()))
    {
      tokenFee = 0;
    }
    else {
      tokenFee = tokenPool.getFee() + LongMath.divide(ctx.getAmount() * tokenPool.getExtraFeeRate(), 100, RoundingMode.CEILING);
    }

    if(ownerAccountCap.getTokenAvailable(tokenKey) < ctx.getAmount() + tokenFee)
      throw new ContractValidateException("not enough token balance");

    if(ctx.getAvailableTime() > 0){
      if(ctx.getAvailableTime() >= tokenPool.getEndTime())
        throw new ContractValidateException("available time exceeded token expired time");

      if(ctx.getAvailableTime() <= dbManager.getHeadBlockTimeStamp())
        throw new ContractValidateException("block time passed available time");
    }

    //after TvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
    if (dbManager.getDynamicPropertiesStore().getAllowTvmSolidity059() == 1
            && toAccountCap != null
            && toAccountCap.getType() == Protocol.AccountType.Contract) {
      throw new ContractValidateException("Cannot transfer token to smartContract.");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(TransferTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }

  private void addFutureTokenV2(byte[] toAddress, byte[] tokenKey, long amount, long availableTime){
    var tokenName = new String(tokenKey);
    var tickDay = Util.makeDayTick(availableTime);
    var tickKey = Util.makeFutureTokenIndexKey(toAddress, tokenKey, tickDay);

    var tokenStore = dbManager.getFutureTokenStore();
    var accountStore = dbManager.getAccountStore();
    var toAcc = accountStore.get(toAddress);
    var summary = toAcc.getFutureTokenSummary(tokenName);
    /**
     * tick exist: the fasted way!
     */
    if(tokenStore.has(tickKey)){
        //update tick
        var tick = tokenStore.get(tickKey);
        tick.addBalance(amount);
        tokenStore.put(tickKey, tick);

        //update account summary
        summary = summary.toBuilder().setTotalValue(summary.getTotalValue() + amount).build();
        toAcc.setFutureTokenSummary(summary);
        accountStore.put(toAddress, toAcc);
        return;
    }

    /**
     * tick not exist: but no other ticks exist
     */
    if(summary == null){
      /*
        no deal exist: new tick, new summary
       */
      //new tick
      var tick = Protocol.FutureTokenV2.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .clearPrevTick()
              .build();
      tokenStore.put(tickKey, new FutureTokenCapsule(tick));

      //save summary
      summary = Protocol.FutureTokenSummaryV2.newBuilder()
              .setTokenName(tokenName)
              .setTotalValue(amount)
              .setTotalDeal(1)
              .setUpperBoundTime(tickDay)
              .setLowerBoundTime(tickDay)
              .setLowerTick(ByteString.copyFrom(tickKey))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setFutureTokenSummary(summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /**
     * other tick exist
     */
    var headKey = summary.getLowerTick().toByteArray();
    var head = tokenStore.get(headKey);
    var headTime = head.getExpireTime();
    /**
     * if new tick is head
     */
    if(tickDay < headTime){
      //new tick is just a new head
      head.setPrevTick(ByteString.copyFrom(tickKey));
      tokenStore.put(headKey, head);

      //save new head
      var newHead = Protocol.FutureTokenV2.newBuilder()
              .setExpireTime(tickDay)
              .setFutureBalance(amount)
              .setNextTick(summary.getLowerTick())
              .clearPrevTick()
              .build();
      tokenStore.put(tickKey, new FutureTokenCapsule(newHead));

      //update summary
      summary = summary.toBuilder()
              .setLowerBoundTime(tickDay)
              .setTotalDeal(summary.getTotalDeal() +1)
              .setTotalValue(summary.getTotalValue() + amount)
              .setLowerTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setFutureTokenSummary(summary);
      accountStore.put(toAddress, toAcc);
      return ;
    }

    /**
     * if new tick is tail
     */
    if(tickDay > headTime){
      //new tail
      var oldTailKeyBs = summary.getUpperTick();
      var newTail = Protocol.FutureTokenV2.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .setPrevTick(oldTailKeyBs)
              .build();
      tokenStore.put(tickKey, new FutureTokenCapsule(newTail));

      //update old tail
      var oldTail = tokenStore.get(oldTailKeyBs.toByteArray());
      oldTail.setNextTick(ByteString.copyFrom(tickKey));
      tokenStore.put(oldTailKeyBs.toByteArray(), oldTail);

      //update summary
      summary = summary.toBuilder()
              .setTotalDeal(summary.getTotalDeal() + 1)
              .setTotalValue(summary.getTotalValue() + amount)
              .setUpperTick(ByteString.copyFrom(tickKey))
              .setUpperBoundTime(tickDay)
              .build();
      toAcc.setFutureTokenSummary(summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /**
     * lookup slot between head and tail
     */
    var searchKeyBs = summary.getUpperTick();
    while (true){
      var searchTick = tokenStore.get(searchKeyBs.toByteArray());
      if(searchTick.getExpireTime() < tickDay)
      {
        /*
          found: update & quit
         */
        //save new tick
        var oldNextTickKey = searchTick.getNextTick();
        var newToken = Protocol.FutureTokenV2.newBuilder()
                .setExpireTime(tickDay)
                .setFutureBalance(amount)
                .setPrevTick(searchKeyBs)
                .setNextTick(oldNextTickKey)
                .build();
        tokenStore.put(tickKey, new FutureTokenCapsule(newToken));

        //update prev

        searchTick.setNextTick(ByteString.copyFrom(tickKey));
        tokenStore.put(searchKeyBs.toByteArray(), searchTick);

        //update next
        var oldNextTick = tokenStore.get(oldNextTickKey.toByteArray());
        oldNextTick.setPrevTick(ByteString.copyFrom(tickKey));

        //update summary
        summary = summary.toBuilder()
                .setTotalValue(summary.getTotalValue() + amount)
                .setTotalDeal(summary.getTotalDeal() +1)
                .build();

        toAcc.setFutureTokenSummary(summary);
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
