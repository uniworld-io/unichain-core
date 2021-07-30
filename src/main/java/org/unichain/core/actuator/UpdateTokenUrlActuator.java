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
import org.unichain.core.capsule.CreateTokenCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.UpdateTokenUrlContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class UpdateTokenUrlActuator extends AbstractActuator {

  UpdateTokenUrlActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      var subContract = contract.unpack(Contract.UpdateTokenUrlContract.class);
      logger.info("UpdateTokenUrl  {} ...", subContract);
      var tokenName = subContract.getTokenName().toByteArray();

      var tokenCap = dbManager.getTokenStore().get(tokenName);
      tokenCap.setUrl(subContract.getUrl());
      tokenCap.setDescription(subContract.getDescription());
      dbManager.getTokenStore().put(tokenName, tokenCap);

      chargeFee(subContract.getOwnerAddress().toByteArray(), fee);
      ret.setStatus(fee, code.SUCESS);
      logger.info("UpdateTokenUrl  {} ...DONE!", subContract);
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

      if (!this.contract.is(UpdateTokenUrlContract.class))
        throw new ContractValidateException("contract type error, expected type [UpdateTokenUrlContract],real type[" + contract.getClass() + "]");

      final UpdateTokenUrlContract subContract;
      try {
        subContract = this.contract.unpack(UpdateTokenUrlContract.class);
      } catch (InvalidProtocolBufferException e) {
        logger.debug(e.getMessage(), e);
        throw new ContractValidateException(e.getMessage());
      }

      var ownerAddress = subContract.getOwnerAddress().toByteArray();
      var accountCap = dbManager.getAccountStore().get(ownerAddress);
      if (Objects.isNull(accountCap))
        throw new ContractValidateException("Invalid ownerAddress");

      if(accountCap.getBalance() < calcFee())
          throw new ContractValidateException("Not enough balance");

      var tokenName = subContract.getTokenName().toByteArray();
      var tokenPool = dbManager.getTokenStore().get(tokenName);
      if (Objects.isNull(tokenPool))
        throw new ContractValidateException("TokenName not exist");

      if(!Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()))
          throw new ContractValidateException("only owner of token pool allowed to update url description");

      if (!TransactionUtil.validUrl(subContract.getUrl().toByteArray()))
          throw new ContractValidateException("Invalid url");

      if (!TransactionUtil.validAssetDescription(subContract.getDescription().toByteArray()))
          throw new ContractValidateException("Invalid description");

      return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateTokenUrlContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_UPDATE;
  }
}
