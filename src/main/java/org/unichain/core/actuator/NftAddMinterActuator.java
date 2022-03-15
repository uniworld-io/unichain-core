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

package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.AddNftMinterContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class NftAddMinterActuator extends AbstractActuator {

  NftAddMinterActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(AddNftMinterContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var minterAddr = ctx.getMinter().toByteArray();
      var accStore = dbManager.getAccountStore();
      var symbol = Util.stringAsBytesUppercase(ctx.getSymbol());

      //create new account
      if (!accStore.has(minterAddr)) {
        fee = Math.addExact(fee, dbManager.createNewAccount(ByteString.copyFrom(minterAddr)));
      }

      //save relation
      var templateStore = dbManager.getNftTemplateStore();
      var template = templateStore.get(symbol);
      template.setMinter(ctx.getMinter());
      templateStore.put(symbol, template);

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
      Assert.isTrue(contract.is(AddNftMinterContract.class), "contract type error, expected type [AddNftMinterContract], real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(AddNftMinterContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var minterAddr = ctx.getMinter().toByteArray();
      var symbol = Util.stringAsBytesUppercase(ctx.getSymbol());
      var accStore = dbManager.getAccountStore();
      var templateStore = dbManager.getNftTemplateStore();

      Assert.isTrue(accStore.has(ownerAddr), "Owner account not exist");
      Assert.isTrue(Wallet.addressValid(minterAddr), "Invalid minter address");
      Assert.isTrue(!Arrays.equals(minterAddr, ownerAddr), "Owner and minter must be not equal");
      Assert.isTrue(templateStore.has(symbol), "Symbol not exist");
      var template = templateStore.get(symbol);
      Assert.isTrue(Arrays.equals(template.getOwner(), ownerAddr), "Not owner of NFT template");

      //check fee
      var fee = calcFee();
      if(!accStore.has(minterAddr))
      {
        fee = Math.addExact(dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract(), fee);
      }
      Assert.isTrue(accStore.get(ownerAddr).getBalance() >= fee, "not enough balance, require at-least: " + fee +" ginza");
      Assert.isTrue(!template.hasMinter() || (!Arrays.equals(template.getMinter(), ctx.getMinter().toByteArray())), "already minter");
      Assert.isTrue(!TransactionUtil.validGenericsAddress(minterAddr), "Minter is generics address");

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AddNftMinterContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
