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
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.ApproveNftTokenContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ApproveNftTokenActuator extends AbstractActuator {

  ApproveNftTokenActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(ApproveNftTokenContract.class);
      var owner = ctx.getOwner().toByteArray();
      var accountStore = dbManager.getAccountStore();
      var nftTokenStore = dbManager.getNftTokenStore();
      var relationStore = dbManager.getNftAccountTokenStore();
      var templateId = Util.stringAsBytesUppercase(ctx.getNftTemplate());
      var tokenId = ArrayUtils.addAll(templateId, ByteArray.fromLong(ctx.getTokenId()));
      var nftToken = nftTokenStore.get(tokenId);

      if(ctx.getApprove()){
        if(nftToken.hasApproval()){
          //remove old approval indexing
          relationStore.remove(nftToken.getApproval(), nftToken.getKey(), true, false);
        }
        nftToken.setApproval(ctx.getToAddress());
        nftTokenStore.put(tokenId, nftToken);
        relationStore.save(ctx.getToAddress().toByteArray(), nftToken.getKey(), true);

        //create new account
        var toAddr = ctx.getToAddress();
        if(!accountStore.has(toAddr.toByteArray())){
            var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
            var toAccountCap = new AccountCapsule(toAddr, Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
            dbManager.getAccountStore().put(toAddr.toByteArray(), toAccountCap);
            fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }
      else {
        nftToken.clearApproval();
        nftTokenStore.put(tokenId, nftToken);
        relationStore.remove(ctx.getToAddress().toByteArray(), tokenId, true, false);
      }

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
      Assert.isTrue(contract.is(ApproveNftTokenContract.class), "contract type error,expected type [ApproveNftTokenContract],real type[" + contract.getClass() + "]");
      var fee = calcFee();
      var accountStore = dbManager.getAccountStore();
      var nftTokenStore = dbManager.getNftTokenStore();
      val ctx = this.contract.unpack(ApproveNftTokenContract.class);
      var ownerAddr = ctx.getOwner().toByteArray();
      Assert.isTrue(accountStore.has(ownerAddr), "owner account not exist");

      var toAddr = ctx.getToAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(toAddr), "Invalid toAddress");
      if(!accountStore.has(toAddr)){
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee,"not enough fee");

      var templateId = Util.stringAsBytesUppercase(ctx.getNftTemplate());
      var tokenId = ArrayUtils.addAll(templateId, ByteArray.fromLong(ctx.getTokenId()));
      Assert.isTrue(nftTokenStore.has(tokenId), "not found ntf token");

      var nftToken = nftTokenStore.get(tokenId);
      if(ctx.getApprove()){
        if(nftToken.hasApproval()){
          //approve: just override exception: already approved
          Assert.isTrue(!Arrays.equals(ctx.getToAddress().toByteArray(), nftToken.getApproval()), "Already approved");
        }
      }
      else {
        //disapprove
        Assert.isTrue(nftToken.hasApproval() && Arrays.equals(ctx.getToAddress().toByteArray(), nftToken.getApproval()), "Unmatched approval address");
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
    return contract.unpack(ApproveNftTokenContract.class).getOwner();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
