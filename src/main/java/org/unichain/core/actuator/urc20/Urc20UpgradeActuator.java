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

package org.unichain.core.actuator.urc20;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.common.utils.AddressUtil;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20ContractCapsule;
import org.unichain.core.capsule.urc20.Urc20FutureTokenCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.function.Predicate;

/**
 * Migrate token urc30 to urc20
 */
@Slf4j(topic = "actuator")
public class Urc20UpgradeActuator extends AbstractActuator {

  public Urc20UpgradeActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
      return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return null;
  }

  @Override
  public long calcFee() {
    return 0L;
  }

  @Override
  public void upgrade(){
    try {
      //1. migrate token pool without spender
      var urc30Store = dbManager.getTokenPoolStore();
      var urc20Store = dbManager.getUrc20ContractStore();
      var accStore = dbManager.getAccountStore();
      var future30TokenStore = dbManager.getFutureTokenStore();
      var future20TokenStore = dbManager.getUrc20FutureTransferStore();
      urc30Store.getAll().forEach(urc30Cap -> {
        var urc20Builder = Contract.Urc20CreateContract.newBuilder();
        urc20Builder.setOwnerAddress(urc30Cap.getOwnerAddress());
        //@todo review
        urc20Builder.setAddress(ByteString.copyFrom(AddressUtil.genAssetAddrBySeed(urc30Cap.getTokenName())));

        urc20Builder.setSymbol(urc30Cap.getName());
        urc20Builder.setName(urc30Cap.getAbbr());
        urc20Builder.setDecimals(0L);
        urc20Builder.setMaxSupply(urc30Cap.getMaxSupply());
        urc20Builder.setTotalSupply(urc30Cap.getTotalSupply());
        urc20Builder.setStartTime(urc30Cap.getStartTime());
        urc20Builder.setEndTime(urc30Cap.getEndTime());
        urc20Builder.setUrl(urc30Cap.getUrl());
        urc20Builder.setFee(urc30Cap.getFee());
        urc20Builder.setExtraFeeRate(urc30Cap.getExtraFeeRate());
        urc20Builder.setFeePool(urc30Cap.getFeePool());
        urc20Builder.setBurned(urc30Cap.getBurnedToken());
        urc20Builder.setLatestOperationTime(urc30Cap.getLatestOperationTime());
        urc20Builder.setLot(urc30Cap.getLot());
        urc20Builder.setFeePoolOrigin(urc30Cap.getOriginFeePool());
        urc20Builder.setExchUnxNum(urc30Cap.getExchUnw());
        urc20Builder.setExchNum(urc30Cap.getExchToken());
        urc20Builder.setExchEnable(true);
        urc20Builder.setCriticalUpdateTime(urc30Cap.getCriticalUpdateTime());
        urc20Builder.setCreateAccFee(urc30Cap.getCreateAccountFee());

        var urc20Cap = new Urc20ContractCapsule(urc20Builder.build());
        var urc20Addr = urc20Cap.getAddress().toByteArray();
        urc20Store.put(urc20Addr, urc20Cap);
      });

      //2.migrate available token
      Predicate<AccountCapsule> filter = accountCap -> {
        var urc30 =  accountCap.getInstance().getTokenMap();
        var urc30Future = accountCap.getInstance().getTokenFutureMap();
        return (urc30 != null && urc30.size() > 0) || (urc30Future != null && urc30Future.size() > 0);
      };

      var accounts = accStore.filter(filter);
      accounts.forEach(acc -> {
        //@todo migrate urc30: Need review
        acc.getInstance().getTokenMap().forEach((symbol, amount) -> {
          acc.addUrc20Token(AddressUtil.genAssetAddrBySeed(symbol), amount);
        });

        //@todo migrate urc30 future: Need review
        acc.getInstance().getTokenFutureMap().forEach((symbol, futureTokenSummaryV2) -> {
          var urc20TokenSummary = Protocol.Urc20FutureTokenSummary.newBuilder()
                  .setAddress(ByteString.copyFrom(AddressUtil.genAssetAddrBySeed(futureTokenSummaryV2.getTokenName())))
                  .setSymbol(futureTokenSummaryV2.getTokenName())
                  .setTotalDeal(futureTokenSummaryV2.getTotalDeal())
                  .setLowerBoundTime(futureTokenSummaryV2.getLowerBoundTime())
                  .setUpperBoundTime(futureTokenSummaryV2.getUpperBoundTime())
                  .setTotalValue(futureTokenSummaryV2.getTotalValue())
                  .setLowerTick(futureTokenSummaryV2.getLowerTick())
                  .setUpperTick(futureTokenSummaryV2.getUpperTick())
                  .build();
          acc.setUrc20FutureTokenSummary(Wallet.encode58Check(ByteString.copyFromUtf8(symbol).toByteArray()), urc20TokenSummary);

          var tempTick = future30TokenStore.get(futureTokenSummaryV2.getLowerTick().toByteArray());
          if (tempTick == null) {
            return;
          }

          while (tempTick.getNextTick() != null || tempTick.getNextTick().size() != 0) {
            var urc20Tick = Protocol.Urc20FutureToken.newBuilder()
                    .setFutureBalance(tempTick.getBalance())
                    .setExpireTime(tempTick.getExpireTime())
                    .setPrevTick(tempTick.getPrevTick())
                    .setNextTick(tempTick.getNextTick())
                    .build();

            var addressBase58 = Wallet.encode58Check(AddressUtil.genAssetAddrBySeed(symbol));
            var tickDay = Util.makeDayTick(tempTick.getExpireTime());
            var urc20TickKey = Util.makeUrc20FutureTokenIndexKey(acc.getAddress().toByteArray(), addressBase58, tickDay);
            future20TokenStore.put(urc20TickKey, new Urc20FutureTokenCapsule(urc20Tick));

            tempTick = future30TokenStore.get(tempTick.getNextTick().toByteArray());
          }
        });

          //@todo migrate future store: Need review
        accStore.put(acc.getAddress().toByteArray(), acc);
      });
    }
    catch (Exception e){
        logger.error("failed to upgrade: ", e);
    }
  }
}
