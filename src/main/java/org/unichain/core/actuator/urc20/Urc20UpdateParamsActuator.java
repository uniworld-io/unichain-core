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

package org.unichain.core.actuator.urc20;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20UpdateParamsContractCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc20UpdateParamsContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;
import java.util.Arrays;

import static org.unichain.core.config.Parameter.ChainConstant.*;

@Slf4j(topic = "actuator")
public class Urc20UpdateParamsActuator extends AbstractActuator {
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_FEE = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.FEE_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_LOT = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.LOT_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_FEE_RATE = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.EXTRA_FEE_RATE_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_URL = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.URL_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_TOTAL_SUPPLY = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.TOTAL_SUPPLY_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_FEE_POOL = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.FEE_POOL_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_EXCH_UNW_NUM = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.EXCH_UNX_NUM_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_EXCH_TOKEN_NUM = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.EXCH_NUM_FIELD_NUMBER);
    private static Descriptors.FieldDescriptor URC20_UPDATE_PARAMS_FIELD_CREATE_ACC_FEE = Urc20UpdateParamsContract.getDescriptor().findFieldByNumber(Urc20UpdateParamsContract.CREATE_ACC_FEE_FIELD_NUMBER);

    public Urc20UpdateParamsActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
        var ctx = contract.unpack(Urc20UpdateParamsContract.class);
        var ctxCap = new Urc20UpdateParamsContractCapsule(ctx);
        var accountStore = dbManager.getAccountStore();
        var contractStore = dbManager.getUrc20ContractStore();

        var ownerAddr = ctx.getOwnerAddress().toByteArray();
        var contractAddr = ctx.getAddress().toByteArray();

        var contractCap = contractStore.get(contractAddr);
        var updateCriticalParams = false;

        if(ctx.hasField(URC20_UPDATE_PARAMS_FIELD_FEE)) {
            contractCap.setFee(ctx.getFee());
            updateCriticalParams = true;
        }

        if(ctx.hasField(URC20_UPDATE_PARAMS_FIELD_FEE_RATE)) {
            contractCap.setExtraFeeRate(ctx.getExtraFeeRate());
            updateCriticalParams = true;
        }

        if(ctx.hasField(URC20_UPDATE_PARAMS_FIELD_LOT)) {
            contractCap.setLot(ctx.getLot());
        }

        if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_URL)) {
            contractCap.setUrl(ctx.getUrl());
        }

        if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_TOTAL_SUPPLY)) {
            var newTotalSupply = ctxCap.getTotalSupply();
            var totalSupplyDiff = newTotalSupply.subtract(contractCap.getTotalSupply());
            contractCap.setTotalSupply(newTotalSupply);
            var ownerAccount = accountStore.get(ownerAddr);
            ownerAccount.addUrc20Token(contractAddr, totalSupplyDiff);
            accountStore.put(ownerAddr, ownerAccount);
            updateCriticalParams = true;
        }

        if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_FEE_POOL)) {
            var newFeePool = ctx.getFeePool();
            var oldFeePool = contractCap.getOriginFeePool();
            var diffFeePool = Math.subtractExact(newFeePool, oldFeePool);
            contractCap.setOriginFeePool(newFeePool);
            dbManager.adjustBalance(ownerAddr, -diffFeePool);
            contractCap.setFeePool(Math.addExact(contractCap.getFeePool(), diffFeePool));
        }

        if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_EXCH_UNW_NUM)) {
            contractCap.setExchUnwNum(ctx.getExchUnxNum());
            updateCriticalParams = true;
        }

        if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_EXCH_TOKEN_NUM)) {
            contractCap.setExchTokenNum(ctx.getExchNum());
            updateCriticalParams = true;
        }

        if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_CREATE_ACC_FEE)) {
            contractCap.setCreateAccFee(ctx.getCreateAccFee());
            updateCriticalParams = true;
        }

        if(updateCriticalParams)
        {
            contractCap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
        }

        contractCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
        contractStore.put(contractAddr, contractCap);

        chargeFee(ownerAddr, fee);
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
          Assert.isTrue(contract.is(Urc20UpdateParamsContract.class), "Contract type error,expected type [Urc20UpdateParamsContract],real type[" + contract.getClass() + "]");

          val ctx = this.contract.unpack(Urc20UpdateParamsContract.class);
          var ctxCap = new Urc20UpdateParamsContractCapsule(ctx);
          var accountStore = dbManager.getAccountStore();
          var contractStore = dbManager.getUrc20ContractStore();

          var ownerAddr = ctx.getOwnerAddress().toByteArray();
          var contractAddr = ctx.getAddress().toByteArray();
          Assert.isTrue(Wallet.addressValid(ownerAddr)
                          && accountStore.has(ownerAddr)
                          && Wallet.addressValid(contractAddr)
                          && contractStore.has(contractAddr),
                  "Unrecognized owner|contract address");

          var accountCap = accountStore.get(ownerAddr);
          Assert.isTrue (accountCap.getBalance() >= calcFee(), "Not enough balance");

          var contractAddrBase58 = Wallet.encode58Check(contractAddr);
          var contractCap = contractStore.get(contractAddr);
          Assert.isTrue(Arrays.equals(ownerAddr, contractCap.getOwnerAddress().toByteArray()), "Mismatched Contract owner not allowed to mine");
          Assert.isTrue (dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
          Assert.isTrue (dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_FEE)) {
              var fee = ctx.getFee();
              Assert.isTrue (fee >= 0 && fee <= TOKEN_MAX_TRANSFER_FEE, "Invalid fee amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE + "]");
          }

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_LOT)) {
              Assert.isTrue (ctx.getLot() >= 0, "Invalid lot: require positive!");
          }

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_FEE_RATE)) {
              var extraFeeRate = ctx.getExtraFeeRate();
              Assert.isTrue (extraFeeRate >= 0 && extraFeeRate <= 100 && extraFeeRate <= TOKEN_MAX_TRANSFER_FEE_RATE, "Invalid extra fee rate amount, should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");
          }

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_URL)) {
              Assert.isTrue(TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUrl().getBytes()).toByteArray()), "Invalid url");
          }

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_TOTAL_SUPPLY)) {
              var maxSupply = contractCap.getMaxSupply();
              var newTotalSupply = ctxCap.getTotalSupply();
              var oldTotalSupply = contractCap.getTotalSupply();
              var diff = newTotalSupply.subtract(oldTotalSupply);

              Assert.isTrue(diff.compareTo(BigInteger.ZERO) != 0, "Total supply not changed!");

              if(diff.compareTo(BigInteger.ZERO) > 0){
                  Assert.isTrue(maxSupply.compareTo(newTotalSupply) >= 0, "New total supply break max supply: " + maxSupply);
              }
              else if(diff.compareTo(BigInteger.ZERO) < 0){
                  var availableSupply = accountCap.getUrc20TokenAvailable(contractAddrBase58);
                  Assert.isTrue(availableSupply.add(diff).compareTo(BigInteger.ZERO) >= 0, "Max available token supply not enough to lower down total supply, minimum total supply is: " + oldTotalSupply.subtract(availableSupply));
              }
          }

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_FEE_POOL)) {
              var newFeePool = ctx.getFeePool();
              var oldFeePool = contractCap.getOriginFeePool();
              var availableFeePool = contractCap.getFeePool();
              var diffFeePool = Math.subtractExact(newFeePool, oldFeePool);
              Assert.isTrue(diffFeePool != 0, "Fee pool not changed");
              if(diffFeePool > 0){
                    Assert.isTrue(accountCap.getBalance() >= Math.addExact(diffFeePool, calcFee()), "Not enough balance to update new fee pool, at least: " + diffFeePool + calcFee());
              }
              else if(diffFeePool < 0){
                  Assert.isTrue(Math.addExact(availableFeePool, diffFeePool) >= 0 && ( Math.subtractExact(accountCap.getBalance(), Math.addExact(diffFeePool, calcFee())) >= 0), "available fee pool not enough to lower down fee pool or balance not enough fee, require at least: " + diffFeePool + " fee :"+ calcFee());
              }
          }

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_EXCH_UNW_NUM)) {
              Assert.isTrue(ctx.getExchUnxNum() > 0, "Exchange unw number must be positive");
          }

          if (ctx.hasField(URC20_UPDATE_PARAMS_FIELD_EXCH_TOKEN_NUM)) {
              Assert.isTrue(ctx.getExchNum() > 0, "Exchange token number must be positive");
          }

          if(ctx.hasField(URC20_UPDATE_PARAMS_FIELD_CREATE_ACC_FEE)){
              Assert.isTrue(ctx.getCreateAccFee() > 0 && ctx.getCreateAccFee() <= TOKEN_MAX_CREATE_ACC_FEE, "Invalid create account fee");
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
    return contract.unpack(Urc20UpdateParamsContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
      return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2UNW default
    }
}
