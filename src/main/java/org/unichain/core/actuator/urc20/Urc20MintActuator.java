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
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20MintContractCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.Urc20MintContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;
import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc20MintActuator extends AbstractActuator {

  private static Descriptors.FieldDescriptor TO_ADDRESS_FIELD_NUMBER = Contract.Urc20MintContract.getDescriptor().findFieldByNumber(Urc20MintContract.TO_ADDRESS_FIELD_NUMBER);

  public Urc20MintActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var urc20Store = dbManager.getUrc20ContractStore();
      var accStore = dbManager.getAccountStore();

      var ctx = contract.unpack(Urc20MintContract.class);
      var ctxCap = new Urc20MintContractCapsule(ctx);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var urc20Addr = ctx.getAddress().toByteArray();

      //update contract
      var urc20Cap = urc20Store.get(urc20Addr);
      urc20Cap.setTotalSupply(urc20Cap.getTotalSupply().add(ctxCap.getAmount()));
      urc20Cap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      urc20Cap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      urc20Store.put(urc20Addr, urc20Cap);

      //mine token
      AccountCapsule toAccountCap;
      byte[] toAddr;

      if(ctx.hasField(TO_ADDRESS_FIELD_NUMBER)){
        toAddr = ctx.getToAddress().toByteArray();
        if(!accStore.has(toAddr)){
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
          toAccountCap = dbManager.createDefaultAccount(toAddr, Protocol.AccountType.Normal);
        }
        else
        {
          toAccountCap = accStore.get(toAddr);
        }
      }
      else {
        toAddr = ownerAddr;
        toAccountCap = accStore.get(toAddr);
      }

      toAccountCap.addUrc20Token(urc20Addr, ctxCap.getAmount());
      accStore.put(toAddr, toAccountCap);

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
      Assert.isTrue(contract.is(Urc20MintContract.class), "Contract type error,expected type [Urc20MintContract], real type[" + contract.getClass() + "]");

      var accStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc20ContractStore();

      val ctx = this.contract.unpack(Urc20MintContract.class);
      val ctxCap = new Urc20MintContractCapsule(ctx);
      var fee = calcFee();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var urc20Addr = ctx.getAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddr) && accStore.has(ownerAddr)
      && Wallet.addressValid(urc20Addr) && contractStore.has(urc20Addr), "Unrecognized owner|contract address");

      if(ctx.hasField(TO_ADDRESS_FIELD_NUMBER)){
        var toAddr = ctx.getToAddress().toByteArray();
        Assert.isTrue(Wallet.addressValid(toAddr), "To address invalid!");
        Assert.isTrue(!Arrays.equals(toAddr, ownerAddr), "Bad to address: is owner!");
        Assert.isTrue(!Arrays.equals(dbManager.getBurnAddress(), toAddr), "Bad to address: is burner!");
        Assert.isTrue((!accStore.has(toAddr) || (accStore.get(toAddr).getType() != Protocol.AccountType.Contract)), "Bad to address: is contract!");

        if(!accStore.has(toAddr)){
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }

      var ownerCap = accStore.get(ownerAddr);
      Assert.isTrue(ownerCap.getBalance() >= fee, "Fee exceed balance");

      var urc20Cap = contractStore.get(urc20Addr);

      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < urc20Cap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(urc20Cap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= urc20Cap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(urc20Cap.getStartTime()));

      Assert.isTrue(Arrays.equals(ownerAddr, urc20Cap.getOwnerAddress().toByteArray()), "Only contract owner allowed to mine");

      Assert.isTrue(ctxCap.getAmount().compareTo(BigInteger.valueOf(urc20Cap.getLot())) >= 0, "Mined amount at least equal lot: " + urc20Cap.getLot());

      //avail to mine = max - total - burned
      var availableToMine = urc20Cap.getMaxSupply().subtract(urc20Cap.getTotalSupply().add(urc20Cap.getBurnedToken()));
      Assert.isTrue(ctxCap.getAmount().compareTo(availableToMine) <= 0, "Not enough frozen token to mine, maximum allowed: " + availableToMine);

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc20MintContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2UNW default
  }
}
