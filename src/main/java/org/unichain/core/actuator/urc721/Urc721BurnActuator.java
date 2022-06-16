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
import com.google.protobuf.Descriptors;
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
import org.unichain.protos.Contract.Urc721BurnContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc721BurnActuator extends AbstractActuator {

  private static final Descriptors.FieldDescriptor URC721_BURN_FIELD_TOKENID = Urc721BurnContract.getDescriptor().findFieldByNumber(Urc721BurnContract.TOKEN_ID_FIELD_NUMBER);

  public Urc721BurnActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc721BurnContract.class);
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var tokenKey = ArrayUtils.addAll(ctx.getAddress().toByteArray(), ByteArray.fromLong(ctx.getTokenId()));
      dbManager.removeUrc721Token(tokenKey);

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
      Assert.isTrue(contract.is(Urc721BurnContract.class), "Contract type error,expected type [Urc721BurnContract],real type[" + contract.getClass() + "]");
      var fee = calcFee();
      val ctx = this.contract.unpack(Urc721BurnContract.class);
      var operatorAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();

      Assert.isTrue(Wallet.addressValid(operatorAddr)
              && Wallet.addressValid(contractAddr)
              && (ctx.hasField(URC721_BURN_FIELD_TOKENID) && ctx.getTokenId() >= 0), "Invalid operator address, contract address or token id");

      var accountStore = dbManager.getAccountStore();
      var tokenStore = dbManager.getUrc721TokenStore();
      var summaryStore = dbManager.getUrc721AccountTokenRelationStore();
      var contractStore = dbManager.getUrc721ContractStore();

      Assert.isTrue(accountStore.has(operatorAddr) && contractStore.has(contractAddr), "Owner|contract address not exist");


      var tokenKey = ArrayUtils.addAll(contractAddr, ByteArray.fromLong(ctx.getTokenId()));
      Assert.isTrue(tokenStore.has(tokenKey), "Token not exist");

      var token = tokenStore.get(tokenKey);
      var tokenOwner = token.getOwner();
      var summary = summaryStore.get(tokenOwner);

      Assert.isTrue(Arrays.equals(tokenOwner, operatorAddr)
                      || (summary.isApprovedForAll(contractAddr, operatorAddr))
                      || token.isApproval(operatorAddr),
              "Not allowed to burn token");

      Assert.isTrue(accountStore.get(operatorAddr).getBalance() >= fee, "Not enough Balance to cover transaction fee, require " + fee + "ginza");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc721BurnContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
