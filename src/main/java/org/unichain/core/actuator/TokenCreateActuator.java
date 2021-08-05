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
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.CreateTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

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

      //make sure init burned amount
      capsule.setBurnedToken(0L);
      //set token name as uppercase
      var tokenName = ByteString.copyFrom(capsule.getTokenName().toStringUtf8().toUpperCase().getBytes());
      capsule.setTokenName(tokenName);
      capsule.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getTokenStore().put(capsule.createDbKey(), capsule);
      chargeFee(ownerAddress, fee);
      //dont move pool fee to burned unx account
      dbManager.adjustBalance(ownerAddress, -ctx.getFeePool());
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      //add token issued list, dont allow the same name
      accountCapsule.addToken(capsule.createDbKey(), capsule.getTotalSupply());
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);
      ret.setStatus(fee, code.SUCESS);
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

    if (!TransactionUtil.validTokenName(ctx.getName().toByteArray()))
      throw new ContractValidateException("Invalid tokenName");

    if (ctx.getName().isEmpty() || !TransactionUtil.validTokenName(ctx.getName().toByteArray())) {
      throw new ContractValidateException("Invalid token name");
    }

    var tokenName = ctx.getName().toStringUtf8();
    if (tokenName.equalsIgnoreCase("UNX"))
      throw new ContractValidateException("Token name can't be UNX");

    if (this.dbManager.getTokenStore().get(tokenName.getBytes()) != null) {
      throw new ContractValidateException("Token exists");
    }

    if ((!ctx.getAbbr().isEmpty()) && !TransactionUtil.validTokenName(ctx.getAbbr().toByteArray())) {
      throw new ContractValidateException("Invalid token abbreviation");
    }

    if (!TransactionUtil.validUrl(ctx.getUrl().toByteArray()))
      throw new ContractValidateException("Invalid url");

    if (!TransactionUtil.validAssetDescription(ctx.getDescription().toByteArray()))
      throw new ContractValidateException("Invalid description");

    if (ctx.getStartTime() == 0)
      throw new ContractValidateException("Start time should be not empty");

    if (ctx.getEndTime() == 0)
      throw new ContractValidateException("End time should be not empty");

    if (ctx.getEndTime() <= ctx.getStartTime())
      throw new ContractValidateException("End time should be greater than start time");

    if (ctx.getStartTime() <= dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Start time should be greater than HeadBlockTime");

    if (ctx.getTotalSupply() <= 0)
      throw new ContractValidateException("TotalSupply must greater than 0!");

    if (ctx.getMaxSupply() <= 0)
      throw new ContractValidateException("MaxSupply must greater than 0!");

    if (ctx.getMaxSupply() < ctx.getTotalSupply())
      throw new ContractValidateException("MaxSupply must greater or equal than TotalSupply!");

    if (ctx.getFee() < 0)
      throw new ContractValidateException("Token transfer fee as token must greater or equal than 0!");

    if (ctx.getFeePool() < 0)
      throw new ContractValidateException("pre-transfer pool fee as UNW must greater or equal than 0!");

    var accountCap = dbManager.getAccountStore().get(ownerAddress);
    if (Objects.isNull(accountCap))
      throw new ContractValidateException("Account not exists");

    if (accountCap.getBalance() < calcFee() + ctx.getFeePool())
      throw new ContractValidateException("No enough balance for fee & pre-transfer pool fee");

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(CreateTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_CREATE_FEE;
  }
}
