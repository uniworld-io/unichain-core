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
import org.unichain.protos.Contract.Urc721AddMinterContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc721AddMinterActuator extends AbstractActuator {

  public Urc721AddMinterActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc721AddMinterContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var minterAddr = ctx.getMinter().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();

      var accStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc721ContractStore();

      //create new account
      if (!accStore.has(minterAddr)) {
        dbManager.createDefaultAccount(minterAddr, Protocol.AccountType.Normal);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      //save relation
      var contract = contractStore.get(contractAddr);
      var currentMinter = contract.getMinter();
      dbManager.removeMinterContract(currentMinter, contractAddr);

      //load again then add minter
      contract = contractStore.get(contractAddr);
      contract.setMinter(ctx.getMinter());
      contractStore.put(contractAddr, contract);
      dbManager.addMinterContractRelation(contract);

      //charge fee
      chargeFee(ownerAddr, fee);
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
      Assert.isTrue(contract.is(Urc721AddMinterContract.class), "contract type error, expected type [Urc721AddMinterContract], real type[" + contract.getClass() + "]");

      var accStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc721ContractStore();

      val ctx = this.contract.unpack(Urc721AddMinterContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      var minterAddr = ctx.getMinter().toByteArray();

      Assert.isTrue(accStore.has(ownerAddr), "Owner account not exist");
      Assert.isTrue(Wallet.addressValid(minterAddr), "Minter address not active or not exists");
      Assert.isTrue(!Arrays.equals(minterAddr, ownerAddr), "Owner and minter must be not the same");
      Assert.isTrue(!Arrays.equals(dbManager.getBurnAddress(), minterAddr)
                      && (!accStore.has(minterAddr) || accStore.get(minterAddr).getType() != Protocol.AccountType.Contract),
              "Bad minter address: must be not burn, normal address");
      Assert.isTrue(contractStore.has(contractAddr), "Contract address not exist");
      var contractCap = contractStore.get(contractAddr);
      Assert.isTrue(Arrays.equals(contractCap.getOwner(), ownerAddr), "Not owner of contract");

      var fee = calcFee();
      if(!accStore.has(minterAddr))
      {
        fee = Math.addExact(dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract(), fee);
      }

      Assert.isTrue(accStore.get(ownerAddr).getBalance() >= fee, "Not enough Balance to cover transaction fee, require " + fee + "ginza");
      Assert.isTrue(!contractCap.hasMinter() || (!Arrays.equals(contractCap.getMinter(), minterAddr)), "Already minter");

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc721AddMinterContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
