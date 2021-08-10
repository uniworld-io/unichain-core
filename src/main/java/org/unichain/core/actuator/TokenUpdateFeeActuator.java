/*
 * unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * unichain-core is distributed in the hope that it will be useful,
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
import lombok.var;
import org.unichain.common.utils.Utils;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.UpdateTokenFeeContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE;
import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE_RATE;

@Slf4j(topic = "actuator")
public class TokenUpdateFeeActuator extends AbstractActuator {

  TokenUpdateFeeActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Contract.UpdateTokenFeeContract.class);
      logger.info("UpdateTokenFee  {} ...", ctx);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());

      TokenPoolCapsule tokenCap = dbManager.getTokenPoolStore().get(tokenKey);
      tokenCap.setFee(ctx.getAmount());
      tokenCap.setExtraFeeRate(ctx.getExtraFeeRate());
      dbManager.getTokenPoolStore().put(tokenKey, tokenCap);

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      logger.info("UpdateTokenFee  {} ...DONE!", ctx);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException | ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
      if (Objects.isNull(contract))
          throw new ContractValidateException("No contract!");

      if (Objects.isNull(dbManager))
          throw new ContractValidateException("No dbManager!");

      if (!this.contract.is(UpdateTokenFeeContract.class))
        throw new ContractValidateException("contract type error, expected type [UpdateTokenFeeContract],real type[" + contract.getClass() + "]");

      final UpdateTokenFeeContract ctx;
      try {
        ctx = this.contract.unpack(UpdateTokenFeeContract.class);
      } catch (InvalidProtocolBufferException e) {
        logger.debug(e.getMessage(), e);
        throw new ContractValidateException(e.getMessage());
      }

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountCap = dbManager.getAccountStore().get(ownerAddress);
      if (Objects.isNull(accountCap))
        throw new ContractValidateException("Invalid ownerAddress");

      if(accountCap.getBalance() < calcFee())
          throw new ContractValidateException("Not enough balance");

      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      if (Objects.isNull(tokenPool))
        throw new ContractValidateException("TokenName not exist");

      if(dbManager.getHeadBlockTimeStamp() >= tokenPool.getEndTime())
          throw new ContractValidateException("Token expired at: "+ Utils.formatDateLong(tokenPool.getEndTime()));

      if(dbManager.getHeadBlockTimeStamp() < tokenPool.getStartTime())
          throw new ContractValidateException("Token pending to start at: "+ Utils.formatDateLong(tokenPool.getStartTime()));

      var amount = ctx.getAmount();
      if (amount < 0 || amount > TOKEN_MAX_TRANSFER_FEE)
        throw new ContractValidateException("invalid fee amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE + "]");

      var extraFeeRate = ctx.getExtraFeeRate();
      if (extraFeeRate < 0 || extraFeeRate > 100 || amount > TOKEN_MAX_TRANSFER_FEE_RATE)
          throw new ContractValidateException("invalid extra fee rate amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");

      return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateTokenFeeContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
      return dbManager.getDynamicPropertiesStore().getAssetIssueFee()/2;//250 unw default
  }
}
