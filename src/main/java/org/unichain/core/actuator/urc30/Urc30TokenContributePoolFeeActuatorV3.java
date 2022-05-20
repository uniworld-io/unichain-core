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
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.ContributeTokenPoolFeeContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class Urc30TokenContributePoolFeeActuatorV3 extends AbstractActuator {

    public Urc30TokenContributePoolFeeActuatorV3(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
        var ctx = contract.unpack(ContributeTokenPoolFeeContract.class);
        var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
        var contributeAmount =  ctx.getAmount();
        var tokenCapsule = dbManager.getTokenPoolStore().get(tokenKey);
        tokenCapsule.setFeePool(Math.addExact(tokenCapsule.getFeePool(), contributeAmount));
        dbManager.getTokenPoolStore().put(tokenKey, tokenCapsule);
        var ownerAddress = ctx.getOwnerAddress().toByteArray();
        dbManager.adjustBalance(ownerAddress, -Math.addExact(ctx.getAmount(), fee));
        dbManager.burnFee(fee);
        ret.setStatus(fee, code.SUCESS);
    }
    catch (Exception e) {
        logger.error("Actuator error: {} --> ", e.getMessage(), e);;
        ret.setStatus(fee, code.FAILED);
        throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
      try {
          Assert.notNull(contract, "No contract!");
          Assert.notNull(dbManager, "No dbManager!");
          Assert.isTrue(contract.is(ContributeTokenPoolFeeContract.class), "Contract type error,expected type [ContributeTokenPoolFeeContract],real type[" + contract.getClass() + "]");

          val ctx  = this.contract.unpack(ContributeTokenPoolFeeContract.class);
          var ownerAccount = dbManager.getAccountStore().get(ctx.getOwnerAddress().toByteArray());
          Assert.notNull(ownerAccount, "Invalid ownerAddress: not found");

          var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
          var contributeAmount = ctx.getAmount();
          var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
          tokenPool.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
          Assert.notNull(tokenPool, "TokenName not exist: " + ctx.getTokenName());

          Assert.isTrue(dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));
          Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));
          Assert.isTrue(ownerAccount.getBalance() >= Math.addExact(contributeAmount, calcFee()), "Not enough balance");
          return true;
      }
      catch (Exception e){
          logger.error("Actuator validate error: {} --> ", e.getMessage(), e);;
          throw  new ContractValidateException(e.getMessage());
      }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ContributeTokenPoolFeeContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
      return Parameter.ChainConstant.TRANSFER_FEE;
  }
}
