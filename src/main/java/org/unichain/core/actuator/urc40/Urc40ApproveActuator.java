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
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc40.Urc40SpenderCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc40ApproveContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

//@todo urc40 should charge token only, not unw ???
@Slf4j(topic = "actuator")
public class Urc40ApproveActuator extends AbstractActuator {

  public Urc40ApproveActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var spenderStore = dbManager.getUrc40SpenderStore();
      var ctx = contract.unpack(Urc40ApproveContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var spenderAddr = ctx.getSpender().toByteArray();
      var urc40Addr = ctx.getAddress().toByteArray();
      var limit = ctx.getAmount();

      var spenderKey = Urc40SpenderCapsule.genKey(spenderAddr, urc40Addr);
      if(!spenderStore.has(spenderKey)){
        var quota = new Urc40SpenderCapsule(spenderAddr, urc40Addr, ownerAddr, limit);
        spenderStore.put(spenderKey, quota);
      }
      else {
        var quota = spenderStore.get(spenderKey);
        quota.setQuotaTo(ownerAddr, limit);
        spenderStore.put(spenderKey, quota);
      }

      dbManager.burnFee(fee);
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
      Assert.isTrue(contract.is(Urc40ApproveContract.class), "contract type error,expected type [Urc40ApproveContract],real type[" + contract.getClass() + "]");

      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc40ContractStore();
      var spenderStore = dbManager.getUrc40SpenderStore();
      val ctx = this.contract.unpack(Urc40ApproveContract.class);

      var owner = ctx.getOwnerAddress().toByteArray();
      var spender = ctx.getSpender().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      var limit = ctx.getAmount();

      Assert.isTrue(Wallet.addressValid(spender) && Wallet.addressValid(contractAddr) && Wallet.addressValid(owner), "Bad owner|contract|spender address");
      Assert.isTrue(!Arrays.equals(owner, spender), "Spender must not be owner");
      Assert.isTrue(accountStore.has(owner) && accountStore.has(spender) && contractStore.has(contractAddr) , "Unrecognized owner|spender|contract address");

      var contractCap = contractStore.get(contractAddr);
      var spenderKey = Urc40SpenderCapsule.genKey(spender, contractAddr);
      if(spenderStore.has(spenderKey))
        Assert.isTrue(spenderStore.get(spenderKey).checkSetQuotaTo(owner, limit), "Invalid limit value");
      else {
        Assert.isTrue(contractCap.getTotalSupply() >= limit, "Spender limit reached out contract total supply!");
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
    return contract.unpack(Urc40ApproveContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0L;
  }
}
