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

import static org.unichain.core.services.http.utils.Util.NFT_MINT_FIELD_TOKEN_ID;

@Slf4j(topic = "actuator")
public class NftMintTokenActuator extends AbstractActuator {

  NftMintTokenActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Contract.MintNftTokenContract.class);
      var accountStore = dbManager.getAccountStore();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();


      //create new account
      if (!accountStore.has(toAddr)) {
        fee = Math.addExact(fee, dbManager.createNewAccount(ByteString.copyFrom(toAddr)));
      }

      //save token
      var nftTokenBuilder = Protocol.NftToken.newBuilder()
              .setContract(ctx.getContract().toUpperCase())
              .setUri(ctx.getUri())
              .clearApproval()
              .setOwnerAddress(ctx.getToAddress())
              .setLastOperation(dbManager.getHeadBlockTimeStamp());

      if(ctx.hasField(NFT_MINT_FIELD_TOKEN_ID))
      {
        //use preset tokenId
        nftTokenBuilder.setId(ctx.getTokenId());
      }
      else
      {
        //allocate tokenId
        val contractStore = dbManager.getNftTemplateStore();
        val contractKey =  Util.stringAsBytesUppercase(ctx.getContract());
        var nftContract = contractStore.get(contractKey);
        var tokenIndex = allocateTokenId(nftContract);
        nftContract.setTokenIndex(tokenIndex);
        contractStore.put(contractKey, nftContract);
        nftTokenBuilder.setId(tokenIndex);
      }

      dbManager.saveNftToken(new NftTokenCapsule(nftTokenBuilder.build()));

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

  /**
   * Safely allocate tokenId:
   * - advance token index
   * - make sure token index not allocated
   */
  private long allocateTokenId(final NftTemplateCapsule template) {
    val tokenStore = dbManager.getNftTokenStore();
    var nextId = Math.incrementExact(template.getTokenIndex());
    while (true){
      var key = NftTokenCapsule.genTokenKey(template.getContract(), nextId);
      if(!tokenStore.has(key)){
        return nextId;
      }
      else{
        nextId++;
        continue;
      }
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(Contract.MintNftTokenContract.class), "contract type error,expected type [MintNftTokenContract],real type[" + contract.getClass() + "]");
      var fee = calcFee();
      val ctx = this.contract.unpack(Contract.MintNftTokenContract.class);
      var accountStore = dbManager.getAccountStore();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCap = accountStore.get(ownerAddr);
      Assert.notNull(ownerAccountCap, "Owner account[" + StringUtil.createReadableString(ownerAddr) + "] not exists");

      var toAddr = ctx.getToAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(toAddr), "Invalid toAddress");
      var toAccountCap = dbManager.getAccountStore().get(toAddr);
      if(Objects.isNull(toAccountCap)){
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      } else {
         Assert.isTrue(!Arrays.equals(toAddr, ownerAddr), "Mint to itself not allowed!");
      }

      var contract = Util.stringAsBytesUppercase(ctx.getContract());
      Assert.isTrue(dbManager.getNftTemplateStore().has(contract), "Contract symbol not existed!");
      var templateCap = dbManager.getNftTemplateStore().get(contract);
      Assert.isTrue(Arrays.equals(ownerAddr, templateCap.getOwner()) || (templateCap.hasMinter() && Arrays.equals(ownerAddr, templateCap.getMinter())), "Only owner or minter allowed to mint NFT token");
      Assert.isTrue(!(Arrays.equals(toAddr, templateCap.getOwner()) || Arrays.equals(toAddr, templateCap.getMinter())), "Can not create token for minter or owner!");
      Assert.isTrue(templateCap.getTokenIndex() < templateCap.getTotalSupply(), "Over slot NFT token mint!");
      Assert.isTrue(ownerAccountCap.getBalance() >= fee, "Not enough balance to cover transaction fee, require "+ fee + "ginza");
      Assert.isTrue(TransactionUtil.validHttpURI(ctx.getUri()), "Invalid uri");

      //make sure tokenId not allocated yet!
      if(ctx.hasField(NFT_MINT_FIELD_TOKEN_ID)){
        val tokenKey = NftTokenCapsule.genTokenKey(ctx.getContract(), ctx.getTokenId());
        Assert.isTrue(!dbManager.getNftTokenStore().has(tokenKey), "TokenId allocated: " + ctx.getContract() + "_" + ctx.getTokenId());
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
    return contract.unpack(Contract.MintNftTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
