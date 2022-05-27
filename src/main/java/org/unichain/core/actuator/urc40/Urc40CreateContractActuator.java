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

package org.unichain.core.actuator.urc40;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.TokenCreateEvent;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc40.Urc40ContractCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import static org.unichain.core.config.Parameter.ChainConstant.*;
import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "actuator")
public class Urc40CreateContractActuator extends AbstractActuator {

  public Urc40CreateContractActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Contract.Urc40CreateContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var contractCap = new Urc40ContractCapsule(ctx);
      var contractAddr = contractCap.getAddress().toByteArray();

      if(!ctx.hasField(URC40_CREATE_FIELD_START_TIME))
      {
        contractCap.setStartTime(dbManager.getHeadBlockTimeStamp());
      }
      var startTime = contractCap.getStartTime();

      if(!ctx.hasField(URC40_CREATE_FIELD_END_TIME))
      {
        contractCap.setEndTime(Math.addExact(startTime , URC30_DEFAULT_AGE_V3));
      }

      if(!ctx.hasField(URC40_CREATE_FIELD_ENABLE_EXCH))
      {
        contractCap.setEnableExch(true);
      }

      contractCap.setBurnedToken(0L);
      contractCap.setSymbol(contractCap.getSymbol().toUpperCase());
      contractCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      contractCap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      contractCap.setOriginFeePool(ctx.getFeePool());
      dbManager.getUrc40ContractStore().put(contractAddr, contractCap);

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      accountCapsule.addUrc40Token(contractCap.createDbKey(), contractCap.getTotalSupply());
      accountCapsule.setBalance(Math.subtractExact(accountCapsule.getBalance(), Math.addExact(ctx.getFeePool(), fee)));
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);

      //register new account with type asset_issue
      var defaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
      var tokenAccount = new AccountCapsule(ByteString.copyFrom(contractAddr), Protocol.AccountType.AssetIssue, dbManager.getHeadBlockTimeStamp(), defaultPermission, dbManager);
      dbManager.getAccountStore().put(contractAddr, tokenAccount);

      //emit event
      var event = NativeContractEvent.builder()
              .topic("Urc40ContractCreate")
              .rawData(
                      TokenCreateEvent.builder()
                              .owner_address(Hex.encodeHexString(ctx.getOwnerAddress().toByteArray()))
                              .name(ctx.getName())
                              .max_supply(ctx.getMaxSupply())
                              .total_supply(ctx.getTotalSupply())
                              .build())
              .build();
      emitEvent(event, ret);
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
      Assert.isTrue(contract.is(Contract.Urc40CreateContract.class), "contract type error,expected type [Urc40CreateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Contract.Urc40CreateContract.class);

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      var accountCap = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCap, "Account not exists");

      var contractAddr = ctx.getAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(contractAddr), "Invalid contractAddress");
      var contractAddrBase58 = Wallet.encode58Check(contractAddr);
      Assert.isTrue(!dbManager.getAccountStore().has(contractAddr) && !dbManager.getUrc40ContractStore().has(contractAddr), "Contract address exists: " + contractAddrBase58);

      Assert.isTrue(!ctx.getSymbol().isEmpty() && TransactionUtil.validTokenName(ctx.getSymbol().getBytes()), "Invalid contract symbol");
      Assert.isTrue(!ctx.getSymbol().equalsIgnoreCase("UNX"), "Token symbol can't be UNX");
      Assert.isTrue(!StringUtils.isEmpty(ctx.getName().isEmpty()) && TransactionUtil.validTokenName(ctx.getName().getBytes()), "Invalid token name");
      Assert.isTrue(TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUrl().getBytes()).toByteArray()), "Invalid url");

      var startTime = ctx.hasField(URC40_CREATE_FIELD_START_TIME) ? ctx.getStartTime() : dbManager.getHeadBlockTimeStamp();
      var maxActive = Math.addExact(dbManager.getHeadBlockTimeStamp(), URC30_MAX_ACTIVE);
      Assert.isTrue((startTime >= dbManager.getHeadBlockTimeStamp()) && (startTime <= maxActive), "Invalid start time: must be greater than current block time and lower than limit timestamp:" +maxActive);

      var endTime = ctx.hasField(URC40_CREATE_FIELD_END_TIME) ? ctx.getEndTime() : Math.addExact(startTime, URC30_DEFAULT_AGE_V3);
      var maxAge = dbManager.getHeadBlockTimeStamp() + URC30_MAX_AGE_V3;
      Assert.isTrue((endTime > 0)
              && (endTime > startTime )
              && (endTime > dbManager.getHeadBlockTimeStamp())
              && (endTime <= maxAge) , "Invalid end time: must greater start time and lower than token age's limit timestamp:" + maxAge);

      Assert.isTrue(ctx.getTotalSupply() > 0 , "TotalSupply must greater than 0");
      Assert.isTrue(ctx.getMaxSupply() > 0 , "MaxSupply must greater than 0!");
      Assert.isTrue(ctx.getMaxSupply() >= ctx.getTotalSupply() , "MaxSupply must greater or equal than TotalSupply");
      Assert.isTrue(ctx.getFee() >= 0 && ctx.getFee() <= TOKEN_MAX_TRANSFER_FEE, "Invalid token transfer fee: must be positive and not exceed max fee : " + TOKEN_MAX_TRANSFER_FEE + " tokens");
      Assert.isTrue(ctx.getExtraFeeRate() >= 0 && ctx.getExtraFeeRate() <= 100 && ctx.getExtraFeeRate() <= TOKEN_MAX_TRANSFER_FEE_RATE, "Invalid extra fee rate , should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");

      Assert.isTrue(ctx.getFeePool() >= URC30_MIN_POOL_FEE && (accountCap.getBalance() >= Math.addExact(calcFee(), ctx.getFeePool())), "Invalid fee pool or not enough balance for fee & pre-deposit pool fee");
      Assert.isTrue(ctx.getLot() >= 0, "Invalid lot: must not negative");
      Assert.isTrue(ctx.getExchUnxNum() > 0, "Invalid exchange unw number: must be positive");
      Assert.isTrue(ctx.getExchNum() > 0, "Invalid exchange token number: must be positive");
      Assert.isTrue(ctx.getDecimals() >= 0 && ctx.getDecimals() <= 100, "Invalid decimals number: must be from 0 to 100");

      Assert.isTrue(ctx.getCreateAccFee() > 0 && ctx.getCreateAccFee() <= TOKEN_MAX_CREATE_ACC_FEE, "Invalid create account fee");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Contract.Urc40CreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 UNW default
  }
}