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
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.RemoveNftMinterContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class NftRemoveMinterActuator extends AbstractActuator {

  NftRemoveMinterActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(RemoveNftMinterContract.class);
      var ownerAddress = ctx.getOwner().toByteArray();
      var templateId = Util.stringAsBytesUppercase(ctx.getSymbol());
      var templateStore = dbManager.getNftTemplateStore();

      var templateCap = templateStore.get(templateId);
      templateCap.clearMinter();
      templateStore.put(templateId, templateCap);

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
      Assert.isTrue(contract.is(RemoveNftMinterContract.class), "contract type error,expected type [RemoveNftMinterContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(RemoveNftMinterContract.class);
      var ownerAddr = ctx.getOwner().toByteArray();
      var templateId = Util.stringAsBytesUppercase(ctx.getSymbol());
      var accountStore = dbManager.getAccountStore();

      Assert.isTrue(accountStore.has(ownerAddr), "Owner account not exist");

      var templateCap = dbManager.getNftTemplateStore().get(templateId);
      Assert.notNull(templateCap, "NFT template not found");
      Assert.isTrue(Arrays.equals(ownerAddr, templateCap.getOwner()), "Not owner of NFT template");
      Assert.isTrue(templateCap.hasMinter(), "Minter not set");
      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= calcFee(), "Not enough fee");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(RemoveNftMinterContract.class).getOwner();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
