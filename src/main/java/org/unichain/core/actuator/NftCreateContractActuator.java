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
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.NftCreateEvent;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.*;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.CreateNftTemplateContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.services.http.utils.Util.NFT_CREATE_TEMPLATE_FIELD_MINTER;

@Slf4j(topic = "actuator")
public class NftCreateContractActuator extends AbstractActuator {

  NftCreateContractActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(CreateNftTemplateContract.class);
      var owner = ctx.getOwnerAddress().toByteArray();
      var tokenAddr = ctx.getAddress().toByteArray();

      dbManager.saveNftTemplate(new NftTemplateCapsule(ctx, dbManager.getHeadBlockTimeStamp(), 0));

      //register new account with type assetissue
      var defaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
      var tokenAccount = new AccountCapsule(ByteString.copyFrom(tokenAddr), Protocol.AccountType.AssetIssue, dbManager.getHeadBlockTimeStamp(), defaultPermission, dbManager);
      dbManager.getAccountStore().put(tokenAddr, tokenAccount);

      chargeFee(owner, fee);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);

      //emit event
      var event = NativeContractEvent.builder()
              .topic("NftCreate")
              .rawData(
                      NftCreateEvent.builder()
                              .owner_address(Hex.encodeHexString(ctx.getOwnerAddress().toByteArray()))
                              .address(Hex.encodeHexString(ctx.getAddress().toByteArray()))
                              .symbol(ctx.getSymbol())
                              .name(ctx.getName())
                              .total_supply(ctx.getTotalSupply())
                              .minter(Hex.encodeHexString(ctx.getMinter().toByteArray()))
                              .build())
              .build();
      emitEvent(event, ret);
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
      Assert.isTrue(contract.is(CreateNftTemplateContract.class), "contract type error, expected type [CreateNftTemplateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(CreateNftTemplateContract.class);
      var accountStore = dbManager.getAccountStore();

      var addr = ctx.getAddress().toByteArray();
      var symbol = ctx.getSymbol();
      var name = ctx.getName();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = accountStore.get(ownerAddr);

      Assert.isTrue(Wallet.addressValid(addr), "Invalid contract address");
      Assert.isTrue(TransactionUtil.validNftName(symbol), "Invalid contract symbol");
      Assert.isTrue(TransactionUtil.validNftName(name), "Invalid contract name");

      Assert.isTrue(!dbManager.getNftTemplateStore().has(addr), "Contract address has existed");

      Assert.isTrue(accountStore.has(ownerAddr), "Owner account[" + StringUtil.createReadableString(ownerAddr) + "] not exists");
      Assert.isTrue(!TransactionUtil.isGenesisAddress(ownerAddr), "Owner is genesis address");

      if (ctx.hasField(NFT_CREATE_TEMPLATE_FIELD_MINTER)){
        var minterAddr = ctx.getMinter().toByteArray();
        Assert.notNull(accountStore.get(minterAddr), "Minter account[" + StringUtil.createReadableString(minterAddr) + "] not exists or not active");
        Assert.isTrue(!Arrays.equals(minterAddr, ownerAddr), "Owner and minter must be not the same");
        Assert.isTrue(!TransactionUtil.isGenesisAddress(minterAddr), "Minter is genesis address");
      }
      Assert.isTrue(ctx.getTotalSupply() > 0, "TotalSupply must greater than 0");
      Assert.isTrue(ownerAccountCap.getBalance() >= calcFee(), "Not enough balance, require 500 UNW");

      //validate address
      Assert.isTrue(!dbManager.getAccountStore().has(ctx.getAddress().toByteArray()), "contract address exist");

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(CreateNftTemplateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getNftIssueFee();//500 UNW default
  }
}


