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

package org.unichain.core.actuator.urc721;

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
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc721ApproveContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc721ApproveActuator extends AbstractActuator {

  public Urc721ApproveActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc721ApproveContract.class);
      var owner = ctx.getOwnerAddress().toByteArray();
      var accountStore = dbManager.getAccountStore();
      var tokenStore = dbManager.getUrc721TokenStore();
      var contractAddr = ctx.getAddress().toByteArray();
      var tokenKey = ArrayUtils.addAll(contractAddr, ByteArray.fromLong(ctx.getTokenId()));
      var token = tokenStore.get(tokenKey);

      if(ctx.getApprove()){
        token.setApproval(ctx.getTo());
        tokenStore.put(tokenKey, token);

        dbManager.addApproveToken(tokenKey, ctx.getTo().toByteArray());

        var toAddr = ctx.getTo();
        if(!accountStore.has(toAddr.toByteArray())){
            var moreFee = dbManager.createNewAccount(toAddr);
            fee = Math.addExact(fee, moreFee);
        }
      }
      else {
        token.clearApproval();
        tokenStore.put(tokenKey, token);
        dbManager.disapproveToken(tokenKey, ctx.getTo().toByteArray());
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
      Assert.isTrue(contract.is(Urc721ApproveContract.class), "Contract type error,expected type [Urc721ApproveContract], real type[" + contract.getClass() + "]");
      var fee = calcFee();
      var accountStore = dbManager.getAccountStore();
      var tokenStore = dbManager.getUrc721TokenStore();
      val ctx = this.contract.unpack(Urc721ApproveContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(accountStore.has(ownerAddr), "Owner account not exist");

      var toAddr = ctx.getTo().toByteArray();
      Assert.isTrue(Wallet.addressValid(toAddr), "Target address not active or not exists");
      if(!accountStore.has(toAddr)){
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee,"Not enough Balance to cover transaction fee, require " + fee + "ginza");

      var tokenId = ArrayUtils.addAll(ctx.getAddress().toByteArray(), ByteArray.fromLong(ctx.getTokenId()));
      Assert.isTrue(tokenStore.has(tokenId), "Not found token");

      var token = tokenStore.get(tokenId);
      if(ctx.getApprove()){
        if(token.hasApproval()){
          //approve: just override exception: already approved
          Assert.isTrue(!Arrays.equals(ctx.getTo().toByteArray(), token.getApproval()), "The address has already been approver");
        }
      }
      else {
        //disapprove
        Assert.isTrue(token.hasApproval() && Arrays.equals(ctx.getTo().toByteArray(), token.getApproval()), "Unmatched approval address");
      }

      Assert.isTrue(Arrays.equals(token.getOwner(), ownerAddr), "Not owner of token");
      Assert.isTrue(!Arrays.equals(toAddr, ownerAddr), "Owner and approver cannot be the same");

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc721ApproveContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
