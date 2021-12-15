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

package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.CreateTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import static org.unichain.core.config.Parameter.ChainConstant.*;
import static org.unichain.core.services.http.utils.Util.TOKEN_CREATE_FIELD_END_TIME;
import static org.unichain.core.services.http.utils.Util.TOKEN_CREATE_FIELD_START_TIME;

@Slf4j(topic = "actuator")
public class TokenCreateActuator extends AbstractActuator {

  TokenCreateActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(CreateTokenContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var capsule = new TokenPoolCapsule(ctx);
      if(!ctx.hasField(TOKEN_CREATE_FIELD_START_TIME))
      {
        capsule.setStartTime(dbManager.getHeadBlockTimeStamp());
        logger.info("default startTime to headBlockTimestamp: " + capsule.getStartTime());
      }
      var startTime = capsule.getStartTime();
      if(!ctx.hasField(TOKEN_CREATE_FIELD_END_TIME))
      {
        capsule.setEndTime(startTime + Parameter.ChainConstant.DEFAULT_TOKEN_AGE);
      }

      capsule.setBurnedToken(0L);
      capsule.setTokenName(capsule.getTokenName().toUpperCase());
      capsule.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      capsule.setOriginFeePool(ctx.getFeePool());
      dbManager.getTokenPoolStore().put(capsule.createDbKey(), capsule);

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      accountCapsule.addToken(capsule.createDbKey(), capsule.getTotalSupply());
      accountCapsule.setBalance(accountCapsule.getBalance() - ctx.getFeePool() - fee);
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (Exception e) {
      logger.error("exec TokenCreateActuator got error ->" , e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(CreateTokenContract.class), "contract type error,expected type [CreateTokenContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(CreateTokenContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      var accountCap = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCap, "Account not exists");

      Assert.isTrue(!ctx.getName().isEmpty() && TransactionUtil.validTokenName(ctx.getName().getBytes()), "Invalid token name");
      Assert.isTrue(!ctx.getName().equalsIgnoreCase("UNX"), "Token name can't be UNX");

      var tokenKey = Util.stringAsBytesUppercase(ctx.getName());
      Assert.isTrue(!this.dbManager.getTokenPoolStore().has(tokenKey), "Token exists");

      Assert.isTrue(!StringUtils.isEmpty(ctx.getAbbr().isEmpty()) && TransactionUtil.validTokenName(ctx.getAbbr().getBytes()), "Invalid token abbreviation");
      Assert.isTrue(TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUrl().getBytes()).toByteArray()), "Invalid url");
      Assert.isTrue(TransactionUtil.validAssetDescription(ByteString.copyFrom(ctx.getDescription().getBytes()).toByteArray()), "Invalid description");

      long startTime = ctx.hasField(TOKEN_CREATE_FIELD_START_TIME) ? ctx.getStartTime() : dbManager.getHeadBlockTimeStamp();
      long maxTokenActive = dbManager.getHeadBlockTimeStamp() + MAX_TOKEN_ACTIVE;
      Assert.isTrue((startTime >= dbManager.getHeadBlockTimeStamp()) && (startTime <= maxTokenActive), "Invalid start time: must be greater than current block time and lower than limit timestamp:" +maxTokenActive);

      long endTime = ctx.hasField(TOKEN_CREATE_FIELD_END_TIME) ? ctx.getEndTime() : (startTime + Parameter.ChainConstant.DEFAULT_TOKEN_AGE);
      long maxTokenAge = dbManager.getHeadBlockTimeStamp() + MAX_TOKEN_AGE;
      Assert.isTrue((endTime > 0)
              && (endTime > startTime )
              && (endTime > dbManager.getHeadBlockTimeStamp())
              && (endTime <= maxTokenAge) , "Invalid end time: must greater start time and lower than token age's limit timestamp:" + maxTokenAge);

      Assert.isTrue(ctx.getTotalSupply() > 0 , "TotalSupply must greater than 0");
      Assert.isTrue(ctx.getMaxSupply() > 0 , "MaxSupply must greater than 0!");
      Assert.isTrue(ctx.getMaxSupply() >= ctx.getTotalSupply() , "MaxSupply must greater or equal than TotalSupply");
      Assert.isTrue(ctx.getFee() >= 0 && ctx.getFee() <= TOKEN_MAX_TRANSFER_FEE, "Invalid token transfer fee: must be positive and not exceed max fee : " + TOKEN_MAX_TRANSFER_FEE + " tokens");
      Assert.isTrue(ctx.getExtraFeeRate() >= 0 && ctx.getExtraFeeRate() <= 100 && ctx.getExtraFeeRate() <= TOKEN_MAX_TRANSFER_FEE_RATE, "Invalid extra fee rate , should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");

      Assert.isTrue(ctx.getFeePool() >= 0 && (accountCap.getBalance() >= calcFee() + ctx.getFeePool()), "Invalid fee pool or not enough balance for fee & pre-deposit pool fee");
      Assert.isTrue(ctx.getLot() >= 0, "Invalid lot: must not negative");

      return true;
    }
    catch (Exception e){
      logger.error("validate TokenCreateActuator got error ->", e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(CreateTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 unw default
  }
}
