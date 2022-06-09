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

package org.unichain.core.actuator.urc721;

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
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc721RemoveMinterContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc721RemoveMinterActuator extends AbstractActuator {

  public Urc721RemoveMinterActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc721RemoveMinterContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      var contractCap = dbManager.getUrc721ContractStore().get(contractAddr);

      dbManager.removeMinterContract(contractCap.getMinter(), contractAddr);

      chargeFee(ownerAddress, fee);
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
      Assert.isTrue(contract.is(Urc721RemoveMinterContract.class), "contract type error,expected type [Urc721RemoveMinterContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Urc721RemoveMinterContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddr) && Wallet.addressValid(contractAddr), "Invalid owner|contract address");
      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc721ContractStore();

      Assert.isTrue(accountStore.has(ownerAddr) && contractStore.has(contractAddr), "Owner account | contract not exist");

      var contractCap = contractStore.get(contractAddr);
      Assert.isTrue(Arrays.equals(ownerAddr, contractCap.getOwner()), "Not owner of contract");
      Assert.isTrue(contractCap.hasMinter(), "Minter not set");
      long fee = calcFee();
      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough Balance to cover transaction fee, require " + fee + "ginza");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc721RemoveMinterContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
