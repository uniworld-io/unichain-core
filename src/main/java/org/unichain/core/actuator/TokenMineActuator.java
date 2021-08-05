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
import org.unichain.protos.Contract.MineTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class TokenMineActuator extends AbstractActuator {

  TokenMineActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      var ctx = contract.unpack(MineTokenContract.class);
      logger.info("MineTokenContract  {} ...", ctx);

      //update total supply
      var tokenName = ctx.getTokenName().toStringUtf8().toUpperCase().getBytes();
      var tokenCapsule = dbManager.getTokenStore().get(tokenName);
      tokenCapsule.setTotalSupply(tokenCapsule.getTotalSupply() + ctx.getAmount());
      dbManager.getTokenStore().put(tokenName, tokenCapsule);

      //add mined amount to owner account
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      accountCapsule.mineToken(tokenName, ctx.getAmount());
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      logger.info("MineTokenContract  {} ...DONE!", ctx);
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

    if (!this.contract.is(MineTokenContract.class))
      throw new ContractValidateException("contract type error, expected type [MineTokenContract],real type[" + contract.getClass() + "]");

    final MineTokenContract ctx;
    try {
      ctx = this.contract.unpack(MineTokenContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    var ownerAddress = ctx.getOwnerAddress().toByteArray();
    var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
    if(Objects.isNull(ownerAccountCap))
      throw new ContractValidateException("Owner address not exist");

    if (ownerAccountCap.getBalance() < calcFee())
      throw new ContractValidateException("Fee exceed balance");

    var tokenName = ctx.getTokenName().toStringUtf8().toUpperCase().getBytes();
    var tokenPool = dbManager.getTokenStore().get(tokenName);
    if(Objects.isNull(tokenPool))
      throw new ContractValidateException("Token not exist :"+ ctx.getTokenName());

    if(tokenPool.getEndTime() <= dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Token expired at: "+ Utils.formatDateLong(tokenPool.getEndTime()));

    if(tokenPool.getStartTime() < dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Token pending to start at: "+ Utils.formatDateLong(tokenPool.getStartTime()));

    if(!Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()))
      throw new ContractValidateException("Mismatched token owner not allowed to mine");

    // avail to mine = max - total - burned
    var availableToMine = tokenPool.getMaxSupply() - tokenPool.getTotalSupply() - tokenPool.getBurnedToken();
    if(ctx.getAmount() > availableToMine)
      throw new ContractValidateException("not enough frozen token to mine, maximum allowed: " + availableToMine);

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(MineTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_MINE_FEE;
  }
}
