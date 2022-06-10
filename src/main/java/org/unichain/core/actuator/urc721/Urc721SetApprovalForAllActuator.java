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
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc721SetApprovalForAllContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class Urc721SetApprovalForAllActuator extends AbstractActuator {

  public Urc721SetApprovalForAllActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc721SetApprovalForAllContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();

      var accountStore = dbManager.getAccountStore();
      var summaryStore = dbManager.getUrc721AccountTokenRelationStore();

      //create new account
      if (!accountStore.has(toAddr)) {
        dbManager.createDefaultAccount(toAddr, Protocol.AccountType.Normal);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      //approve or dis-approve
      if(ctx.getApprove())
        summaryStore.approveForAll(ownerAddr, toAddr, contractAddr);
      else
        summaryStore.disApproveForAll(ownerAddr, toAddr, contractAddr);

      //charge fee
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
      Assert.isTrue(contract.is(Urc721SetApprovalForAllContract.class), "contract type error,expected type [Urc721SetApprovalForAllContract],real type[" + contract.getClass() + "]");
      var fee = calcFee();
      val ctx = this.contract.unpack(Urc721SetApprovalForAllContract.class);

      var accountStore = dbManager.getAccountStore();
      var summaryStore = dbManager.getUrc721AccountTokenRelationStore();
      var contractStore = dbManager.getUrc721ContractStore();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddr)
              && Wallet.addressValid(contractAddr)
              && Wallet.addressValid(toAddr),
              "Invalid owner|contract|to address");

      var contractAddrBase58 = Wallet.encode58Check(contractAddr);
      var toAddrBase58 = Wallet.encode58Check(toAddr);

      Assert.isTrue(!Arrays.equals(toAddr, ownerAddr), "Owner and operator cannot be the same");
      Assert.isTrue(accountStore.has(ownerAddr) && contractStore.has(contractAddr), "Owner| urc721 contract not exist");

      //check has token to approve for all
      var summary = summaryStore.get(ownerAddr);
      Assert.isTrue(Objects.nonNull(summary) && summary.getTotal(contractAddrBase58) > 0, "Not found any token of urc721 contract: " + contractAddrBase58);

      //approve or not
      if(ctx.getApprove()){
        Assert.isTrue(!summary.hasApprovalForAll(toAddrBase58, contractAddrBase58), "To address has already been approved for all with contract: " + contractAddrBase58);
      } else {
        Assert.isTrue(summary.hasApprovalForAll(toAddrBase58, contractAddrBase58), "To address not approved for all with contract: " + contractAddrBase58);
      }

      if(!accountStore.has(toAddr)){
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough balance to cover transaction fee, require " + fee + "ginza");

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc721SetApprovalForAllContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetUpdateFee();//2 UNW default
  }
}
