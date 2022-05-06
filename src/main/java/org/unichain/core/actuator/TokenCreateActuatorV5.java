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
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.utils.AddressUtil;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TokenAddressSymbolIndexCapsule;
import org.unichain.core.capsule.TokenSymbolAddressIndexCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
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
      var code = super.execute(ret);
      var ctx = contract.unpack(Contract.CreateTokenContract.class);
      var symbol = ctx.getName().toUpperCase();
      var tokenKey = symbol.getBytes();
      var tokenPoolStore = dbManager.getTokenPoolStore();
      var tokenCap = tokenPoolStore.get(tokenKey);
      var walletInfo = AddressUtil.generateAddress(dbManager);
      Assert.notNull(walletInfo, "failed to generate wallet address!!!");
      var tokenAddr = Hex.decodeHex(walletInfo.addressHex);
      tokenCap.setAddress(tokenAddr);
      tokenPoolStore.put(tokenKey, tokenCap);

      //indexing tokenAddrs vs symbol
      var index1Store = dbManager.getTokenAddrSymbolIndexStore();
      var index2Store = dbManager.getTokenSymbolAddrIndexStore();
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

      return code;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setErrorCode(Protocol.Transaction.Result.code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }
}
