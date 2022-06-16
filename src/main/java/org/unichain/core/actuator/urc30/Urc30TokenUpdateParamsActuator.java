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

package org.unichain.core.actuator.urc30;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc30.Urc30TokenPoolCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.UpdateTokenParamsContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE;
import static org.unichain.core.config.Parameter.ChainConstant.TOKEN_MAX_TRANSFER_FEE_RATE;

@Slf4j(topic = "actuator")
public class Urc30TokenUpdateParamsActuator extends AbstractActuator {

    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_OWNER_ADDR = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.OWNER_ADDRESS_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_NAME = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.TOKEN_NAME_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_FEE = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.AMOUNT_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_LOT = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.LOT_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_FEE_RATE = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.EXTRA_FEE_RATE_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_URL = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.URL_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_DESCRIPTION = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.DESCRIPTION_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_TOTAL_SUPPLY = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.TOTAL_SUPPLY_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_FEE_POOL = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.FEE_POOL_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_EXCH_UNW_NUM = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.EXCH_UNX_NUM_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_EXCH_TOKEN_NUM = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.EXCH_NUM_FIELD_NUMBER);
    public static Descriptors.FieldDescriptor TOKEN_UPDATE_PARAMS_FIELD_CREATE_ACC_FEE = UpdateTokenParamsContract.getDescriptor().findFieldByNumber(UpdateTokenParamsContract.CREATE_ACC_FEE_FIELD_NUMBER);

    public Urc30TokenUpdateParamsActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
        var ctx = contract.unpack(Contract.UpdateTokenParamsContract.class);
        var ownerAddress = ctx.getOwnerAddress().toByteArray();

        var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());

        Urc30TokenPoolCapsule tokenCap = dbManager.getTokenPoolStore().get(tokenKey);

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
            var totalSupplyDiff = Math.subtractExact(newTotalSupply, tokenCap.getTotalSupply());
            tokenCap.setTotalSupply(newTotalSupply);
            var ownerAccount = dbManager.getAccountStore().get(ownerAddress);
            ownerAccount.addToken(tokenKey, totalSupplyDiff);
            dbManager.getAccountStore().put(ownerAddress, ownerAccount);
        }

        if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE_POOL)) {
            var newFeePool = ctx.getFeePool();
            var oldFeePool = tokenCap.getOriginFeePool();
            var diffFeePool = Math.subtractExact(newFeePool, oldFeePool);
            tokenCap.setOriginFeePool(newFeePool);
            dbManager.adjustBalance(ownerAddress, -diffFeePool);
            tokenCap.setFeePool(Math.addExact(tokenCap.getFeePool(), diffFeePool));
        }

        dbManager.getTokenPoolStore().put(tokenKey, tokenCap);

        chargeFee(ownerAddress, fee);
        ret.setStatus(fee, code.SUCESS);
        return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
      try {
          Assert.notNull(contract, "No contract!");
          Assert.notNull(dbManager, "No dbManager!");
          Assert.isTrue(contract.is(UpdateTokenParamsContract.class), "Contract type error,expected type [UpdateTokenParamsContract],real type[" + contract.getClass() + "]");

          val ctx = this.contract.unpack(UpdateTokenParamsContract.class);

          Assert.isTrue(ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_OWNER_ADDR), "Missing owner address");
          Assert.isTrue(ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_NAME), "Missing token name");

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
              Assert.isTrue (fee >= 0 && fee <= TOKEN_MAX_TRANSFER_FEE, "Invalid fee amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE + "]");
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_LOT)) {
              Assert.isTrue (ctx.getLot() >= 0, "Invalid lot: require positive!");
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE_RATE)) {
              var extraFeeRate = ctx.getExtraFeeRate();
              Assert.isTrue (extraFeeRate >= 0 && extraFeeRate <= 100 && extraFeeRate <= TOKEN_MAX_TRANSFER_FEE_RATE, "Invalid extra fee rate amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");
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
              var diff = Math.subtractExact(newTotalSupply, oldTotalSupply);

              Assert.isTrue(diff != 0, "Total supply not changed!");

              if(diff > 0){
                  Assert.isTrue(maxSupply >= newTotalSupply, "New total supply break max supply: " + maxSupply);
              }
              else if(diff < 0){
                  var availableSupply = accountCap.getTokenAvailable(tokenKey);
                  Assert.isTrue(Math.addExact(availableSupply, diff) >= 0, "Max available token supply not enough to lower down total supply, minimum total supply is: " + (oldTotalSupply - availableSupply));
              }
          }

          if (ctx.hasField(TOKEN_UPDATE_PARAMS_FIELD_FEE_POOL)) {
              var newFeePool = ctx.getFeePool();
              var oldFeePool = tokenPool.getOriginFeePool();
              var availableFeePool = tokenPool.getFeePool();
              var diffFeePool = Math.subtractExact(newFeePool, oldFeePool);
              Assert.isTrue(diffFeePool != 0, "Fee pool not changed");
              if(diffFeePool > 0){
                    Assert.isTrue(accountCap.getBalance() >= Math.addExact(diffFeePool, calcFee()), "Not enough balance to update new fee pool, at least: " + diffFeePool + calcFee());
              }
              else if(diffFeePool < 0){
                  Assert.isTrue(Math.addExact(availableFeePool, diffFeePool) >= 0 && (Math.subtractExact(accountCap.getBalance(),  Math.addExact(diffFeePool, calcFee())) >= 0), "Available fee pool not enough to lower down fee pool or balance not enough fee, require at least: " + diffFeePool + " fee :"+ calcFee());
              }
          }

          return true;
      }
      catch (Exception e){
          logger.error("Actuator error: {} --> ", e.getMessage(), e);
          throw new ContractValidateException(e.getMessage());
      }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateTokenParamsContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
      return dbManager.getDynamicPropertiesStore().getAssetIssueFee()/2;//250 unw default
  }
}
