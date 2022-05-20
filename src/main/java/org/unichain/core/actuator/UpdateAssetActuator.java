package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
import org.unichain.protos.Contract.UpdateAssetContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class UpdateAssetActuator extends AbstractActuator {

  public UpdateAssetActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(UpdateAssetContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var newLimit = ctx.getNewLimit();
      var newPublicLimit = ctx.getNewPublicLimit();
      var newUrl = ctx.getUrl();
      var newDescription = ctx.getDescription();
      var capsule = dbManager.getAccountStore().get(ownerAddress);

      AssetIssueCapsule assetIssueCapsule, assetIssueCapsuleV2;

      var assetIssueStoreV2 = dbManager.getAssetIssueV2Store();
      assetIssueCapsuleV2 = assetIssueStoreV2.get(capsule.getAssetIssuedID().toByteArray());
      assetIssueCapsuleV2.setFreeAssetNetLimit(newLimit);
      assetIssueCapsuleV2.setPublicFreeAssetNetLimit(newPublicLimit);
      assetIssueCapsuleV2.setUrl(newUrl);
      assetIssueCapsuleV2.setDescription(newDescription);

      if(dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        var assetIssueStore = dbManager.getAssetIssueStore();
        assetIssueCapsule = assetIssueStore.get(capsule.getAssetIssuedName().toByteArray());
        assetIssueCapsule.setFreeAssetNetLimit(newLimit);
        assetIssueCapsule.setPublicFreeAssetNetLimit(newPublicLimit);
        assetIssueCapsule.setUrl(newUrl);
        assetIssueCapsule.setDescription(newDescription);
        dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
        dbManager.getAssetIssueV2Store().put(assetIssueCapsuleV2.createDbV2Key(), assetIssueCapsuleV2);
      } else {
        dbManager.getAssetIssueV2Store().put(assetIssueCapsuleV2.createDbV2Key(), assetIssueCapsuleV2);
      }

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(UpdateAssetContract.class), "Contract type error,expected type [UpdateAssetContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(UpdateAssetContract.class);
      var newLimit = ctx.getNewLimit();
      var newPublicLimit = ctx.getNewPublicLimit();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var newUrl = ctx.getUrl();
      var newDescription = ctx.getDescription();

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");

      var account = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(account, "Account has not existed");

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        Assert.isTrue(!account.getAssetIssuedName().isEmpty(), "Account has not issue any asset");
        Assert.notNull(dbManager.getAssetIssueStore().get(account.getAssetIssuedName().toByteArray()), "Asset not exists in AssetIssueStore");
      } else {
        Assert.isTrue(!account.getAssetIssuedID().isEmpty(), "Account has not issue any asset");
        Assert.notNull(dbManager.getAssetIssueV2Store().get(account.getAssetIssuedID().toByteArray()), "Asset not exists  in AssetIssueV2Store");
      }

      Assert.isTrue(TransactionUtil.validUrl(newUrl.toByteArray()), "Invalid url");
      Assert.isTrue(TransactionUtil.validAssetDescription(newDescription.toByteArray()), "Invalid description");

      var freeAssetNetLimit = newLimit < 0 || newLimit >= dbManager.getDynamicPropertiesStore().getOneDayNetLimit();
      Assert.isTrue(!freeAssetNetLimit, "Invalid FreeAssetNetLimit");

      var publicFreeAssetNetLimit = newPublicLimit < 0 || newPublicLimit >= dbManager.getDynamicPropertiesStore().getOneDayNetLimit();
      Assert.isTrue(!publicFreeAssetNetLimit, "Invalid PublicFreeAssetNetLimit");
      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateAssetContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
