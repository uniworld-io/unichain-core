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
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.UpdateTokenUrlContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

import static org.unichain.core.services.http.utils.Util.*;

@Deprecated
@Slf4j(topic = "actuator")
public class TokenUpdateUrlActuator extends AbstractActuator {

  TokenUpdateUrlActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      var ctx = contract.unpack(Contract.UpdateTokenUrlContract.class);
      logger.info("UpdateTokenUrl  {} ...", ctx);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenCap = dbManager.getTokenPoolStore().get(tokenKey);
      if(ctx.hasField(TOKEN_UPDATE_FIELD_URL))
        tokenCap.setUrl(ctx.getUrl());
      if(ctx.hasField(TOKEN_UPDATE_FIELD_DESC))
        tokenCap.setDescription(ctx.getDescription());
      dbManager.getTokenPoolStore().put(tokenKey, tokenCap);

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      logger.info("UpdateTokenUrl  {} ...DONE!", ctx);
      return true;
    } catch (Exception e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
      try {
          Assert.notNull(contract, "No contract!");
          Assert.notNull(dbManager, "No dbManager!");
          Assert.isTrue(contract.is(UpdateTokenUrlContract.class), "contract type error,expected type [UpdateTokenUrlContract],real type[" + contract.getClass() + "]");

          val ctx = this.contract.unpack(UpdateTokenUrlContract.class);

          Assert.isTrue(ctx.hasField(TOKEN_UPDATE_FIELD_OWNER_ADDR) && ctx.hasField(TOKEN_UPDATE_FIELD_NAME), "Missing owner address or token name");

          var ownerAddress = ctx.getOwnerAddress().toByteArray();
          var accountCap = dbManager.getAccountStore().get(ownerAddress);

          Assert.notNull(accountCap, "Invalid ownerAddress");

          Assert.isTrue (accountCap.getBalance() >= calcFee(), "Not enough balance");

          var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
          var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
          Assert.notNull (tokenPool, "TokenName not exist");

          Assert.isTrue (dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));

          Assert.isTrue (dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));

          Assert.isTrue (Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()), "only owner of token pool allowed to update url description");

          if (ctx.hasField(TOKEN_UPDATE_FIELD_URL) && !TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUrl().getBytes()).toByteArray()))
              throw new ContractValidateException("Invalid url");

          if (ctx.hasField(TOKEN_UPDATE_FIELD_DESC) && !TransactionUtil.validAssetDescription(ByteString.copyFrom(ctx.getDescription().getBytes()).toByteArray()))
              throw new ContractValidateException("Invalid description");

          return true;
      }
      catch (Exception e){
          logger.error("validate token update got error -->", e);
          throw new ContractValidateException(e.getMessage());
      }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateTokenUrlContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
      return dbManager.getDynamicPropertiesStore().getAssetIssueFee()/2;//250 unw default
  }
}
