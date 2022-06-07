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
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.Urc20MintContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

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
      var ctx = contract.unpack(Urc20MintContract.class);
      var urc20Addr = ctx.getAddress().toByteArray();
      var urc20Cap = urc20Store.get(urc20Addr);
      urc20Cap.setTotalSupply(Math.addExact(urc20Cap.getTotalSupply(), ctx.getAmount()));
      urc20Cap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      urc20Cap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      urc20Store.put(urc20Addr, urc20Cap);

      var accStore = dbManager.getAccountStore();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();

      AccountCapsule toAccountCap;
      byte[] toAddr;

      if(ctx.hasField(TO_ADDRESS_FIELD_NUMBER)){
        toAddr = ctx.getToAddress().toByteArray();
        if(!accStore.has(toAddr)){
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
          toAccountCap = createDefaultAccount(toAddr);
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

      toAccountCap.addUrc20Token(urc20Addr, ctx.getAmount());
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

      val ctx = this.contract.unpack(Urc20MintContract.class);
      var fee = calcFee();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = accStore.get(ownerAddr);
      Assert.notNull(ownerAccountCap, "Owner address not exist");

      if(ctx.hasField(TO_ADDRESS_FIELD_NUMBER)){
        var toAddr = ctx.getToAddress().toByteArray();
        Assert.isTrue(Wallet.addressValid(toAddr) && !Arrays.equals(ownerAddr, toAddr), "Invalid to address");
        if(!accStore.has(toAddr)){
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }

      Assert.isTrue(ownerAccountCap.getBalance() >= fee, "Fee exceed balance");

      var urc20Addr = ctx.getAddress().toByteArray();
      var urc20AddrBase58 = Wallet.encode58Check(urc20Addr);
      var urc20Cap = dbManager.getUrc20ContractStore().get(urc20Addr);
      Assert.notNull(urc20Cap, "Contract not exist :" + urc20AddrBase58);

      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < urc20Cap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(urc20Cap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= urc20Cap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(urc20Cap.getStartTime()));

      //limit on owner only
      Assert.isTrue(Arrays.equals(ownerAddr, urc20Cap.getOwnerAddress().toByteArray()), "Mismatched Contract owner not allowed to mine");

      Assert.isTrue(ctx.getAmount() >= urc20Cap.getLot(), "Mined amount at least equal lot: " + urc20Cap.getLot());

      //avail to mine = max - total - burned
      var availableToMine = Math.subtractExact(urc20Cap.getMaxSupply(), Math.addExact(urc20Cap.getTotalSupply(), urc20Cap.getBurnedToken()));
      Assert.isTrue(ctx.getAmount() <= availableToMine, "Not enough frozen token to mine, maximum allowed: " + availableToMine);

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
