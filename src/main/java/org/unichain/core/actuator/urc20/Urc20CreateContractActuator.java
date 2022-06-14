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
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.TokenCreateEvent;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20ContractCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;
import java.util.Arrays;

import static org.unichain.core.config.Parameter.ChainConstant.*;

@Slf4j(topic = "actuator")
public class Urc20CreateContractActuator extends AbstractActuator {

  public static final long MAX_DECIMALS = 18L;

  public static Descriptors.FieldDescriptor URC20_CREATE_FIELD_START_TIME= Contract.Urc20CreateContract.getDescriptor().findFieldByNumber(Contract.Urc20CreateContract.START_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor URC20_CREATE_FIELD_END_TIME= Contract.Urc20CreateContract.getDescriptor().findFieldByNumber(Contract.Urc20CreateContract.END_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor URC20_CREATE_FIELD_ENABLE_EXCH = Contract.Urc20CreateContract.getDescriptor().findFieldByNumber(Contract.Urc20CreateContract.EXCH_ENABLE_FIELD_NUMBER);

  public Urc20CreateContractActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Contract.Urc20CreateContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      var contractCap = new Urc20ContractCapsule(ctx);

      if(!ctx.hasField(URC20_CREATE_FIELD_START_TIME))
      {
        contractCap.setStartTime(dbManager.getHeadBlockTimeStamp());
      }
      var startTime = contractCap.getStartTime();

      if(!ctx.hasField(URC20_CREATE_FIELD_END_TIME))
      {
        contractCap.setEndTime(Math.addExact(startTime , URC30_DEFAULT_AGE_V3));
      }

      if(!ctx.hasField(URC20_CREATE_FIELD_ENABLE_EXCH))
      {
        contractCap.setEnableExch(true);
      }


      contractCap.setBurnedToken(BigInteger.ZERO);
      contractCap.setSymbol(contractCap.getSymbol().toUpperCase());
      contractCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      contractCap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      contractCap.setOriginFeePool(ctx.getFeePool());
      dbManager.getUrc20ContractStore().put(contractAddr, contractCap);

      var accountCapsule = dbManager.getAccountStore().get(ownerAddr);
      accountCapsule.addUrc20Token(contractCap.createDbKey(), contractCap.getTotalSupply());
      accountCapsule.setBalance(Math.subtractExact(accountCapsule.getBalance(), Math.addExact(ctx.getFeePool(), fee)));
      dbManager.getAccountStore().put(ownerAddr, accountCapsule);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);

      //register new account with type asset_issue
      dbManager.createDefaultAccount(contractAddr, Protocol.AccountType.Contract);

      //emit event
      var event = NativeContractEvent.builder()
              .topic("Urc20ContractCreate")
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
      var accountStore = dbManager.getAccountStore();
      Assert.isTrue(contract.is(Contract.Urc20CreateContract.class), "contract type error,expected type [Urc20CreateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Contract.Urc20CreateContract.class);

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();

      Assert.isTrue(Wallet.addressValid(ownerAddr)
                      && accountStore.has(ownerAddr)
                      && !Arrays.equals(dbManager.getBurnAddress(), ownerAddr),
              "Invalid owner: unrecongized or burn address");

      var accountCap = accountStore.get(ownerAddr);
      Assert.isTrue( accountCap.getType() != Protocol.AccountType.Contract, "Account not exists or must be not contract type type");

      Assert.isTrue(Wallet.addressValid(contractAddr)
              && !accountStore.has(contractAddr)
              && !dbManager.getUrc20ContractStore().has(contractAddr)
              && !Arrays.equals(dbManager.getBurnAddress(), contractAddr), "Bad contract address: invalid|exist|is burn address");

      Assert.isTrue(!ctx.getSymbol().isEmpty() && TransactionUtil.validTokenSymbol(ctx.getSymbol()), "Invalid contract symbol");
      Assert.isTrue(!ctx.getSymbol().equalsIgnoreCase("UNW"), "Token symbol can't be UNW");
      Assert.isTrue(!StringUtils.isEmpty(ctx.getName().isEmpty()) && TransactionUtil.validTokenName(ctx.getName()), "Invalid token name");
      Assert.isTrue(TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUrl().getBytes()).toByteArray()), "Invalid url");

      var startTime = ctx.hasField(URC20_CREATE_FIELD_START_TIME) ? ctx.getStartTime() : dbManager.getHeadBlockTimeStamp();
      var maxActive = Math.addExact(dbManager.getHeadBlockTimeStamp(), URC30_MAX_ACTIVE);
      Assert.isTrue((startTime >= dbManager.getHeadBlockTimeStamp()) && (startTime <= maxActive), "Invalid start time: must be greater than current block time and lower than limit timestamp:" +maxActive);

      var endTime = ctx.hasField(URC20_CREATE_FIELD_END_TIME) ? ctx.getEndTime() : Math.addExact(startTime, URC30_DEFAULT_AGE_V3);
      var maxAge = dbManager.getHeadBlockTimeStamp() + URC30_MAX_AGE_V3;
      Assert.isTrue((endTime > 0)
              && (endTime > startTime )
              && (endTime > dbManager.getHeadBlockTimeStamp())
              && (endTime <= maxAge) , "Invalid end time: must greater start time and lower than token age's limit timestamp:" + maxAge);

      val ctxCap = new Urc20ContractCapsule(ctx);
      Assert.isTrue(ctxCap.getTotalSupply().compareTo(BigInteger.ZERO) > 0 , "TotalSupply must greater than 0");
      Assert.isTrue(ctxCap.getMaxSupply().compareTo(BigInteger.ZERO) > 0 , "MaxSupply must greater than 0!");
      Assert.isTrue(ctxCap.getMaxSupply().compareTo(ctxCap.getTotalSupply()) >= 0 , "MaxSupply must greater or equal than TotalSupply");
      Assert.isTrue(ctx.getFee() >= 0 && ctx.getFee() <= TOKEN_MAX_TRANSFER_FEE, "Invalid token transfer fee: must be positive and not exceed max fee : " + TOKEN_MAX_TRANSFER_FEE + " tokens");
      Assert.isTrue(ctx.getExtraFeeRate() >= 0 && ctx.getExtraFeeRate() <= 100 && ctx.getExtraFeeRate() <= TOKEN_MAX_TRANSFER_FEE_RATE, "Invalid extra fee rate , should between [0, " + TOKEN_MAX_TRANSFER_FEE_RATE + "]");

      Assert.isTrue(ctx.getFeePool() >= URC30_MIN_POOL_FEE && (accountCap.getBalance() >= Math.addExact(calcFee(), ctx.getFeePool())), "Invalid fee pool or not enough balance for fee & pre-deposit pool fee");
      Assert.isTrue(ctx.getLot() >= 0, "Invalid lot: must not negative");
      Assert.isTrue(ctx.getExchUnxNum() > 0, "Invalid exchange unw number: must be positive");
      Assert.isTrue(ctx.getExchNum() > 0, "Invalid exchange token number: must be positive");
      Assert.isTrue(ctx.getDecimals() >= 0 && ctx.getDecimals() <= MAX_DECIMALS, "Invalid decimals number: must be from 0 to " + MAX_DECIMALS);
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
    return contract.unpack(Contract.Urc20CreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 UNW default
  }
}
