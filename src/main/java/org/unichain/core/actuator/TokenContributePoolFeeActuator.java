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
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
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
      var subContract = contract.unpack(ContributeTokenPoolFeeContract.class);
      logger.info("ContributeTokenPoolFee  {} ...", subContract);
      var ownerAddress = subContract.getOwnerAddress().toByteArray();
      var tokenName = subContract.getTokenName().toByteArray();
      var tokenCapsule = dbManager.getTokenStore().get(tokenName);
      tokenCapsule.setFeePool(tokenCapsule.getFeePool() + subContract.getAmount());
      dbManager.getTokenStore().put(tokenName, tokenCapsule);

      chargeFee(ownerAddress, fee);
      dbManager.adjustBalance(ownerAddress, subContract.getAmount());
      ret.setStatus(fee, code.SUCESS);
      logger.info("ContributeTokenPoolFee  {} ...DONE!", subContract);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    } catch (BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
      if (Objects.isNull(contract))
        throw new ContractValidateException("No contract!");

      if (Objects.isNull(dbManager))
        throw new ContractValidateException("No dbManager!");

      if (!this.contract.is(ContributeTokenPoolFeeContract.class))
        throw new ContractValidateException("contract type error, expected type [ContributeTokenPoolFeeContract],real type[" + contract.getClass() + "]");

      final ContributeTokenPoolFeeContract subContract;
      try {
        subContract = this.contract.unpack(ContributeTokenPoolFeeContract.class);
      } catch (InvalidProtocolBufferException e) {
        logger.debug(e.getMessage(), e);
        throw new ContractValidateException(e.getMessage());
      }

      var accountCap = dbManager.getAccountStore().get(subContract.getOwnerAddress().toByteArray());
      if (Objects.isNull(accountCap))
        throw new ContractValidateException("Invalid ownerAddress");

      var tokenName = subContract.getTokenName().toByteArray();
      var tokenPool = dbManager.getTokenStore().get(tokenName);
      if (Objects.isNull(tokenPool))
        throw new ContractValidateException("TokenName not exist");

      if(tokenPool.getEndTime() <= dbManager.getHeadBlockTimeStamp())
          throw new ContractValidateException("Token expired at: "+ Utils.formatDateLong(tokenPool.getEndTime()));

      if(tokenPool.getStartTime() < dbManager.getHeadBlockTimeStamp())
          throw new ContractValidateException("Token pending to start at: "+ Utils.formatDateLong(tokenPool.getStartTime()));

      var contributeAmount = subContract.getAmount();
      if (accountCap.getBalance() < contributeAmount + calcFee())
        throw new ContractValidateException("Not enough balance");

      return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ContributeTokenPoolFeeContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_UPDATE;
  }
}
