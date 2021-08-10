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
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.ContributeTokenPoolFeeContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

@Slf4j(topic = "actuator")
public class TokenContributePoolFeeActuator extends AbstractActuator {

  TokenContributePoolFeeActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
        var ctx = contract.unpack(ContributeTokenPoolFeeContract.class);
        var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
        var contributeAmount =  ctx.getAmount();
        var tokenCapsule = dbManager.getTokenPoolStore().get(tokenKey);
        tokenCapsule.setFeePool(tokenCapsule.getFeePool() + contributeAmount);
        dbManager.getTokenPoolStore().put(tokenKey, tokenCapsule);

        var ownerAddress = ctx.getOwnerAddress().toByteArray();
        dbManager.adjustBalance(ownerAddress, -(ctx.getAmount() + fee));
        dbManager.burnFee(fee);
        ret.setStatus(fee, code.SUCESS);
        logger.info("ContributeTokenPoolFee  {} ...DONE!", ctx);
    } catch (InvalidProtocolBufferException | BalanceInsufficientException | ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
      logger.info("validate ContributeTokenPoolFee ...");
      if (Objects.isNull(contract))
        throw new ContractValidateException("No contract!");

      if (Objects.isNull(dbManager))
        throw new ContractValidateException("No dbManager!");

      if (!this.contract.is(ContributeTokenPoolFeeContract.class))
        throw new ContractValidateException("contract type error, expected type [ContributeTokenPoolFeeContract], real type[" + contract.getClass() + "]");

      final ContributeTokenPoolFeeContract ctx;
      try {
        ctx = this.contract.unpack(ContributeTokenPoolFeeContract.class);
      } catch (InvalidProtocolBufferException e) {
        logger.error(e.getMessage(), e);
        throw new ContractValidateException(e.getMessage());
      }

      var ownerAccount = dbManager.getAccountStore().get(ctx.getOwnerAddress().toByteArray());
      if (Objects.isNull(ownerAccount))
        throw new ContractValidateException("Invalid ownerAddress");

      var tokenKey =  Util.stringAsBytesUppercase(ctx.getTokenName());
      var contributeAmount = ctx.getAmount();
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      if (Objects.isNull(tokenPool))
      {
          logger.warn("validate ContributeTokenPoolFee 4: token name not exist");
          throw new ContractValidateException("TokenName not exist: " + ctx.getTokenName());
      }

      if(dbManager.getHeadBlockTimeStamp() >= tokenPool.getEndTime())
          throw new ContractValidateException("Token expired at: "+ Utils.formatDateLong(tokenPool.getEndTime()));

      if(dbManager.getHeadBlockTimeStamp() < tokenPool.getStartTime())
          throw new ContractValidateException("Token pending to start at: "+ Utils.formatDateLong(tokenPool.getStartTime()));

      if (ownerAccount.getBalance() < contributeAmount + calcFee())
          throw new ContractValidateException("Not enough balance");

      logger.info("validate ContributeTokenPoolFee ...DONE!");

      return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ContributeTokenPoolFeeContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
      return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 unw default
  }
}
