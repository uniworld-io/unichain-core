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
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AssetIssueCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.AssetIssueContract;
import org.unichain.protos.Contract.AssetIssueContract.FrozenSupply;
import org.unichain.protos.Protocol.Account.Frozen;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.ArrayList;

@Slf4j(topic = "actuator")
public class AssetIssueActuator extends AbstractActuator {

  public AssetIssueActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(AssetIssueContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var assetIssueCapsule = new AssetIssueCapsule(ctx);
      var assetIssueCapsuleV2 = new AssetIssueCapsule(ctx);
      var tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      tokenIdNum++;
      assetIssueCapsule.setId(Long.toString(tokenIdNum));
      assetIssueCapsuleV2.setId(Long.toString(tokenIdNum));
      dbManager.getDynamicPropertiesStore().saveTokenIdNum(tokenIdNum);

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        assetIssueCapsuleV2.setPrecision(0);
        dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
        dbManager.getAssetIssueV2Store().put(assetIssueCapsuleV2.createDbV2Key(), assetIssueCapsuleV2);
      } else {
        dbManager.getAssetIssueV2Store().put(assetIssueCapsuleV2.createDbV2Key(), assetIssueCapsuleV2);
      }

      chargeFee(ownerAddress, fee);

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      var frozenSupplyList = ctx.getFrozenSupplyList();
      var iterator = frozenSupplyList.iterator();
      var remainSupply = ctx.getTotalSupply();
      var frozenList = new ArrayList<Frozen>();
      var startTime = ctx.getStartTime();

      while (iterator.hasNext()) {
        var next = iterator.next();
        var expireTime = Math.addExact(startTime, Math.multiplyExact(next.getFrozenDays(), 86_400_000L));
        var newFrozen = Frozen.newBuilder()
            .setFrozenBalance(next.getFrozenAmount())
            .setExpireTime(expireTime)
            .build();
        frozenList.add(newFrozen);
        remainSupply = Math.subtractExact(remainSupply, next.getFrozenAmount());
      }

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        accountCapsule.addAsset(assetIssueCapsule.createDbKey(), remainSupply);
      }
      accountCapsule.setAssetIssuedName(assetIssueCapsule.createDbKey());
      accountCapsule.setAssetIssuedID(assetIssueCapsule.createDbV2Key());
      accountCapsule.addAssetV2(assetIssueCapsuleV2.createDbV2Key(), remainSupply);
      accountCapsule.setInstance(accountCapsule.getInstance().toBuilder().addAllFrozenSupply(frozenList).build());
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);

      ret.setAssetIssueID(Long.toString(tokenIdNum));
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException | ArithmeticException e) {
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
      Assert.isTrue(this.contract.is(AssetIssueContract.class), "contract type error,expected type [AssetIssueContract],real type[" + contract.getClass() + "]");

      var assetIssueContract = this.contract.unpack(AssetIssueContract.class);
      var ownerAddress = assetIssueContract.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      Assert.isTrue(TransactionUtil.validAssetName(assetIssueContract.getName().toByteArray()), "Invalid assetName");

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() != 0) {
        String name = assetIssueContract.getName().toStringUtf8().toLowerCase();
        Assert.isTrue(!name.equals("unx"), "assetName can't be unx");
      }

      int precision = assetIssueContract.getPrecision();
      if (precision != 0 && dbManager.getDynamicPropertiesStore().getAllowSameTokenName() != 0) {
        Assert.isTrue(!(precision < 0 || precision > 6), "precision cannot exceed 6");
      }

      Assert.isTrue (!((!assetIssueContract.getAbbr().isEmpty()) && !TransactionUtil.validAssetName(assetIssueContract.getAbbr().toByteArray())),"Invalid abbreviation for token");

      Assert.isTrue(TransactionUtil.validUrl(assetIssueContract.getUrl().toByteArray()), "Invalid url");
      Assert.isTrue(TransactionUtil.validAssetDescription(assetIssueContract.getDescription().toByteArray()), "Invalid description");
      Assert.isTrue(assetIssueContract.getStartTime() != 0, "Start time should be not empty");
      Assert.isTrue(assetIssueContract.getEndTime() != 0, "End time should be not empty");
      Assert.isTrue(assetIssueContract.getEndTime() > assetIssueContract.getStartTime(), "End time should be greater than start time");
      Assert.isTrue(assetIssueContract.getStartTime() > dbManager.getHeadBlockTimeStamp(), "Start time should be greater than HeadBlockTime");

      Assert.isTrue (!(this.dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0
                      && this.dbManager.getAssetIssueStore().get(assetIssueContract.getName().toByteArray()) != null) ,
              "Token exists");

      Assert.isTrue(assetIssueContract.getTotalSupply() > 0,"TotalSupply must greater than 0!");
      Assert.isTrue(assetIssueContract.getUnxNum() > 0,"UnxNum must greater than 0!");
      Assert.isTrue(assetIssueContract.getNum() > 0,"Num must greater than 0!");
      Assert.isTrue(assetIssueContract.getPublicFreeAssetNetUsage() == 0,"PublicFreeAssetNetUsage must be 0!");
      Assert.isTrue(assetIssueContract.getFrozenSupplyCount() <= this.dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyNumber(),
              "Frozen supply list length is too long");
      Assert.isTrue  (!(assetIssueContract.getFreeAssetNetLimit() < 0 || assetIssueContract.getFreeAssetNetLimit() >= dbManager.getDynamicPropertiesStore().getOneDayNetLimit()) ,
              "Invalid FreeAssetNetLimit");
      Assert.isTrue (!(assetIssueContract.getPublicFreeAssetNetLimit() < 0 || assetIssueContract.getPublicFreeAssetNetLimit() >= dbManager.getDynamicPropertiesStore().getOneDayNetLimit()),
              "Invalid PublicFreeAssetNetLimit");

      var remainSupply = assetIssueContract.getTotalSupply();
      var minFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMinFrozenSupplyTime();
      var maxFrozenSupplyTime = dbManager.getDynamicPropertiesStore().getMaxFrozenSupplyTime();
      var frozenList = assetIssueContract.getFrozenSupplyList();

      for (FrozenSupply next : frozenList) {
        Assert.isTrue(next.getFrozenAmount() > 0, "Frozen supply must be greater than 0!");
        Assert.isTrue(next.getFrozenAmount() <= remainSupply, "Frozen supply cannot exceed total supply");
        Assert.isTrue((next.getFrozenDays() >= minFrozenSupplyTime && next.getFrozenDays() <= maxFrozenSupplyTime),
                "frozenDuration must be less than " + maxFrozenSupplyTime + " days " + "and more than " + minFrozenSupplyTime + " days");
        remainSupply = Math.subtractExact(remainSupply, next.getFrozenAmount());
      }

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account not exists");
      Assert.isTrue(accountCapsule.getAssetIssuedName().isEmpty(), "An account can only issue one asset");
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "No enough balance for fee!");
      //
      //    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
      //    String name = new String(assetIssueCapsule.getName().toByteArray(),
      //        Charset.forName("UTF-8")); // getName().toStringUtf8()
      //    long order = 0;
      //    byte[] key = name.getBytes();
      //    while (this.dbManager.getAssetIssueStore().get(key) != null) {
      //      order++;
      //      String nameKey = AssetIssueCapsule.createDbKeyString(name, order);
      //      key = nameKey.getBytes();
      //    }
      //    assetIssueCapsule.setOrder(order);
      //
      //    if (!TransactionUtil.validAssetName(assetIssueCapsule.createDbKey())) {
      //      throw new ContractValidateException("Invalid assetID");
      //    }

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AssetIssueContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();
  }
}
