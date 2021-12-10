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
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.CreateTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.config.Parameter.ChainConstant.*;
import static org.unichain.core.services.http.utils.Util.TOKEN_CREATE_FIELD_END_TIME;
import static org.unichain.core.services.http.utils.Util.TOKEN_CREATE_FIELD_START_TIME;

@Slf4j(topic = "actuator")
public class TokenExchangeActuator extends AbstractActuator {

  TokenExchangeActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var accountStore = dbManager.getAccountStore();
      var tokenStore = dbManager.getTokenPoolStore();

      var ctx = contract.unpack(Contract.TokenExchangeContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var ownerAccount = accountStore.get(ownerAddress);

      byte[] tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = tokenStore.get(tokenKey);
      var tokenOwnerAddress = tokenPool.getOwnerAddress().toByteArray();
      var tokenOwnerAcc = accountStore.get(tokenOwnerAddress);

      var exchUnw = tokenPool.getExchUnw();
      var exchToken = tokenPool.getExchToken();
      var exchangedToken = ctx.getAmount() * exchToken / exchUnw; //@todo review ginza factor

      ownerAccount.addToken(tokenKey, exchangedToken);
      accountStore.put(ownerAddress, ownerAccount);

      tokenOwnerAcc.burnToken(tokenKey, exchangedToken);

      ownerAccount.setBalance(ownerAccount.getBalance() - ctx.getAmount() - fee);
      tokenOwnerAcc.setBalance(tokenOwnerAcc.getBalance() + ctx.getAmount());
      accountStore.put(ownerAddress, ownerAccount);
      accountStore.put(tokenOwnerAddress, tokenOwnerAcc);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (Exception e) {
      logger.error("exec TokenExchange got error ->" , e);
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

      var accountStore = dbManager.getAccountStore();
      var tokenPoolStore = dbManager.getTokenPoolStore();
      val ctx = this.contract.unpack(Contract.TokenExchangeContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      var ownerCap = accountStore.get(ownerAddress);
      Assert.notNull(ownerCap, "Account not exists");

      byte[] tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = tokenPoolStore.get(tokenKey);
      Assert.notNull(tokenPool, "Token pool not found");
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));

      var tokenOwnerAccount = accountStore.get(tokenPool.getOwnerAddress().toByteArray());
      Assert.notNull(tokenOwnerAccount, "Token owner account not exists");
      Assert.isTrue(tokenOwnerAccount.getTokenAvailable(tokenKey) >= ctx.getAmount(), "Not enough token to exchange");
      Assert.isTrue(ownerCap.getBalance() >= calcFee(), "Not enough balance to exchange");
      return true;
    }
    catch (Exception e){
      logger.error("validate TokenExchangeActuator got error ->", e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Contract.TokenExchangeContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getTransactionFee();//500 unw default
  }
}
