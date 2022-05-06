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
import org.unichain.common.utils.AddressUtil;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.capsule.*;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
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
      var walletInfo = AddressUtil.generateAddress(dbManager);
      Assert.notNull(walletInfo, "failed to generate wallet address!!!");
      var tokenAddr = Hex.decodeHex(walletInfo.addressHex);

      dbManager.saveNftTemplate(new NftTemplateCapsule(ctx, dbManager.getHeadBlockTimeStamp(), 0, tokenAddr));

      //indexing contract addr
      var symbol = ctx.getContract().toUpperCase();
      var tokenKey = symbol.getBytes();
      var index1Store = dbManager.getNftAddrSymbolIndexStore();
      var index2Store = dbManager.getNftSymbolAddrIndexStore();
      var index1 = new TokenAddressSymbolIndexCapsule(Protocol.TokenAddressSymbolIndex.newBuilder().build());
      var index2 = new TokenSymbolAddressIndexCapsule(Protocol.TokenSymbolAddressIndex.newBuilder().build());
      index1.setSymbol(symbol);
      index1Store.put(tokenAddr, index1);
      index2.setAddress(tokenAddr);
      index2Store.put(tokenKey, index2);

      //register new account with type assetissue
      var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
      var tokenAccount = new AccountCapsule(ByteString.copyFrom(tokenAddr), Protocol.AccountType.AssetIssue, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
      dbManager.getAccountStore().put(tokenAddr, tokenAccount);

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

      var contract = Util.stringAsBytesUppercase(ctx.getContract());
      var name = ctx.getName().getBytes();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = accountStore.get(ownerAddr);

      Assert.isTrue(TransactionUtil.validContract(contract), "Invalid template contract");
      Assert.isTrue(TransactionUtil.validNftName(name), "Invalid template name");
      Assert.isTrue(!dbManager.getNftTemplateStore().has(contract), "Contract has existed");
      Assert.isTrue(accountStore.has(ownerAddr), "Owner account[" + StringUtil.createReadableString(ownerAddr) + "] not exists");
      Assert.isTrue(!TransactionUtil.validGenericsAddress(ownerAddr), "Owner is generics address");

      if (ctx.hasField(NFT_CREATE_TEMPLATE_FIELD_MINTER)){
        var minterAddr = ctx.getMinter().toByteArray();
        Assert.notNull(accountStore.get(minterAddr), "Minter account[" + StringUtil.createReadableString(minterAddr) + "] not exists or not active");
        Assert.isTrue(!Arrays.equals(minterAddr, ownerAddr), "Owner and minter must be not the same");
        Assert.isTrue(!TransactionUtil.validGenericsAddress(minterAddr), "Minter is generics address");

      }
      Assert.isTrue(ctx.getTotalSupply() > 0, "TotalSupply must greater than 0");
      Assert.isTrue(ownerAccountCap.getBalance() >= calcFee(), "Not enough balance, require 500 UNW");

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


