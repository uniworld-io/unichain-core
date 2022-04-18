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
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.ApproveForAllNftTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class NftApproveForAllTokenActuator extends AbstractActuator {

  NftApproveForAllTokenActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(ApproveForAllNftTokenContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      var accountStore = dbManager.getAccountStore();
      var relationStore = dbManager.getNftAccountTokenStore();
      if(ctx.getApprove())
        relationStore.approveForAll(ownerAddr, toAddr);
      else
        relationStore.disApproveForAll(ownerAddr, toAddr);

      //create new account
      if (!accountStore.has(toAddr)) {
        fee = Math.addExact(fee, dbManager.createNewAccount(ctx.getToAddress()));
      }

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
      Assert.isTrue(contract.is(ApproveForAllNftTokenContract.class), "contract type error,expected type [ApproveForAllNftTokenContract],real type[" + contract.getClass() + "]");
      var fee = calcFee();
      val ctx = this.contract.unpack(ApproveForAllNftTokenContract.class);
      var accountStore = dbManager.getAccountStore();
      var relationStore = dbManager.getNftAccountTokenStore();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();

      Assert.isTrue(accountStore.has(ownerAddr), "Owner account not exist");
      Assert.isTrue(Wallet.addressValid(toAddr), "Target address not exists or not active");
      Assert.isTrue(relationStore.has(ownerAddr) && relationStore.get(ownerAddr).getTotal() > 0, "Not found any token");
      var relation = relationStore.get(ownerAddr);

      if(ctx.getApprove()){
        if(relation.hasApprovalForAll()) {
          Assert.isTrue(!Arrays.equals(toAddr, relation.getApprovedForAll()), "The address has already been approver all");
        }
      } else {
        Assert.isTrue(relation.hasApprovalForAll() && Arrays.equals(toAddr, relation.getApprovedForAll()), "Not approved yet");
      }

      if(!accountStore.has(toAddr)){
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough balance to cover transaction fee, require " + fee + "ginza");
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
    return contract.unpack(ApproveForAllNftTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
