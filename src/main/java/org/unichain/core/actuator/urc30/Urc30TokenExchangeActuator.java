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
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.ExchangeTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.config.Parameter.ChainConstant.URC30_CRITICAL_UPDATE_TIME_GUARD;

@Slf4j(topic = "actuator")
public class Urc30TokenExchangeActuator extends AbstractActuator {

  public Urc30TokenExchangeActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var accountStore = dbManager.getAccountStore();
      var tokenPoolStore = dbManager.getTokenPoolStore();

      var ctx = contract.unpack(ExchangeTokenContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var ownerAccount = accountStore.get(ownerAddress);

      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = tokenPoolStore.get(tokenKey);
      var tokenOwnerAddress = tokenPool.getOwnerAddress().toByteArray();
      var tokenOwnerAcc = accountStore.get(tokenOwnerAddress);

      var exchUnwFactor = tokenPool.getExchUnw();
      var exchTokenFactor = tokenPool.getExchToken();
      Assert.isTrue(exchUnwFactor > 0, "Exchange unw factor must be positive");
      Assert.isTrue(exchTokenFactor > 0, "Exchange token factor must be positive");
      var exchangedToken = Math.floorDiv(Math.multiplyExact(ctx.getAmount(), exchTokenFactor), exchUnwFactor);

      ownerAccount.addToken(tokenKey, exchangedToken);
      ownerAccount.setBalance(Math.subtractExact(ownerAccount.getBalance(), Math.addExact(ctx.getAmount(), fee)));

      tokenOwnerAcc.burnToken(tokenKey, exchangedToken);

      tokenOwnerAcc.setBalance(Math.addExact(tokenOwnerAcc.getBalance() , ctx.getAmount()));

      accountStore.put(ownerAddress, ownerAccount);
      accountStore.put(tokenOwnerAddress, tokenOwnerAcc);
      tokenPool.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      tokenPoolStore.put(tokenKey, tokenPool);
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
      Assert.isTrue(contract.is(ExchangeTokenContract.class), "contract type error,expected type [ExchangeTokenContract],real type[" + contract.getClass() + "]");

      var accountStore = dbManager.getAccountStore();
      var tokenPoolStore = dbManager.getTokenPoolStore();
      val ctx = this.contract.unpack(ExchangeTokenContract.class);
      Assert.isTrue(ctx.getAmount() > 0, "Exchange UNW amount must be positive");
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      var ownerCap = accountStore.get(ownerAddress);
      Assert.notNull(ownerCap, "Owner account not exists");
      Assert.isTrue(ownerCap.getBalance() >= Math.addExact(ctx.getAmount(), calcFee()), "Not enough balance to exchange");

      byte[] tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = tokenPoolStore.get(tokenKey);
      Assert.notNull(tokenPool, "Token pool not exists");
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));

      //prevent critical token update cause this tx to be wrong affected!
      var guardTime = Math.subtractExact(dbManager.getHeadBlockTimeStamp(), tokenPool.getCriticalUpdateTime());
      Assert.isTrue(guardTime >= URC30_CRITICAL_UPDATE_TIME_GUARD, "Critical token update found! Please wait up to 3 minutes before retry.");

      var tokenOwnerCap = accountStore.get(tokenPool.getOwnerAddress().toByteArray());
      Assert.notNull(tokenOwnerCap, "Token owner account not exists");

      Assert.isTrue(!Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()), "Token owner not allowed to exchange token");

      var exchUnwFactor = tokenPool.getExchUnw();
      var exchTokenFactor = tokenPool.getExchToken();
      Assert.isTrue(exchUnwFactor > 0, "Exchange unw factor must be positive");
      Assert.isTrue(exchTokenFactor > 0, "Exchange token factor must be positive");
      var estimatedExchangeToken = Math.floorDiv(Math.multiplyExact(ctx.getAmount(), exchTokenFactor), exchUnwFactor);
      Assert.isTrue(tokenOwnerCap.getTokenAvailable(tokenKey) >= estimatedExchangeToken, "Not enough token liquidity to exchange");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ExchangeTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0L;
  }
}
