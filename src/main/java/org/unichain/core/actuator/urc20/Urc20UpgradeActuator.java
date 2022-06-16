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
import lombok.val;
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
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.math.BigInteger;
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
      logger.warn("UPGRADING urc30 to urc20 asset ....");
      var urc30ContractStore = dbManager.getTokenPoolStore();
      var urc20ContractStore = dbManager.getUrc20ContractStore();
      var accStore = dbManager.getAccountStore();
      var urc30FutureTokenStore = dbManager.getFutureTokenStore();

      //1. filter migrate account first!
      logger.warn("filtering urc30 asset account...");
      Predicate<AccountCapsule> filter = accountCap -> {
        var urc30Noodle =  accountCap.getInstance().getTokenMap();
        var urc30Future = accountCap.getInstance().getTokenFutureMap();
        return (Objects.nonNull(urc30Noodle) && urc30Noodle.size() > 0) || ( Objects.nonNull(urc30Future) && urc30Future.size() > 0);
      };
      var migrateAccounts = accStore.filter(filter);
      logger.warn("filtering urc30 asset account...done! got {} accounts!", migrateAccounts.size());


      //2.migrate contracts
      urc30ContractStore.getAll().forEach(urc30Cap -> {
        logger.warn("migrate urc30 contract {} ...", urc30Cap.getName());
        //register contract acc
        var urc20Addr = AddressUtil.genAssetAddrBySeed(urc30Cap.getTokenName());
        dbManager.createDefaultAccount(urc20Addr, Protocol.AccountType.Contract);

        //save urc20 contract
        var urc20Builder = Contract.Urc20CreateContract.newBuilder()
                .setOwnerAddress(urc30Cap.getOwnerAddress())
                .setAddress(ByteString.copyFrom(urc20Addr))
                .setSymbol(urc30Cap.getName())
                .setName(urc30Cap.getAbbr())
                .setDecimals(0L)
                .setMaxSupply(BigInteger.valueOf(urc30Cap.getMaxSupply()).toString())
                .setTotalSupply(BigInteger.valueOf(urc30Cap.getTotalSupply()).toString())
                .setStartTime(urc30Cap.getStartTime())
                .setEndTime(urc30Cap.getEndTime())
                .setUrl(urc30Cap.getUrl())
                .setFee(urc30Cap.getFee())
                .setExtraFeeRate(urc30Cap.getExtraFeeRate())
                .setFeePool(urc30Cap.getFeePool())
                .setBurned(BigInteger.valueOf(urc30Cap.getBurnedToken()).toString())
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
        logger.warn("migrate urc30 contract {} ...done!", urc30Cap.getName());
      });

      //3. migrate account asset
      migrateAccounts.forEach(acc -> {
        try {
          val ownerAddr = acc.getAddress().toByteArray();
          var ownerAddrBase58 = Wallet.encode58Check(ownerAddr);
          logger.warn("migrate urc30 acc {} ...", ownerAddrBase58);
          //migrate noodle urc30
          acc.getInstance().getTokenMap().forEach((symbol, amount) -> acc.addUrc20Token(AddressUtil.genAssetAddrBySeed(symbol), BigInteger.valueOf(amount)));
          accStore.put(ownerAddr, acc);

          //migrate future
          acc.getInstance().getTokenFutureMap().forEach((symbol, urc30Summary) -> {
            try {
              val urc20Addr = AddressUtil.genAssetAddrBySeed(symbol);
              var urc30HeadDeal = urc30FutureTokenStore.get(urc30Summary.getLowerTick().toByteArray());

              while (Objects.nonNull(urc30HeadDeal) && Objects.nonNull(urc30HeadDeal.getNextTick()) && urc30HeadDeal.getNextTick().size() > 0) {
                ActuatorUtil.addUrc20Future(dbManager, ownerAddr, urc20Addr, BigInteger.valueOf(urc30HeadDeal.getBalance()), urc30HeadDeal.getExpireTime());
                urc30HeadDeal = urc30FutureTokenStore.get(urc30HeadDeal.getNextTick().toByteArray());
              }
              logger.warn("migrate future urc30 of symbol {} success!", symbol);
            }
            catch (Exception e){
              logger.error("migrate future urc30 of symbol {} error -->", symbol, e);
            }
          });
          logger.warn("migrate urc30 acc {} ...done!", ownerAddrBase58);
        }
        catch (Exception e){
          logger.error("migrate urc30 of account  {} error -->", acc, e);
        }
      });

      logger.warn("UPGRADING urc30 to urc20 asset ....SUCCESSFUL!");
    }
    catch (Exception e){
        logger.error("failed to upgrade: ", e);
    }
  }
}
