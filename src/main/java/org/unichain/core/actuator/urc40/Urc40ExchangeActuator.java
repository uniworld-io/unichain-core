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

package org.unichain.core.actuator.urc40;

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
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc40ExchangeContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.config.Parameter.ChainConstant.URC30_CRITICAL_UPDATE_TIME_GUARD;

@Slf4j(topic = "actuator")
public class Urc40ExchangeActuator extends AbstractActuator {

  public Urc40ExchangeActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc40ContractStore();

      var ctx = contract.unpack(Urc40ExchangeContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccount = accountStore.get(ownerAddr);

      var contractAddr = ctx.getAddress().toByteArray();
      var contractCap = contractStore.get(contractAddr);
      var contractOwnerAddr = contractCap.getOwnerAddress().toByteArray();
      var contractOwnerCap = accountStore.get(contractOwnerAddr);

      var exchUnwFactor = contractCap.getExchUnw();
      var exchTokenFactor = contractCap.getExchToken();
      Assert.isTrue(exchUnwFactor > 0, "Exchange unw factor must be positive");
      Assert.isTrue(exchTokenFactor > 0, "Exchange token factor must be positive");
      var exchangedTokenAmt = Math.floorDiv(Math.multiplyExact(ctx.getAmount(), exchTokenFactor), exchUnwFactor);

      ownerAccount.addUrc40Token(contractAddr, exchangedTokenAmt);
      ownerAccount.setBalance(Math.subtractExact(ownerAccount.getBalance(), Math.addExact(ctx.getAmount(), fee)));
      contractOwnerCap.burnUrc40Token(contractAddr, exchangedTokenAmt);
      contractOwnerCap.setBalance(Math.addExact(contractOwnerCap.getBalance() , ctx.getAmount()));

      accountStore.put(ownerAddr, ownerAccount);
      accountStore.put(contractOwnerAddr, contractOwnerCap);
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
      Assert.isTrue(contract.is(Urc40ExchangeContract.class), "contract type error,expected type [Urc40ExchangeContract],real type[" + contract.getClass() + "]");

      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc40ContractStore();
      val ctx = this.contract.unpack(Urc40ExchangeContract.class);
      Assert.isTrue(ctx.getAmount() > 0, "Exchange UNW amount must be positive");
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      var ownerCap = accountStore.get(ownerAddress);
      Assert.notNull(ownerCap, "Owner account not exists");
      Assert.isTrue(ownerCap.getBalance() >= Math.addExact(ctx.getAmount(), calcFee()), "Not enough balance to exchange");

      var  contractAddr = ctx.getAddress().toByteArray();
      var contractAddrBase58 = Wallet.encode58Check(contractAddr);
      var contractCap = contractStore.get(contractAddr);
      Assert.notNull(contractCap, "Contract not exists");
      Assert.isTrue(contractCap.getEnableExch(), "Contract disabled exchange feature");
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));

      //prevent critical token update cause this tx to be wrong affected!
      var guardTime = Math.subtractExact(dbManager.getHeadBlockTimeStamp(), contractCap.getCriticalUpdateTime());
      Assert.isTrue(guardTime >= URC30_CRITICAL_UPDATE_TIME_GUARD, "Critical Contract update found! Please wait up to 3 minutes before retry.");

      var contractOwnerCap = accountStore.get(contractCap.getOwnerAddress().toByteArray());
      Assert.notNull(contractOwnerCap, "Contract owner account not exists");

      Assert.isTrue(!Arrays.equals(ownerAddress, contractCap.getOwnerAddress().toByteArray()), "Contract owner not allowed to exchange token");

      var exchUnwFactor = contractCap.getExchUnw();
      var exchTokenFactor = contractCap.getExchToken();
      Assert.isTrue(exchUnwFactor > 0, "Exchange unw factor must be positive");
      Assert.isTrue(exchTokenFactor > 0, "Exchange token factor must be positive");
      var estimatedExchangeToken = Math.floorDiv(Math.multiplyExact(ctx.getAmount(), exchTokenFactor), exchUnwFactor);
      Assert.isTrue(contractOwnerCap.getUrc40TokenAvailable(contractAddrBase58.toLowerCase()) >= estimatedExchangeToken, "Not enough token liquidity to exchange");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc40ExchangeContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0L;
  }
}