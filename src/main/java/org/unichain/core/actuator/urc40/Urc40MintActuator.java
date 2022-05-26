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
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc40MintContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc40MintActuator extends AbstractActuator {

  public Urc40MintActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc40MintContract.class);
      var contractAddr = ctx.getAddress().toByteArray();
      var contractCap = dbManager.getUrc40ContractStore().get(contractAddr);
      contractCap.setTotalSupply(Math.addExact(contractCap.getTotalSupply(), ctx.getAmount()));
      contractCap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      contractCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      dbManager.getUrc40ContractStore().put(contractAddr, contractCap);

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      accountCapsule.addUrc40Token(contractAddr, ctx.getAmount());
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);

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
      Assert.isTrue(contract.is(Urc40MintContract.class), "Contract type error,expected type [Urc40MintContract], real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Urc40MintContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddr);
      Assert.notNull(ownerAccountCap, "Owner address not exist");

      Assert.isTrue(ownerAccountCap.getBalance() >= calcFee(), "Fee exceed balance");

      var contractAddr = ctx.getAddress().toByteArray();
      var contractAddrBase58 = Wallet.encode58Check(contractAddr);
      var contractCap = dbManager.getUrc40ContractStore().get(contractAddr);
      Assert.notNull(contractCap, "Contract not exist :" + contractAddrBase58);

      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));

      Assert.isTrue(Arrays.equals(ownerAddr, contractCap.getOwnerAddress().toByteArray()), "Mismatched Contract owner not allowed to mine");

      Assert.isTrue(ctx.getAmount() >= contractCap.getLot(), "Mined amount at least equal lot: " + contractCap.getLot());

      //avail to mine = max - total - burned
      var availableToMine = Math.subtractExact(contractCap.getMaxSupply(), Math.addExact(contractCap.getTotalSupply(), contractCap.getBurnedToken()));
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
    return contract.unpack(Urc40MintContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2UNW default
  }
}
