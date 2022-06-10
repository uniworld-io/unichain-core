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
import org.unichain.common.utils.ActuatorUtil;
import org.unichain.common.utils.AddressUtil;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20ContractCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.Objects;
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

      var urc30ContractStore = dbManager.getTokenPoolStore();
      var urc20ContractStore = dbManager.getUrc20ContractStore();
      var accStore = dbManager.getAccountStore();
      var urc30FutureTokenStore = dbManager.getFutureTokenStore();

      //1. filter migrate account first!
      Predicate<AccountCapsule> filter = accountCap -> {
        var urc30Noodle =  accountCap.getInstance().getTokenMap();
        var urc30Future = accountCap.getInstance().getTokenFutureMap();
        return (Objects.nonNull(urc30Noodle) && urc30Noodle.size() > 0) || ( Objects.nonNull(urc30Future) && urc30Future.size() > 0);
      };
      var migrateAccounts = accStore.filter(filter);

      //2.migrate contracts
      urc30ContractStore.getAll().forEach(urc30Cap -> {
        //register contract acc
        var urc20Addr = AddressUtil.genAssetAddrBySeed(urc30Cap.getTokenName());
        dbManager.createDefaultAccount(urc20Addr, Protocol.AccountType.AssetIssue);

        //save urc20 contract
        var urc20Builder = Contract.Urc20CreateContract.newBuilder()
                .setOwnerAddress(urc30Cap.getOwnerAddress())
                .setAddress(ByteString.copyFrom(urc20Addr))
                .setSymbol(urc30Cap.getName())
                .setName(urc30Cap.getAbbr())
                .setDecimals(0L)
                .setMaxSupply(urc30Cap.getMaxSupply())
                .setTotalSupply(urc30Cap.getTotalSupply())
                .setStartTime(urc30Cap.getStartTime())
                .setEndTime(urc30Cap.getEndTime())
                .setUrl(urc30Cap.getUrl())
                .setFee(urc30Cap.getFee())
                .setExtraFeeRate(urc30Cap.getExtraFeeRate())
                .setFeePool(urc30Cap.getFeePool())
                .setBurned(urc30Cap.getBurnedToken())
                .setLatestOperationTime(urc30Cap.getLatestOperationTime())
                .setLot(urc30Cap.getLot())
                .setFeePoolOrigin(urc30Cap.getOriginFeePool())
                .setExchUnxNum(urc30Cap.getExchUnw())
                .setExchNum(urc30Cap.getExchToken())
                .setExchEnable(true)
                .setCriticalUpdateTime(urc30Cap.getCriticalUpdateTime())
                .setCreateAccFee(urc30Cap.getCreateAccountFee());

        var urc20Cap = new Urc20ContractCapsule(urc20Builder.build());
        urc20ContractStore.put(urc20Addr, urc20Cap);
      });

      //3. migrate account asset
      migrateAccounts.forEach(acc -> {
        //migrate noodle urc30
        acc.getInstance().getTokenMap().forEach((symbol, amount) -> acc.addUrc20Token(AddressUtil.genAssetAddrBySeed(symbol), amount));

        //migrate future
        acc.getInstance().getTokenFutureMap().forEach((symbol, urc30Summary) -> {
          var addrBase58 = Wallet.encode58Check(AddressUtil.genAssetAddrBySeed(symbol));

          //@todo migrate future store: Need review
          // move future urc30 future deals to urc20 future deals
          var tempTick = urc30FutureTokenStore.get(urc30Summary.getLowerTick().toByteArray());
          if (tempTick == null) {
            return;
          }

          while (tempTick.getNextTick() != null && tempTick.getNextTick().size() != 0) {
            ActuatorUtil.addUrc20Future(dbManager, acc.getAddress().toByteArray(), AddressUtil.genAssetAddrBySeed(symbol), tempTick.getBalance(), tempTick.getExpireTime());
            tempTick = urc30FutureTokenStore.get(tempTick.getNextTick().toByteArray());
          }

          // change summary
          var lowerTickUrc20Pointer =
                  createTickDayKeyUrc20(acc, addrBase58, urc30Summary.getLowerBoundTime());
          var upperTickUrc20Pointer =
                  createTickDayKeyUrc20(acc, addrBase58, urc30Summary.getUpperBoundTime());
          var urc20TokenSummary = Protocol.Urc20FutureTokenSummary.newBuilder()
                  .setAddress(ByteString.copyFrom(AddressUtil.genAssetAddrBySeed(urc30Summary.getTokenName())))
                  .setSymbol(urc30Summary.getTokenName())
                  .setTotalDeal(urc30Summary.getTotalDeal())
                  .setLowerBoundTime(urc30Summary.getLowerBoundTime())
                  .setUpperBoundTime(urc30Summary.getUpperBoundTime())
                  .setTotalValue(urc30Summary.getTotalValue())
                  .setLowerTick(lowerTickUrc20Pointer)
                  .setUpperTick(upperTickUrc20Pointer)
                  .build();
          acc.setUrc20FutureTokenSummary(Wallet.encode58Check(ByteString.copyFromUtf8(symbol).toByteArray()), urc20TokenSummary);
        });

        accStore.put(acc.getAddress().toByteArray(), acc);
      });
    }
    catch (Exception e){
        logger.error("failed to upgrade: ", e);
    }
  }

  private ByteString createTickDayKeyUrc20(AccountCapsule ownerAccount, String tokenAddressBase58, long expireTime) {
    var tickDay = Util.makeDayTick(expireTime);
    var tickDayKey = Util.makeUrc20FutureTokenIndexKey(ownerAccount.getAddress().toByteArray(), tokenAddressBase58, tickDay);
    return ByteString.copyFrom(tickDayKey);
  }

}
