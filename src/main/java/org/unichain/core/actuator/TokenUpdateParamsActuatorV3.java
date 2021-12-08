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
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.UpdateTokenParamsContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE;
import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE_RATE;
import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "actuator")
public class TokenUpdateParamsActuatorV3 extends AbstractActuator {

  TokenUpdateParamsActuatorV3(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
        var ctx = contract.unpack(UpdateTokenParamsContract.class);
        var ownerAddress = ctx.getOwnerAddress().toByteArray();

        var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());

        TokenPoolCapsule tokenCap = dbManager.getTokenPoolStore().get(tokenKey);

        if(ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE)) {
          tokenCap.setFee(ctx.getAmount());
        }

        if(ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE_RATE)) {
          tokenCap.setExtraFeeRate(ctx.getExtraFeeRate());
        }

        if(ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_LOT)) {
          tokenCap.setLot(ctx.getLot());
        }

        if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_URL)) {
            tokenCap.setUrl(ctx.getUrl());
        }

        if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_DESCRIPTION)) {
            tokenCap.setDescription(ctx.getDescription());
        }

        if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_TOTAL_SUPPLY)) {
            var newTotalSupply = ctx.getTotalSupply();
            var totalSupplyDiff = newTotalSupply - tokenCap.getTotalSupply();
            tokenCap.setTotalSupply(newTotalSupply);
            var ownerAccount = dbManager.getAccountStore().get(ownerAddress);
            ownerAccount.mineToken(tokenKey, totalSupplyDiff);
            dbManager.getAccountStore().put(ownerAddress, ownerAccount);
        }

        if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE_POOL)) {
            var newFeePool = ctx.getFeePool();
            var oldFeePool = tokenCap.getOriginFeePool();
            var diffFeePool = newFeePool - oldFeePool;
            tokenCap.setOriginFeePool(newFeePool);
            dbManager.adjustBalance(ownerAddress, -diffFeePool);
            tokenCap.setFeePool(tokenCap.getFeePool() + diffFeePool);
        }

        dbManager.getTokenPoolStore().put(tokenKey, tokenCap);

        chargeFee(ownerAddress, fee);
        ret.setStatus(fee, code.SUCESS);
        return true;
    } catch (Exception e) {
      logger.error("TokenUpdateParams got error --> ", e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
      try {
          Assert.notNull(contract, "No contract!");
          Assert.notNull(dbManager, "No dbManager!");
          Assert.isTrue(contract.is(UpdateTokenParamsContract.class), "contract type error,expected type [UpdateTokenParamsContract],real type[" + contract.getClass() + "]");

          val ctx = this.contract.unpack(UpdateTokenParamsContract.class);

          Assert.isTrue(ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_OWNER_ADDR), "missing owner address");
          Assert.isTrue(ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_NAME), "missing token name");

          var ownerAddress = ctx.getOwnerAddress().toByteArray();
          var accountCap = dbManager.getAccountStore().get(ownerAddress);
          Assert.notNull(accountCap, "Invalid ownerAddress");
          Assert.isTrue (accountCap.getBalance() >= calcFee(), "Not enough balance");

          var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
          var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
          Assert.notNull(tokenPool, "TokenName not exist");

          Assert.isTrue(Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()), "Mismatched token owner not allowed to mine");

          Assert.isTrue (dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));
          Assert.isTrue (dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE)) {
              var fee = ctx.getAmount();
              Assert.isTrue (fee >= 0 && fee <= TOKEN_MAX_TRANSFER_FEE, "invalid fee amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE + "]");
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_LOT)) {
              Assert.isTrue (ctx.getLot() >= 0, "invalid lot: require positive!");
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE_RATE)) {
              var extraFeeRate = ctx.getExtraFeeRate();
              Assert.isTrue (extraFeeRate >= 0 && extraFeeRate <= 100 && extraFeeRate <= TOKEN_MAX_TRANSFER_FEE_RATE, "invalid extra fee rate amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_URL)) {
              Assert.isTrue(TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUrl().getBytes()).toByteArray()), "Invalid url");
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_DESCRIPTION)) {
              Assert.isTrue(TransactionUtil.validAssetDescription(ByteString.copyFrom(ctx.getDescription().getBytes()).toByteArray()), "Invalid description");
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_TOTAL_SUPPLY)) {
              var maxSupply = tokenPool.getMaxSupply();
              var newTotalSupply = ctx.getTotalSupply();
              var oldTotalSupply = tokenPool.getTotalSupply();
              var diff = newTotalSupply - oldTotalSupply;

              Assert.isTrue(diff != 0, "total supply not changed!");

              if(diff > 0){
                  Assert.isTrue(maxSupply >= newTotalSupply, "new total supply break max supply: " + maxSupply);
              }
              else if(diff < 0){
                  var availableSupply = accountCap.getTokenAvailable(tokenKey);
                  Assert.isTrue(availableSupply + diff >= 0, "max available token supply not enough to lower down total supply, minimum total supply is: " + (oldTotalSupply - availableSupply));
              }
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE_POOL)) {
              var newFeePool = ctx.getFeePool();
              var oldFeePool = tokenPool.getOriginFeePool();
              var availableFeePool = tokenPool.getFeePool();
              var diffFeePool = newFeePool - oldFeePool;
              Assert.isTrue(diffFeePool != 0, "fee pool not changed");
              if(diffFeePool > 0){
                    Assert.isTrue(accountCap.getBalance() >= diffFeePool + calcFee(), "not enough balance to update new fee pool, at least: " + diffFeePool + calcFee());
              }
              else if(diffFeePool < 0){
                  Assert.isTrue(availableFeePool + diffFeePool >= 0 && (accountCap.getBalance() - diffFeePool - calcFee() ) >= 0, "available fee pool not enough to lower down fee pool or balance not enough fee, require at least: " + diffFeePool + " fee :"+ calcFee());
              }
          }

          return true;
      }
      catch (Exception e){
          logger.error("validate TokenUpdateParams got error -->", e);
          throw new ContractValidateException(e.getMessage());
      }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateTokenParamsContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
      return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2unw default
  }
}
