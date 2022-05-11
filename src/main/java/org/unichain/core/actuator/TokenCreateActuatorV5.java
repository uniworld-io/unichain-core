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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TokenAddressSymbolIndexCapsule;
import org.unichain.core.capsule.TokenSymbolAddressIndexCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

@Slf4j(topic = "actuator")
public class TokenCreateActuatorV5 extends TokenCreateActuatorV4 {

  TokenCreateActuatorV5(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    try {
      super.execute(ret);
      var ctx = contract.unpack(Contract.CreateTokenContract.class);
      var symbol = ctx.getName().toUpperCase();
      var tokenAddr = ctx.getAddress().toByteArray();

      //indexing token address vs symbol
      var index1 = dbManager.getTokenAddrSymbolIndexStore();
      var index2 = dbManager.getTokenSymbolAddrIndexStore();
      index1.put(tokenAddr, new TokenAddressSymbolIndexCapsule(Protocol.TokenAddressSymbolIndex.newBuilder()
              .setSymbol(symbol)
              .build()));
      index2.put(symbol.getBytes(), new TokenSymbolAddressIndexCapsule(Protocol.TokenSymbolAddressIndex.newBuilder()
              .setAddress(ctx.getAddress())
              .build()));

      //register token contract account
      var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
      var tokenAccount = new AccountCapsule(ByteString.copyFrom(tokenAddr), Protocol.AccountType.AssetIssue, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
      dbManager.getAccountStore().put(tokenAddr, tokenAccount);

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setErrorCode(Protocol.Transaction.Result.code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      super.validate();
      //make sure this address not exist
      val ctx = this.contract.unpack(Contract.CreateTokenContract.class);
      Assert.isTrue(!dbManager.getAccountStore().has(ctx.getAddress().toByteArray()), "token address exist");
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public void upgrade(){
    //generate all token address
  }
}
