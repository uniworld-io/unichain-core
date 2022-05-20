/*
 * Unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Unichain-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.unichain.core.actuator.urc30;

import com.google.common.math.LongMath;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.urc30.Urc30FutureTokenCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
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
public class Urc30TokenTransferActuator extends AbstractActuator {

  public Urc30TokenTransferActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(TransferTokenContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddr);
      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      var tokenPoolOwnerAddr = tokenPool.getOwnerAddress().toByteArray();
      var toAddress = ctx.getToAddress().toByteArray();

      var toAccountCap = dbManager.getAccountStore().get(toAddress);
      if (toAccountCap == null) {
        var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        toAccountCap = new AccountCapsule(ByteString.copyFrom(toAddress), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

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
          addFutureToken(toAddress, tokenKey, ctx.getAmount(), ctx.getAvailableTime());
      }
      else {
        var tokenFee = Math.addExact(tokenPool.getFee(), LongMath.divide(Math.multiplyExact(ctx.getAmount(), tokenPool.getExtraFeeRate()), 100, RoundingMode.CEILING));
        var tokenPoolOwnerCap = dbManager.getAccountStore().get(tokenPoolOwnerAddr);
        tokenPoolOwnerCap.addToken(tokenKey, tokenFee);
        dbManager.getAccountStore().put(tokenPoolOwnerAddr, tokenPoolOwnerCap);

        ownerAccountCap.burnToken(tokenKey, Math.addExact(tokenFee , ctx.getAmount()));
        dbManager.getAccountStore().put(ownerAddr, ownerAccountCap);

        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addToken(tokenKey, ctx.getAmount());
          dbManager.getAccountStore().put(toAddress, toAccountCap);
        }
        else
          addFutureToken(toAddress, tokenKey, ctx.getAmount(), ctx.getAvailableTime());
      }

      //charge pool fee
      tokenPool.setFeePool(Math.subtractExact(tokenPool.getFeePool(), fee));
      tokenPool.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getTokenPoolStore().put(tokenKey, tokenPool);
      dbManager.burnFee(fee);

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
      Assert.isTrue(contract.is(TransferTokenContract.class), "contract type error,expected type [TransferTokenContract],real type[" + contract.getClass() + "]");

      var fee = calcFee();

      val ctx = this.contract.unpack(TransferTokenContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccountCap, "Owner account not found");

      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      Assert.notNull(tokenPool, "Token pool not found: " + ctx.getTokenName());
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));

      if (ctx.getAvailableTime() > 0) {
        Assert.isTrue (ctx.getAvailableTime() > dbManager.getHeadBlockTimeStamp(), "Block time passed available time");
        long maxAvailTime = Math.addExact(dbManager.getHeadBlockTimeStamp(), dbManager.getMaxFutureTransferTimeDurationToken());
        Assert.isTrue (ctx.getAvailableTime() <= maxAvailTime, "Available time limited. Max available timestamp: " + maxAvailTime);
        Assert.isTrue(ctx.getAvailableTime() < tokenPool.getEndTime(), "Available time exceeded token expired time");
        Assert.isTrue(ctx.getAmount() >= tokenPool.getLot(),"Future transfer require minimum amount of : " + tokenPool.getLot());
      }

      var toAddress = ctx.getToAddress().toByteArray();
      Assert.isTrue(!Arrays.equals(ownerAddress, toAddress), "Transfer to itself not allowed");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");

      var toAccountCap = dbManager.getAccountStore().get(toAddress);
      if (toAccountCap == null) {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      Assert.isTrue(tokenPool.getFeePool() >= fee, "Not enough token pool fee balance");

      Assert.isTrue (ctx.getAmount() > 0, "Invalid transfer amount, expect positive number");

      //estimate new fee
      long tokenFee;
      if (Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray())) {
        tokenFee = 0;
      } else {
        tokenFee = Math.addExact(tokenPool.getFee(), LongMath.divide(Math.multiplyExact(ctx.getAmount(), tokenPool.getExtraFeeRate()), 100, RoundingMode.CEILING));
      }

      Assert.isTrue(ownerAccountCap.getTokenAvailable(tokenKey) >= Math.addExact(ctx.getAmount(), tokenFee), "Not enough token balance");

      //after UvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      if (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1
              && toAccountCap != null
              && toAccountCap.getType() == Protocol.AccountType.Contract) {
        throw new ContractValidateException("Cannot transfer token to smartContract.");
      }

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(TransferTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }

  private void addFutureToken(byte[] toAddress, byte[] tokenKey, long amount, long availableTime){
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
        summary = summary.toBuilder().setTotalValue(Math.addExact(summary.getTotalValue(), amount)).build();
        toAcc.setFutureTokenSummary(summary);
        accountStore.put(toAddress, toAcc);
        return;
    }

    /**
     * the first tick ever.
     */
    if(Objects.isNull(summary)){
      //save tick
      var tick = Protocol.FutureTokenV2.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .clearPrevTick()
              .build();
      tokenStore.put(tickKey, new Urc30FutureTokenCapsule(tick));

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
      //save old head pointer
      head.setPrevTick(ByteString.copyFrom(tickKey));
      tokenStore.put(headKey, head);

      //save new head
      var newHead = Protocol.FutureTokenV2.newBuilder()
              .setExpireTime(tickDay)
              .setFutureBalance(amount)
              .setNextTick(summary.getLowerTick())
              .clearPrevTick()
              .build();
      tokenStore.put(tickKey, new Urc30FutureTokenCapsule(newHead));

      //save summary
      summary = summary.toBuilder()
              .setLowerBoundTime(tickDay)
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalValue(Math.addExact(summary.getTotalValue(), amount))
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
      var oldTailKeyBs = summary.getUpperTick();

      //save new tail
      var newTail = Protocol.FutureTokenV2.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .setPrevTick(oldTailKeyBs)
              .build();
      tokenStore.put(tickKey, new Urc30FutureTokenCapsule(newTail));

      //save old tail
      var oldTail = tokenStore.get(oldTailKeyBs.toByteArray());
      oldTail.setNextTick(ByteString.copyFrom(tickKey));
      tokenStore.put(oldTailKeyBs.toByteArray(), oldTail);

      //save summary
      summary = summary.toBuilder()
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalValue(Math.addExact(summary.getTotalValue(), amount))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .setUpperBoundTime(tickDay)
              .build();
      toAcc.setFutureTokenSummary(summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /**
     * lookup slot and insert tick
     */
    var searchKeyBs = summary.getUpperTick();
    while (true){
      var searchTick = tokenStore.get(searchKeyBs.toByteArray());
      if(searchTick.getExpireTime() < tickDay)
      {
        var oldNextTickKey = searchTick.getNextTick();

        //save new tick
        var newTick = Protocol.FutureTokenV2.newBuilder()
                .setExpireTime(tickDay)
                .setFutureBalance(amount)
                .setPrevTick(searchKeyBs)
                .setNextTick(oldNextTickKey)
                .build();
        tokenStore.put(tickKey, new Urc30FutureTokenCapsule(newTick));

        //save prev tick
        searchTick.setNextTick(ByteString.copyFrom(tickKey));
        tokenStore.put(searchKeyBs.toByteArray(), searchTick);

        //save next tick
        var oldNextTick = tokenStore.get(oldNextTickKey.toByteArray());
        oldNextTick.setPrevTick(ByteString.copyFrom(tickKey));

        //save tick summary
        summary = summary.toBuilder()
                .setTotalValue(Math.addExact(summary.getTotalValue(), amount))
                .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
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
