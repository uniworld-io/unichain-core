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
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.AddNftMinterContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

//@todo implement
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
      var ownerAddr = ctx.getOwner().toByteArray();
      var minterAddr = ctx.getMinter().toByteArray();
      var accTemplateRelStore = dbManager.getNftAccountTemplateStore();
      var accStore = dbManager.getAccountStore();
      var templateId = Util.stringAsBytesUppercase(ctx.getNftTemplate());

      //create new account
      if (accStore.has(minterAddr)) {
        var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        var minterAcc = new AccountCapsule(ByteString.copyFrom(minterAddr), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        accStore.put(minterAddr, minterAcc);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      //save relation
      accTemplateRelStore.save(minterAddr, templateId, true);

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
      var ownerAddr = ctx.getOwner().toByteArray();
      var minterAddr = ctx.getMinter().toByteArray();
      var templateId = Util.stringAsBytesUppercase(ctx.getNftTemplate());
      var accStore = dbManager.getAccountStore();
      var templateStore = dbManager.getNftTemplateStore();
      var accTemplateStore = dbManager.getNftAccountTemplateStore();
      var template = templateStore.get(templateId);

      Assert.isTrue(accStore.has(ownerAddr), "Owner account not exist");
      Assert.isTrue(templateStore.has(templateId), "Template not exist");
      Assert.isTrue(Wallet.addressValid(minterAddr), "Invalid minter address");
      Assert.isTrue(Arrays.equals(minterAddr, ownerAddr), "Owner and minter must be not equal");
      Assert.isTrue(Arrays.equals(template.getOwner(), ownerAddr), "Not owner of NFT template");

      //check fee
      var fee = calcFee();
      if(!accStore.has(minterAddr))
      {
        fee = Math.addExact(dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract(), fee);
      }
      Assert.isTrue(accStore.get(ownerAddr).getBalance() >= fee, "not enough balance, require at-least: " + fee +" ginza");

      //already minter or template ?
      if(!accTemplateStore.has(minterAddr))
        return true;

      var relationKey = ArrayUtils.addAll(minterAddr, templateId);
      if(accTemplateStore.has(relationKey))
        return false;

      var headRelation = accTemplateStore.get(minterAddr);
      return !Arrays.equals(headRelation.getTemplateId().toByteArray(), templateId);
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AddNftMinterContract.class).getOwner();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
