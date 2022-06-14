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
import org.unichain.common.utils.ActuatorUtil;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20TransferFromContractCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.Urc20TransferFromContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

import static org.unichain.core.config.Parameter.ChainConstant.URC30_CRITICAL_UPDATE_TIME_GUARD;

@Slf4j(topic = "actuator")
public class Urc20TransferFromActuator extends AbstractActuator {
  public Urc20TransferFromActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc20TransferFromContract.class);
      var ctxCap = new Urc20TransferFromContractCapsule(ctx);
      var accStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc20ContractStore();

      var spender = ctx.getOwnerAddress().toByteArray();
      var from = ctx.getFrom().toByteArray();
      var to = ctx.getTo().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();

      var fromCap = accStore.get(from);
      var toAccountCap = accStore.get(to);
      var contractCap = contractStore.get(contractAddr);
      var contractOwnerAddr = contractCap.getOwnerAddress().toByteArray();

      var createToAcc = Objects.isNull(toAccountCap);
      if (createToAcc) {
        toAccountCap = dbManager.createDefaultAccount(to, Protocol.AccountType.Normal);
      }

      if(Arrays.equals(from, contractOwnerAddr)){
         /**
          owner of token, so:
          - if create new account, charge more fee on owner
          - don't charge token fee
        **/
        if(createToAcc)
        {
          dbManager.adjustBalanceNoPut(fromCap, -dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }

        fromCap.burnUrc20Token(contractAddr, ctxCap.getAmount());
        accStore.put(from, fromCap);

        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addUrc20Token(contractAddr, ctxCap.getAmount());
          accStore.put(to, toAccountCap);
        }
        else
        {
          ActuatorUtil.addUrc20Future(dbManager, to, contractAddr, ctxCap.getAmount(), ctx.getAvailableTime());
        }
      }
      else {
         /**
          not owner of token, so:
          - if create new account, charge more fee on pool and more token fee on this account
          - charge more token fee on this account
        **/
        var tokenFee = BigInteger.valueOf(contractCap.getFee())
                .add(Utils.divideCeiling(ctxCap.getAmount()
                        .multiply(BigInteger.valueOf(contractCap.getExtraFeeRate())), BigInteger.valueOf(100L)));
        if(createToAcc)
        {
          tokenFee = tokenFee.add(BigInteger.valueOf(contractCap.getCreateAccountFee()));
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
        var contractOwnerCap = accStore.get(contractOwnerAddr);
        contractOwnerCap.addUrc20Token(contractAddr, tokenFee);
        accStore.put(contractOwnerAddr, contractOwnerCap);

        fromCap.burnUrc20Token(contractAddr, ctxCap.getAmount());
        accStore.put(from, fromCap);
        var receivedAmt = ctxCap.getAmount().subtract(tokenFee);
        Assert.isTrue(receivedAmt.compareTo(BigInteger.ZERO) > 0, "Transfer amount must be greater than fee: " + tokenFee);
        if(ctx.getAvailableTime() <= 0)
        {
          toAccountCap.addUrc20Token(contractAddr, receivedAmt);
          accStore.put(to, toAccountCap);
        }
        else
          ActuatorUtil.addUrc20Future(dbManager, to, contractAddr, receivedAmt, ctx.getAvailableTime());
      }

      //update spender
      dbManager.getUrc20SpenderStore().spend(spender, contractAddr, from, ctxCap.getAmount());

      //charge pool fee
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
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(Urc20TransferFromContract.class), "Contract type error,expected type [Urc20TransferFromContract], real type[" + contract.getClass() + "]");

      var fee = calcFee();
      val ctx = this.contract.unpack(Urc20TransferFromContract.class);
      var ctxCap = new Urc20TransferFromContractCapsule(ctx);
      var accStore = dbManager.getAccountStore();
      var spenderStore = dbManager.getUrc20SpenderStore();
      var contractStore = dbManager.getUrc20ContractStore();

      var spenderAddr = ctx.getOwnerAddress().toByteArray();
      var fromAddr = ctx.getFrom().toByteArray();
      var urc20Addr = ctx.getAddress().toByteArray();
      var toAddr = ctx.getTo().toByteArray();

      Assert.isTrue(Wallet.addressValid(spenderAddr) && accStore.has(spenderAddr), "Unrecognized spender address!");
      Assert.isTrue(Wallet.addressValid(fromAddr) && accStore.has(fromAddr), "Unrecognized fromAddr address!");
      Assert.isTrue(Wallet.addressValid(urc20Addr) && contractStore.has(urc20Addr), "Unrecognized contract address!");
      Assert.isTrue(Wallet.addressValid(toAddr)
                      && !Arrays.equals(dbManager.getBurnAddress(), toAddr)
                      && (!accStore.has(toAddr) || (accStore.get(toAddr).getType() != Protocol.AccountType.Contract)),
              "Unrecognized to address or burn address or contract address");

      var urc20AddrBase58 = Wallet.encode58Check(urc20Addr);

      Assert.isTrue(!Arrays.equals(spenderAddr, toAddr) && !Arrays.equals(fromAddr, toAddr), "Transfer to itself not allowed");

      //check spender
      spenderStore.checkSpend(spenderAddr, urc20Addr, fromAddr, ctxCap.getAmount());

      //check contract active
      var contractCap = contractStore.get(urc20Addr);
      var contractOwnerAddr = contractCap.getOwnerAddress().toByteArray();
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));

      //prevent critical token update cause this tx to be wrong affected!
      var guardTime = Math.subtractExact(dbManager.getHeadBlockTimeStamp(), contractCap.getCriticalUpdateTime());
      Assert.isTrue(guardTime >= URC30_CRITICAL_UPDATE_TIME_GUARD, "Critical token update found! Please wait up to 3 minutes before retry.");

      if (ctx.getAvailableTime() > 0) {
        Assert.isTrue (ctx.getAvailableTime() > dbManager.getHeadBlockTimeStamp(), "Block time passed available time");
        long maxAvailTime = Math.addExact(dbManager.getHeadBlockTimeStamp(), dbManager.getMaxFutureTransferTimeDurationTokenV3());
        Assert.isTrue (ctx.getAvailableTime() <= maxAvailTime, "Available time limited. Max available timestamp: " + maxAvailTime);
        Assert.isTrue(ctx.getAvailableTime() < contractCap.getEndTime(), "Available time exceeded token expired time");
        Assert.isTrue(ctxCap.getAmount().compareTo(BigInteger.valueOf(contractCap.getLot())) >= 0,"Future transfer require minimum amount of : " + contractCap.getLot());
      }

      //check fee
      var toAccountCap = accStore.get(toAddr);
      var fromAccCap = accStore.get(fromAddr);
      var createToAccount = Objects.isNull(toAccountCap);
      var ownerIsContractOwner = Arrays.equals(fromAddr, contractOwnerAddr);
      if(ownerIsContractOwner)
      {
        if(createToAccount)
        {
          var moreFee = dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
          Assert.isTrue(fromAccCap.getBalance() >= moreFee, "Owner not enough balance to create new account fee, require at least "+ moreFee + "ginza");
        }
      }
      else {
        if(createToAccount){
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }

      Assert.isTrue(contractCap.getFeePool() >= fee, "Not enough token pool fee balance, require at least " + fee);
      Assert.isTrue (ctxCap.getAmount().compareTo(BigInteger.ZERO) > 0, "Invalid transfer amount, expect positive number");
      Assert.isTrue(fromAccCap.getUrc20TokenAvailable(urc20AddrBase58).compareTo(ctxCap.getAmount()) >= 0, "Not enough token balance");

      //validate transfer amount vs fee
      if(!Arrays.equals(fromAddr, contractOwnerAddr)){
        var tokenFee = BigInteger.valueOf(contractCap.getFee())
                .add(Utils.divideCeiling(ctxCap.getAmount()
                        .multiply(BigInteger.valueOf(contractCap.getExtraFeeRate())), BigInteger.valueOf(100L)));
        if(createToAccount)
        {
          tokenFee = tokenFee.add(BigInteger.valueOf(contractCap.getCreateAccountFee()));
        }
        Assert.isTrue(ctxCap.getAmount().compareTo(tokenFee) > 0, "Not enough token balance to cover transfer fee");
      }

      return true;
    }
    catch (Exception e){
      logger.error("Urc20TransferFrom got error -->", e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Contract.Urc20TransferFromContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }

}
