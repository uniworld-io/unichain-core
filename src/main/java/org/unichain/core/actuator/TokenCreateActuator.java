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
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.CreateTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE;
import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE_RATE;

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
      capsule.setBurnedToken(0L);
      if(capsule.getStartTime() == 0)
        capsule.setStartTime(dbManager.getHeadBlockTimeStamp());
      capsule.setTokenName(capsule.getTokenName().toUpperCase());
      capsule.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getTokenPoolStore().put(capsule.createDbKey(), capsule);

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      accountCapsule.addToken(capsule.createDbKey(), capsule.getTotalSupply());
      accountCapsule.setBalance(accountCapsule.getBalance() - ctx.getFeePool() - fee);
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);
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

    if (!this.contract.is(CreateTokenContract.class)) {
      throw new ContractValidateException("contract type error, expected type [CreateTokenContract],real type[" + contract.getClass() + "]");
    }

    final CreateTokenContract ctx;
    try {
      ctx = this.contract.unpack(CreateTokenContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    var ownerAddress = ctx.getOwnerAddress().toByteArray();
    if (!Wallet.addressValid(ownerAddress))
      throw new ContractValidateException("Invalid ownerAddress");

    if (ctx.getName().isEmpty() || !TransactionUtil.validTokenName(ctx.getName().getBytes())) {
      throw new ContractValidateException("Invalid token name");
    }

    if (ctx.getName().equalsIgnoreCase("UNX"))
      throw new ContractValidateException("Token name can't be UNX");

    var tokenKey = Util.stringAsBytesUppercase(ctx.getName());

    if (this.dbManager.getTokenPoolStore().get(tokenKey) != null) {
      throw new ContractValidateException("Token exists");
    }

    if ((!ctx.getAbbr().isEmpty()) && !TransactionUtil.validTokenName(ctx.getAbbr().getBytes())) {
      throw new ContractValidateException("Invalid token abbreviation");
    }

    if (!TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUrl().getBytes()).toByteArray()))
      throw new ContractValidateException("Invalid url");

    if (!TransactionUtil.validAssetDescription(ByteString.copyFrom(ctx.getDescription().getBytes()).toByteArray()))
      throw new ContractValidateException("Invalid description");

    if (!(ctx.getStartTime() == 0 || ctx.getStartTime() >= dbManager.getHeadBlockTimeStamp()))
      throw new ContractValidateException("Invalid start time");

    if (ctx.getEndTime() <= 0 ||  ctx.getEndTime() <= ctx.getStartTime() || ctx.getEndTime() <= dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Invalid end time");

    if (ctx.getTotalSupply() <= 0)
      throw new ContractValidateException("TotalSupply must greater than 0!");

    if (ctx.getMaxSupply() <= 0)
      throw new ContractValidateException("MaxSupply must greater than 0!");

    if (ctx.getMaxSupply() < ctx.getTotalSupply())
      throw new ContractValidateException("MaxSupply must greater or equal than TotalSupply!");

    if (ctx.getFee() < 0 || ctx.getFee() > TOKEN_MAX_TRANSFER_FEE)
      throw new ContractValidateException("Invalid token transfer fee: must be positive and not exceed max fee : " + TOKEN_MAX_TRANSFER_FEE + " tokens");

    if (ctx.getExtraFeeRate() < 0 || ctx.getExtraFeeRate() > 100 || ctx.getExtraFeeRate() > TOKEN_MAX_TRANSFER_FEE_RATE)
      throw new ContractValidateException("Invalid extra fee rate , should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");

    var accountCap = dbManager.getAccountStore().get(ownerAddress);
    if (Objects.isNull(accountCap))
      throw new ContractValidateException("Account not exists");

    if (ctx.getFeePool() < 0 || (accountCap.getBalance() < calcFee() + ctx.getFeePool()))
      throw new ContractValidateException("Invalid fee pool or not enough balance for fee & pre-deposit pool fee");

    return true;
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
