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
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20FutureTokenSummaryCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.Urc20WithdrawFutureContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;

@Slf4j(topic = "actuator")
public class Urc20WithdrawFutureActuator extends AbstractActuator {
    public Urc20WithdrawFutureActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc20WithdrawFutureContract.class);
      var contractStore = dbManager.getUrc20ContractStore();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      var contractCap = contractStore.get(contractAddr);

      withdraw(ownerAddr, contractAddr, dbManager.getHeadBlockTimeStamp());
      contractCap.setFeePool(Math.subtractExact(contractCap.getFeePool(), fee));
      contractCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      contractStore.put(contractAddr, contractCap);

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
      var fee = calcFee();
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(Urc20WithdrawFutureContract.class), "Contract type error,expected type [Urc20WithdrawFutureContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Urc20WithdrawFutureContract.class);
      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc20ContractStore();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddr)
                      && accountStore.has(ownerAddr)
                      && Wallet.addressValid(contractAddr)
                      && contractStore.has(contractAddr),
              "Unrecognized owner|contract address");

      var contractBase58 = Wallet.encode58Check(contractAddr);
      var contractCap = contractStore.get(contractAddr);
      Assert.isTrue (dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue (dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));
      Assert.isTrue (availableToWithdraw(ownerAddr, contractBase58, dbManager.getHeadBlockTimeStamp()), "Contract unavailable to withdraw");
      Assert.isTrue (contractCap.getFeePool() >= fee, "Not enough contract pool fee balance");
      return true;
    }
    catch (Exception e){
      logger.error("Urc20WithdrawFutureContract failed -->", e);
      throw new ContractValidateException(e.getMessage());
    }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc20WithdrawFutureContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }

    private boolean availableToWithdraw(byte[] ownerAddress, String contractAddrBase58, long headBlockTime) {
      var headBlockTickDay = Util.makeDayTick(headBlockTime);
      var ownerAcc = dbManager.getAccountStore().get(ownerAddress);
      var summary = ownerAcc.getUrc20FutureTokenSummary(contractAddrBase58);
      val summaryCap = new Urc20FutureTokenSummaryCapsule(summary);

      if(summary == null || headBlockTickDay < summary.getLowerBoundTime() || summary.getTotalDeal() <= 0 || summaryCap.getTotalValue().compareTo(BigInteger.ZERO) <= 0)
        return false;
      else
        return true;
    }

    private void withdraw(byte[] ownerAddress, byte[] contractAddr, long headBlockTime){
      var headBlockTickDay = Util.makeDayTick(headBlockTime);
      var contractAddrBase58 = Wallet.encode58Check(contractAddr);
      var futureStore = dbManager.getUrc20FutureTransferStore();
      var accountStore = dbManager.getAccountStore();
      var ownerAcc = accountStore.get(ownerAddress);
      var summary = ownerAcc.getUrc20FutureTokenSummary(contractAddrBase58);
      val summaryCap = new Urc20FutureTokenSummaryCapsule(summary);

      /**
       * loop to withdraw, the most fastest way!!!
       */
      var tmpTickKeyBs = summary.getLowerTick();
      var withdrawAmount = BigInteger.ZERO;
      var withdrawDeal = 0L;
      var withdrawAll = false;
      while (true){
        if(tmpTickKeyBs == null)
        {
          withdrawAll = true;
          break;
        }
        var tmpTickKey = tmpTickKeyBs.toByteArray();
        if(!futureStore.has(tmpTickKey))
        {
          withdrawAll = true;
          break;
        }

        var tmpTick = futureStore.get(tmpTickKey);
        if(tmpTick.getExpireTime() <= headBlockTickDay)
        {
          //withdraw
          withdrawAmount = withdrawAmount.add(tmpTick.getBalance());
          withdrawDeal = Math.incrementExact(withdrawDeal);
          futureStore.delete(tmpTickKeyBs.toByteArray());
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
        ownerAcc.clearUrc20FutureToken(contractAddrBase58);
        ownerAcc.addUrc20Token(contractAddr, withdrawAmount);
        accountStore.put(ownerAddress, ownerAcc);
        return;
      }

      /**
       * some deals remain: update head & summary
       */
      var newHead = futureStore.get(tmpTickKeyBs.toByteArray());
      newHead.clearPrevTick();
      futureStore.put(tmpTickKeyBs.toByteArray(), newHead);
      summary = summary.toBuilder()
              .setTotalDeal(Math.subtractExact(summary.getTotalDeal(), withdrawDeal))
              .setTotalValue(summaryCap.getTotalValue().subtract(withdrawAmount).toString())
              .setLowerTick(tmpTickKeyBs)
              .setLowerBoundTime(newHead.getExpireTime())
              .build();
      ownerAcc.setUrc20FutureTokenSummary(contractAddrBase58, summary);
      ownerAcc.addUrc20Token(contractAddr, withdrawAmount);
      accountStore.put(ownerAddress, ownerAcc);
    }
}
