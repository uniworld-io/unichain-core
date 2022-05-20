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
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.BurnTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc30TokenBurnActuatorV3 extends AbstractActuator {

  public Urc30TokenBurnActuatorV3(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(BurnTokenContract.class);
      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenCap = dbManager.getTokenPoolStore().get(tokenKey);
      tokenCap.burnToken(ctx.getAmount());
      tokenCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      tokenCap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getTokenPoolStore().put(tokenKey, tokenCap);

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountCap = dbManager.getAccountStore().get(ownerAddress);
      accountCap.burnToken(tokenKey, ctx.getAmount());
      dbManager.getAccountStore().put(ownerAddress, accountCap);

      chargeFee(ownerAddress, fee);
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
      Assert.isTrue(contract.is(BurnTokenContract.class), "Contract type error,expected type [BurnTokenContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(BurnTokenContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);

      Assert.notNull(ownerAccountCap, "Owner address not exist");
      Assert.isTrue(ownerAccountCap.getBalance() >= calcFee());

      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      Assert.notNull(tokenPool, "Token not exist :" + ctx.getTokenName());

      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: "+ Utils.formatDateLong(tokenPool.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));
      Assert.isTrue(Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()), "Mismatched token owner not allowed to mine");
      Assert.isTrue(ownerAccountCap.getTokenAvailable(tokenKey) >= ctx.getAmount(), "Not enough token balance of" + ctx.getTokenName() + "at least " + ctx.getAmount());

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(BurnTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2UNW default
  }
}
