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
import org.joda.time.LocalDateTime;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.WithdrawFutureTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

/**
 * @todo:
 * what happen if some one constantly make withdraw tx that not in account ?
 * - pool spam because fee is on pool
 */
@Slf4j(topic = "actuator")
public class WithdrawFutureTokenActuator extends AbstractActuator {

  WithdrawFutureTokenActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      WithdrawFutureTokenContract subContract = contract.unpack(WithdrawFutureTokenContract.class);
      var ownerAddress = subContract.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
      var tokenName = subContract.getTokenName().toByteArray();
      var tokenPool = dbManager.getTokenStore().get(tokenName);

      //withdraw future
      if(!ownerAccountCap.withdrawTokenFuture(tokenName, dbManager.getHeadBlockTimeStamp()))
        throw new ContractExeException("failed to withdraw token from account");
      dbManager.getAccountStore().put(ownerAddress, ownerAccountCap);

      //if success, charge pool fee
      tokenPool.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      tokenPool.setFeePool(tokenPool.getFeePool() - fee);
      dbManager.getTokenStore().put(tokenName, tokenPool);

      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(WithdrawFutureTokenContract.class)) {
      throw new ContractValidateException("contract type error, expected type [WithdrawFutureTokenContract],real type[" + contract.getClass() + "]");
    }

    long fee = calcFee();

    final WithdrawFutureTokenContract subContract;
    try {
      subContract = this.contract.unpack(WithdrawFutureTokenContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    var ownerAddress = subContract.getOwnerAddress().toByteArray();
    var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
    if(Objects.isNull(ownerAccountCap))
      throw new ContractValidateException("Owner account not found");

    //detect available token to withdraw
    var tokenName = subContract.getTokenName().toByteArray();
    var tokenPool = dbManager.getTokenStore().get(tokenName);
    if(Objects.isNull(tokenPool))
      throw new ContractValidateException("Token pool not found: " + subContract.getTokenName());

    if(tokenPool.getEndTime() <= dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Token expired at: "+ (new LocalDateTime(tokenPool.getEndTime())));

    if(tokenPool.getStartTime() < dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Token pending to start at: "+ (new LocalDateTime(tokenPool.getStartTime())));

    long available = ownerAccountCap.getTokenFutureAvailable(tokenName, dbManager.getHeadBlockTimeStamp());
    if(available <= 0)
      throw new ContractValidateException("Not found future token or unavailable to withdraw");

    if(tokenPool.getFeePool() < fee)
      throw new ContractValidateException("not enough token pool fee balance");

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(WithdrawFutureTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_FUTURE_WITHDRAW_FEE;
  }
}
