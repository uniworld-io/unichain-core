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
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.TransferNftTokenContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

//@todo later
@Slf4j(topic = "actuator")
public class TransferNftTokenActuator extends AbstractActuator {

  TransferNftTokenActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(TransferNftTokenContract.class);
      var accountStore = dbManager.getAccountStore();
      var tokenStore = dbManager.getNftTokenStore();
      var relationStore = dbManager.getNftAccountTokenStore();
      var ownerAddr = ctx.getOwner().toByteArray();
      var toAddr = ctx.getToAddress();
      var toAddrBytes = toAddr.toByteArray();
      var tokenId = ArrayUtils.addAll(Util.stringAsBytesUppercase(ctx.getNftTemplate()), ByteArray.fromLong(ctx.getTokenId()));

      //create new acc if not exist
      if (accountStore.has(toAddrBytes)) {
        var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        var toAccountCap = new AccountCapsule(ByteString.copyFrom(toAddrBytes), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddrBytes, toAccountCap);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      var token = tokenStore.get(tokenId);
      var oldTokenOwner = token.getOwner();
      var oldTokenApprovalForAllCap = relationStore.get(ownerAddr);

      //update token
      token.setOwner(toAddr);
      token.setLastOperation(dbManager.getHeadBlockTimeStamp());
      tokenStore.put(tokenId, token);

      //update old relation
      var isApproveForAll = oldTokenApprovalForAllCap.hasApprovalForAll() && Arrays.equals(oldTokenApprovalForAllCap.getApprovalForAll(), ownerAddr);
      var isApproval = token.hasApproval() && Arrays.equals(token.getApproval(), ownerAddr);
      relationStore.remove(oldTokenOwner, tokenId, isApproval, isApproveForAll);

      //add new relation
      relationStore.save(toAddr.toByteArray(), tokenId, false);

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
      Assert.isTrue(contract.is(TransferNftTokenContract.class), "contract type error,expected type [TransferNftTokenContract],real type[" + contract.getClass() + "]");
      var fee = calcFee();
      val ctx = this.contract.unpack(TransferNftTokenContract.class);
      var accountStore = dbManager.getAccountStore();
      var tokenStore = dbManager.getNftTokenStore();
      var relationStore = dbManager.getNftAccountTokenStore();
      var ownerAddr = ctx.getOwner().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      var tokenId = ArrayUtils.addAll(Util.stringAsBytesUppercase(ctx.getNftTemplate()), ByteArray.fromLong(ctx.getTokenId()));

      Assert.isTrue(Wallet.addressValid(toAddr), "Invalid target address");
      Assert.isTrue(accountStore.has(ownerAddr), "Owner, approval or approval for all not exist");
      Assert.isTrue(tokenStore.has(tokenId), "NFT token not exist");
      var nft = tokenStore.get(tokenId);
      var nftOwner = nft.getOwner();
      var relation = relationStore.get(nftOwner);

      Assert.isTrue(Arrays.equals(ownerAddr, nftOwner)
              || (relation.hasApprovalForAll() && Arrays.equals(ownerAddr, relation.getApprovalForAll()))
              || (nft.hasApproval() && Arrays.equals(ownerAddr, nft.getApproval())), "Not allowed to burn NFT token");

      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough fee");

      if(accountStore.has(toAddr))
      {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough balance to cover fee");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(TransferNftTokenContract.class).getOwner();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;  }
}
