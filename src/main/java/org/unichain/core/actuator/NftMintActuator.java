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
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.NftTokenCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

//@todo later
@Slf4j(topic = "actuator")
public class NftMintActuator extends AbstractActuator {

  NftMintActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  //@todo later
  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Contract.MintNftTokenContract.class);
      var accountStore = dbManager.getAccountStore();
      var templateStore = dbManager.getNftTemplateStore();
      var nftStore = dbManager.getNftTokenStore();
      var nftAccRelationStore = dbManager.getNftAccountTokenStore();
      var templateKey =  Util.stringAsBytesUppercase(ctx.getSymbol());
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      var ownerAccountCap = accountStore.get(ownerAddr);
      var template = templateStore.get(templateKey);

      var tokenId = Math.incrementExact(template.getTokenIndex());
      Protocol.NftToken nftToken = Protocol.NftToken.newBuilder()
              .setId(tokenId)
              .setLastOperation(dbManager.getHeadBlockTimeStamp())
              .clearApproval()
              .setOwnerAddress(ctx.getToAddress())
              .setMetadata(ctx.getMetadata())
              .setTemplateId(ByteString.copyFrom(templateKey))
              .setExpireTime(ctx.getAvailableTime())
              .setUri(ctx.getUri())
              .build();
      var nftTokenCap = new NftTokenCapsule(nftToken);
      nftStore.put(nftTokenCap.getKey(), nftTokenCap);

      //update template index
      template.setTokenIndex(tokenId);
      templateStore.put(templateKey, template);

      //create new account
      if (!accountStore.has(toAddr)) {
        var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        var toAccountCap = new AccountCapsule(ByteString.copyFrom(toAddr), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddr, toAccountCap);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      //indexing account-token
      nftAccRelationStore.save(toAddr, nftTokenCap);

      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  //@todo later
  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(Contract.MintNftTokenContract.class), "contract type error,expected type [CreateNftTemplateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Contract.MintNftTokenContract.class);
      var accountStore = dbManager.getAccountStore();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddr), "Invalid ownerAddress");
      var ownerAccountCap = accountStore.get(ownerAddr);
      Assert.notNull(ownerAccountCap, "Owner account[" + StringUtil.createReadableString(ownerAddr) + "] not exists");

      var symbol = Util.stringAsBytesUppercase(ctx.getSymbol());
      Assert.isTrue(!ctx.getSymbol().isEmpty() && TransactionUtil.validTokenName(symbol), "Invalid template symbol");
      Assert.isTrue(dbManager.getNftTemplateStore().has(symbol), "NftTemplate not existed");
      var template = dbManager.getNftTemplateStore().get(symbol);
      Assert.isTrue(Arrays.equals(ownerAddr, template.getOwner()) || (template.hasMinter() && Arrays.equals(ownerAddr, template.getMinter())), "Only owner or minter allowed to mint NFT token");
      Assert.isTrue(template.getTokenIndex() < template.getTotalSupply(), "All NFT token mint!");

      var toAccountCap = dbManager.getAccountStore().get(ctx.getToAddress().toByteArray());
      var fee = calcFee();
      if(Objects.isNull(toAccountCap)){
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      Assert.isTrue(ownerAccountCap.getBalance() >= fee, "Owner not enough balance to create new account fee, require at least "+ fee + "ginza");

      Assert.isTrue(TransactionUtil.validUrl(ByteString.copyFrom(ctx.getUri().getBytes()).toByteArray()), "invalid uri");
      Assert.isTrue(TransactionUtil.validJsonString(ByteString.copyFrom(ctx.getMetadata().getBytes()).toByteArray()), "invalid metadata, should be json format");

      Assert.isTrue(ctx.getAvailableTime() >= dbManager.getHeadBlockTimeStamp(), "available time should pass current block timestamp");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Contract.MintNftTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
