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

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.WithdrawFutureTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class Urc30TokenWithdrawFutureActuatorV3 extends AbstractActuator {

    public Urc30TokenWithdrawFutureActuatorV3(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(WithdrawFutureTokenContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);

      withdraw(ownerAddress, tokenKey, dbManager.getHeadBlockTimeStamp());

      tokenPool.setFeePool(Math.subtractExact(tokenPool.getFeePool(), fee));
      tokenPool.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getTokenPoolStore().put(tokenKey, tokenPool);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | ArithmeticException | BalanceInsufficientException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      var fee = calcFee();
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(WithdrawFutureTokenContract.class), "Contract type error,expected type [Contract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(WithdrawFutureTokenContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull (ownerAccountCap, "Owner account not found");

      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      Assert.notNull (tokenPool, "Token not found: " + ctx.getTokenName());

      Assert.isTrue (dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));
      Assert.isTrue (dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));
      Assert.isTrue (availableToWithdraw(ownerAddress, tokenKey, dbManager.getHeadBlockTimeStamp()), "Token unavailable to withdraw");
      Assert.isTrue (tokenPool.getFeePool() >= fee, "Not enough token pool fee balance");
      return true;
    }
    catch (Exception e){
      logger.error("TokenWithdrawFuture failed -->", e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(WithdrawFutureTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }

  private boolean availableToWithdraw(byte[] ownerAddress, byte[] tokenKey, long headBlockTime) {
      var headBlockTickDay = Util.makeDayTick(headBlockTime);
      var ownerAcc = dbManager.getAccountStore().get(ownerAddress);
      var summary = ownerAcc.getFutureTokenSummary(new String(tokenKey));
      if(summary == null || headBlockTickDay < summary.getLowerBoundTime() || summary.getTotalDeal() <= 0 || summary.getTotalValue() <= 0)
        return false;
      else
        return true;
  }

  private void withdraw(byte[] ownerAddress, byte[] tokenKey, long headBlockTime){
      var headBlockTickDay = Util.makeDayTick(headBlockTime);
      var tokenName = new String(tokenKey);
      var tokenStore = dbManager.getFutureTokenStore();
      var accountStore = dbManager.getAccountStore();
      var ownerAcc = accountStore.get(ownerAddress);
      var summary = ownerAcc.getFutureTokenSummary(tokenName);

      /**
       * loop to withdraw, the most fastest way!!!
       */
      var tmpTickKeyBs = summary.getLowerTick();
      var withdrawAmount = 0;
      var withdrawDeal = 0;
      var withdrawAll = false;
      while (true){
        if(tmpTickKeyBs == null)
        {
          withdrawAll = true;
          break;
        }
        var tmpTickKey = tmpTickKeyBs.toByteArray();
        if(!tokenStore.has(tmpTickKey))
        {
          withdrawAll = true;
          break;
        }

        var tmpTick = tokenStore.get(tmpTickKey);
        if(tmpTick.getExpireTime() <= headBlockTickDay)
        {
          //withdraw
          withdrawAmount += tmpTick.getBalance();
          withdrawDeal ++;
          tokenStore.delete(tmpTickKeyBs.toByteArray());
          tmpTickKeyBs = tmpTick.getNextTick();
          continue;
        }
        else
        {
          break;
        }
      }

      /**
       * all deals withdrawn: remove summary
       */
      if(withdrawAll){
        ownerAcc.clearFutureToken(tokenKey);
        ownerAcc.addToken(tokenKey, withdrawAmount);
        accountStore.put(ownerAddress, ownerAcc);
        return;
      }

      /**
       * some deals remain: update head & summary
       */
      var newHead = tokenStore.get(tmpTickKeyBs.toByteArray());
      newHead.clearPrevTick();
      tokenStore.put(tmpTickKeyBs.toByteArray(), newHead);
      summary = summary.toBuilder()
              .setTotalDeal(summary.getTotalDeal() - withdrawDeal)
              .setTotalValue(summary.getTotalValue() - withdrawAmount)
              .setLowerTick(tmpTickKeyBs)
              .setLowerBoundTime(newHead.getExpireTime())
              .build();
      ownerAcc.setFutureTokenSummary(summary);
      ownerAcc.addToken(tokenKey, withdrawAmount);
      accountStore.put(ownerAddress, ownerAcc);
  }
}
