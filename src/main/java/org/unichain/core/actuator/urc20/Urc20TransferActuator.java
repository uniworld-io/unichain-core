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

package org.unichain.core.actuator.urc20;

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
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20FutureTokenCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.Urc20TransferContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Objects;

import static org.unichain.core.config.Parameter.ChainConstant.URC30_CRITICAL_UPDATE_TIME_GUARD;

@Slf4j(topic = "actuator")
public class Urc20TransferActuator extends AbstractActuator {
  public Urc20TransferActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc20TransferContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddr);
      var contractAddr = ctx.getAddress().toByteArray();
      var contract = dbManager.getUrc20ContractStore().get(contractAddr);
      var contractOwnerAddr = contract.getOwnerAddress().toByteArray();
      var toAddr = ctx.getTo().toByteArray();
      var toAccountCap = dbManager.getAccountStore().get(toAddr);

      var isCreateNewAcc = Objects.isNull(toAccountCap);
      if (isCreateNewAcc) {
        var withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
        toAccountCap = new AccountCapsule(ByteString.copyFrom(toAddr), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddr, toAccountCap);
      }

      if(Arrays.equals(ownerAddr, contractOwnerAddr)){
         /*
          owner of token, so:
          - if create new account, charge more fee on owner
          - don't charge token fee
        */
        if(isCreateNewAcc)
        {
          var moreFee = dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
          dbManager.adjustBalanceNoPut(ownerAccountCap, -moreFee);
        }

        ownerAccountCap.burnUrc20Token(contractAddr, ctx.getAmount());
        dbManager.getAccountStore().put(ownerAddr, ownerAccountCap);

        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addUrc20Token(contractAddr, ctx.getAmount());
          dbManager.getAccountStore().put(toAddr, toAccountCap);
        }
        else
          addUrc20Future(toAddr, contractAddr, ctx.getAmount(), ctx.getAvailableTime());
      }
      else {
         /**
          not owner of token, so:
          - if create new account, charge more fee on pool and more token fee on this account
          - charge more token fee on this account
        **/
        var tokenFee = Math.addExact(contract.getFee(), LongMath.divide(Math.multiplyExact(ctx.getAmount(), contract.getExtraFeeRate()), 100, RoundingMode.CEILING));
        if(isCreateNewAcc)
        {
          tokenFee = Math.addExact(tokenFee, contract.getCreateAccountFee());
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
        var contractOwnerCap = dbManager.getAccountStore().get(contractOwnerAddr);
        contractOwnerCap.addUrc20Token(contractAddr, tokenFee);
        dbManager.getAccountStore().put(contractOwnerAddr, contractOwnerCap);

        ownerAccountCap.burnUrc20Token(contractAddr, ctx.getAmount());
        dbManager.getAccountStore().put(ownerAddr, ownerAccountCap);
        var receivedAmt = Math.subtractExact(ctx.getAmount(), tokenFee);
        Assert.isTrue(receivedAmt > 0, "Transfer amount must be greater than fee: " + tokenFee);
        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addUrc20Token(contractAddr, receivedAmt);
          dbManager.getAccountStore().put(toAddr, toAccountCap);
        }
        else
          addUrc20Future(toAddr, contractAddr, receivedAmt, ctx.getAvailableTime());
      }

      //charge pool fee by unw
      contract.setFeePool(Math.subtractExact(contract.getFeePool(), fee));
      contract.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getUrc20ContractStore().put(contractAddr, contract);
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
      Assert.isTrue(contract.is(Urc20TransferContract.class), "Contract type error,expected type [Urc20TransferContract],real type[" + contract.getClass() + "]");

      var fee = calcFee();

      val ctx = this.contract.unpack(Urc20TransferContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddr);
      Assert.notNull(ownerAccountCap, "Owner account not found");

      var contractAddr = ctx.getAddress().toByteArray();
      var contractAddrBase58 = Wallet.encode58Check(contractAddr);
      var contractCap = dbManager.getUrc20ContractStore().get(contractAddr);
      var contractOwnerAddr = contractCap.getOwnerAddress().toByteArray();
      Assert.notNull(contractCap, "Contract not found: " + contractAddrBase58);
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Token expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));

      //prevent critical token update cause this tx to be wrong affected!
      var guardTime = Math.subtractExact(dbManager.getHeadBlockTimeStamp(), contractCap.getCriticalUpdateTime());
      Assert.isTrue(guardTime >= URC30_CRITICAL_UPDATE_TIME_GUARD, "Critical token update found! Please wait up to 3 minutes before retry.");

      if (ctx.getAvailableTime() > 0) {
        Assert.isTrue (ctx.getAvailableTime() > dbManager.getHeadBlockTimeStamp(), "Block time passed available time");
        long maxAvailTime = Math.addExact(dbManager.getHeadBlockTimeStamp(), dbManager.getMaxFutureTransferTimeDurationTokenV3());
        Assert.isTrue (ctx.getAvailableTime() <= maxAvailTime, "Available time limited. Max available timestamp: " + maxAvailTime);
        Assert.isTrue(ctx.getAvailableTime() < contractCap.getEndTime(), "Available time exceeded token expired time");
        Assert.isTrue(ctx.getAmount() >= contractCap.getLot(),"Future transfer require minimum amount of : " + contractCap.getLot());
      }

      var toAddr = ctx.getTo().toByteArray();
      Assert.isTrue(!Arrays.equals(ownerAddr, toAddr), "Transfer to itself not allowed");
      Assert.isTrue(Wallet.addressValid(toAddr), "Invalid toAddress");

      var toAccountCap = dbManager.getAccountStore().get(toAddr);
      var isCreateNewAccount = Objects.isNull(toAccountCap);
      var ownerIsContractOwner = Arrays.equals(ownerAddr, contractOwnerAddr);
      if(ownerIsContractOwner)
      {
        if(isCreateNewAccount)
        {
          var moreFee = dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
          //@fixme why charge unw ?
          Assert.isTrue(ownerAccountCap.getBalance() >= moreFee, "Owner not enough balance to create new account fee, require at least "+ moreFee + "ginza");
        }
      }
      else {
        if(isCreateNewAccount){
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }
      Assert.isTrue(contractCap.getFeePool() >= fee, "Not enough token pool fee balance, require at least " + fee);

      Assert.isTrue (ctx.getAmount() > 0, "Invalid transfer amount, expect positive number");
      Assert.isTrue(ownerAccountCap.getUrc20TokenAvailable(contractAddrBase58) >= ctx.getAmount(), "Not enough token balance");

      //validate transfer amount vs fee
      if(!Arrays.equals(ownerAddr, contractOwnerAddr)){
        var tokenFee = Math.addExact(contractCap.getFee(), LongMath.divide(Math.multiplyExact(ctx.getAmount(), contractCap.getExtraFeeRate()), 100, RoundingMode.CEILING));
        if(isCreateNewAccount)
        {
          tokenFee = Math.addExact(tokenFee, contractCap.getCreateAccountFee());
        }
        Assert.isTrue(ctx.getAmount() > tokenFee, "Not enough token balance to cover transfer fee");
      }
      //after UvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      if (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1
              && toAccountCap != null
              && toAccountCap.getType() == Protocol.AccountType.Contract) {
        throw new ContractValidateException("Cannot transfer token to smartContract.");
      }

      return true;
    }
    catch (Exception e){
      logger.error("TokenTransfer got error -->", e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc20TransferContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }

  private void addUrc20Future(byte[] toAddress, byte[] contractAddr, long amount, long availableTime){
    var addrBase58 = Wallet.encode58Check(contractAddr);
    var tickDay = Util.makeDayTick(availableTime);
    var tickKey = Util.makeUrc20FutureTokenIndexKey(toAddress, addrBase58, tickDay);

    var contractCap = dbManager.getUrc20ContractStore().get(contractAddr);
    var futureStore = dbManager.getUrc20FutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var toAcc = accountStore.get(toAddress);
    var summary = toAcc.getUrc20FutureTokenSummary(addrBase58);

    /*
      tick exist: the fasted way!
     */
    if(futureStore.has(tickKey)){
        //update tick
        var tick = futureStore.get(tickKey);
        tick.addBalance(amount);
        futureStore.put(tickKey, tick);

        //update account summary
        summary = summary.toBuilder().setTotalValue(Math.addExact(summary.getTotalValue(), amount)).build();
        toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
        accountStore.put(toAddress, toAcc);
        return;
    }

    /*
      the first tick ever.
     */
    if(Objects.isNull(summary)){
      //save tick
      var tick = Protocol.Urc20FutureToken.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .clearPrevTick()
              .build();
      futureStore.put(tickKey, new Urc20FutureTokenCapsule(tick));

      //save summary
      summary = Protocol.Urc20FutureTokenSummary.newBuilder()
              .setAddress(ByteString.copyFrom(contractAddr))
              .setSymbol(contractCap.getSymbol())
              .setTotalValue(amount)
              .setTotalDeal(1)
              .setUpperBoundTime(tickDay)
              .setLowerBoundTime(tickDay)
              .setLowerTick(ByteString.copyFrom(tickKey))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /*
      other tick exist
     */
    var headKey = summary.getLowerTick().toByteArray();
    var head = futureStore.get(headKey);
    var headTime = head.getExpireTime();
    /*
      if new tick is head
     */
    if(tickDay < headTime){
      //save old head pointer
      head.setPrevTick(ByteString.copyFrom(tickKey));
      futureStore.put(headKey, head);

      //save new head
      var newHead = Protocol.Urc20FutureToken.newBuilder()
              .setExpireTime(tickDay)
              .setFutureBalance(amount)
              .setNextTick(summary.getLowerTick())
              .clearPrevTick()
              .build();
      futureStore.put(tickKey, new Urc20FutureTokenCapsule(newHead));

      //save summary
      summary = summary.toBuilder()
              .setLowerBoundTime(tickDay)
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalValue(Math.addExact(summary.getTotalValue(), amount))
              .setLowerTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
      accountStore.put(toAddress, toAcc);
      return ;
    }

    /*
      if new tick is tail
     */
    if(tickDay > headTime){
      var oldTailKeyBs = summary.getUpperTick();

      //save new tail
      var newTail = Protocol.Urc20FutureToken.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .setPrevTick(oldTailKeyBs)
              .build();
      futureStore.put(tickKey, new Urc20FutureTokenCapsule(newTail));

      //save old tail
      var oldTail = futureStore.get(oldTailKeyBs.toByteArray());
      oldTail.setNextTick(ByteString.copyFrom(tickKey));
      futureStore.put(oldTailKeyBs.toByteArray(), oldTail);

      //save summary
      summary = summary.toBuilder()
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalValue(Math.addExact(summary.getTotalValue(), amount))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .setUpperBoundTime(tickDay)
              .build();
      toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /*
      lookup slot and insert tick
     */
    var searchKeyBs = summary.getUpperTick();
    while (true){
      var searchTick = futureStore.get(searchKeyBs.toByteArray());
      if(searchTick.getExpireTime() < tickDay)
      {
        var oldNextTickKey = searchTick.getNextTick();

        //save new tick
        var newTick = Protocol.Urc20FutureToken.newBuilder()
                .setExpireTime(tickDay)
                .setFutureBalance(amount)
                .setPrevTick(searchKeyBs)
                .setNextTick(oldNextTickKey)
                .build();
        futureStore.put(tickKey, new Urc20FutureTokenCapsule(newTick));

        //save prev tick
        searchTick.setNextTick(ByteString.copyFrom(tickKey));
        futureStore.put(searchKeyBs.toByteArray(), searchTick);

        //save next tick
        var oldNextTick = futureStore.get(oldNextTickKey.toByteArray());
        oldNextTick.setPrevTick(ByteString.copyFrom(tickKey));
        futureStore.put(oldNextTickKey.toByteArray(), oldNextTick);

        //save tick summary
        summary = summary.toBuilder()
                .setTotalValue(Math.addExact(summary.getTotalValue() , amount))
                .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
                .build();

        toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
        accountStore.put(toAddress, toAcc);
        return;
      }
      else {
        searchKeyBs = searchTick.getPrevTick();
      }
    }
  }
}
