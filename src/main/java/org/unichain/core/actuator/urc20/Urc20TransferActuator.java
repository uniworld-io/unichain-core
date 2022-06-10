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
import org.unichain.common.utils.ActuatorUtil;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
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
      var accountStore = dbManager.getAccountStore();
      var urc20Store = dbManager.getUrc20ContractStore();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = accountStore.get(ownerAddr);
      var urc20Addr = ctx.getAddress().toByteArray();
      var urc20Cap = urc20Store.get(urc20Addr);
      var urc20OwnerAddr = urc20Cap.getOwnerAddress().toByteArray();
      var toAddr = ctx.getTo().toByteArray();
      var toAccountCap = accountStore.get(toAddr);

      var isCreateNewAcc = Objects.isNull(toAccountCap);
      if (isCreateNewAcc) {
        toAccountCap = createDefaultAccount(toAddr);
      }

      if(Arrays.equals(ownerAddr, urc20OwnerAddr)){
        if(isCreateNewAcc)
        {
          var moreFee = dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
          dbManager.adjustBalanceNoPut(ownerAccountCap, -moreFee);
        }

        ownerAccountCap.burnUrc20Token(urc20Addr, ctx.getAmount());
        accountStore.put(ownerAddr, ownerAccountCap);

        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addUrc20Token(urc20Addr, ctx.getAmount());
          accountStore.put(toAddr, toAccountCap);
        }
        else
        {
          ActuatorUtil.addUrc20Future(dbManager, toAddr, urc20Addr, ctx.getAmount(), ctx.getAvailableTime());
        }
      }
      else {
        var tokenFee = Math.addExact(urc20Cap.getFee(), LongMath.divide(Math.multiplyExact(ctx.getAmount(), urc20Cap.getExtraFeeRate()), 100, RoundingMode.CEILING));
        if(isCreateNewAcc)
        {
          tokenFee = Math.addExact(tokenFee, urc20Cap.getCreateAccountFee());
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
        var urc20OwnerCap = accountStore.get(urc20OwnerAddr);
        urc20OwnerCap.addUrc20Token(urc20Addr, tokenFee);
        accountStore.put(urc20OwnerAddr, urc20OwnerCap);

        ownerAccountCap.burnUrc20Token(urc20Addr, ctx.getAmount());
        accountStore.put(ownerAddr, ownerAccountCap);
        var receivedAmt = Math.subtractExact(ctx.getAmount(), tokenFee);
        Assert.isTrue(receivedAmt > 0, "Transfer amount must be greater than fee: " + tokenFee);
        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addUrc20Token(urc20Addr, receivedAmt);
          accountStore.put(toAddr, toAccountCap);
        }
        else
          ActuatorUtil.addUrc20Future(dbManager, toAddr, urc20Addr, receivedAmt, ctx.getAvailableTime());
      }

      //charge pool fee by unw
      urc20Cap.setFeePool(Math.subtractExact(urc20Cap.getFeePool(), fee));
      urc20Cap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      urc20Store.put(urc20Addr, urc20Cap);
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
      var createNewAccount = Objects.isNull(toAccountCap);
      var ownerIsContractOwner = Arrays.equals(ownerAddr, contractOwnerAddr);
      if(ownerIsContractOwner)
      {
        if(createNewAccount)
        {
          var moreFee = dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
          Assert.isTrue(ownerAccountCap.getBalance() >= moreFee, "Owner not enough balance to create new account fee, expected gas: "+ moreFee);
        }
      }
      else {
        if(createNewAccount){
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }
      Assert.isTrue(contractCap.getFeePool() >= fee, "Not enough token pool fee balance, require at least " + fee);

      Assert.isTrue (ctx.getAmount() > 0, "Invalid transfer amount, expect positive number");
      Assert.isTrue(ownerAccountCap.getUrc20TokenAvailable(contractAddrBase58) >= ctx.getAmount(), "Not enough token balance");

      //validate transfer amount vs fee
      if(!Arrays.equals(ownerAddr, contractOwnerAddr)){
        var tokenFee = Math.addExact(contractCap.getFee(), LongMath.divide(Math.multiplyExact(ctx.getAmount(), contractCap.getExtraFeeRate()), 100, RoundingMode.CEILING));
        if(createNewAccount)
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

}
