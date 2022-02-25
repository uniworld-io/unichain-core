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
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.NftTemplateCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.CreateNftTemplateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.services.http.utils.Util.NFT_CREATE_TEMPLATE_FIELD_MINTER;

@Slf4j(topic = "actuator")
public class NftCreateTemplateActuator extends AbstractActuator {

  NftCreateTemplateActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(CreateNftTemplateContract.class);
      var symbol = Util.stringAsBytesUppercase(ctx.getSymbol());
      var owner = ctx.getOwner().toByteArray();
      var total = ctx.getTotalSupply();
      var lastOperation = dbManager.getHeadBlockTimeStamp();
      var templateCap = new NftTemplateCapsule(ctx, lastOperation);
      dbManager.getNftTemplateStore().put(symbol, templateCap);
      dbManager.getNftAccountTemplateStore().save(owner, templateCap, false);

      chargeFee(owner, fee);
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
      Assert.isTrue(contract.is(CreateNftTemplateContract.class), "contract type error,expected type [CreateNftTemplateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(CreateNftTemplateContract.class);
      var accountStore = dbManager.getAccountStore();

      var symbol = Util.stringAsBytesUppercase(ctx.getSymbol());
      var name = ctx.getName().getBytes();
      var ownerAddr = ctx.getOwner().toByteArray();

      Assert.isTrue(!ctx.getSymbol().isEmpty() && TransactionUtil.validTokenName(symbol), "Invalid template symbol");
      Assert.isTrue(!ctx.getName().isEmpty() && TransactionUtil.validTokenName(name), "Invalid template symbol");
      Assert.isTrue(!dbManager.getNftTemplateStore().has(symbol), "NftTemplate has existed");

      Assert.isTrue(Wallet.addressValid(ownerAddr), "Invalid ownerAddress");
      var accountCap = accountStore.get(ownerAddr);
      Assert.notNull(accountCap, "Owner account[" + StringUtil.createReadableString(ownerAddr) + "] not exists");

      if (ctx.hasField(NFT_CREATE_TEMPLATE_FIELD_MINTER)){
        var minterAddr = ctx.getMinter().toByteArray();
        Assert.notNull(accountStore.get(minterAddr), "Minter account[" + StringUtil.createReadableString(minterAddr) + "] not exists");
        Assert.notNull(!Arrays.equals(minterAddr, ownerAddr), "Owner and minter must be not the same");

      }
      Assert.isTrue(ctx.getTotalSupply() > 0, "TotalSupply must greater than 0");

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(CreateNftTemplateContract.class).getOwner();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 UNW default
  }
}
