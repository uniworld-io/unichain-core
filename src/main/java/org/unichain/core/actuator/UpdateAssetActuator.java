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
import org.unichain.protos.Contract.AccountUpdateContract;
import org.unichain.protos.Contract.UpdateAssetContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class UpdateAssetActuator extends AbstractActuator {

  UpdateAssetActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val updateAssetContract = this.contract.unpack(UpdateAssetContract.class);
      var ownerAddress = updateAssetContract.getOwnerAddress().toByteArray();
      var newLimit = updateAssetContract.getNewLimit();
      var newPublicLimit = updateAssetContract.getNewPublicLimit();

      var newUrl = updateAssetContract.getUrl();
      var newDescription = updateAssetContract.getDescription();

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);

      AssetIssueCapsule assetIssueCapsule, assetIssueCapsuleV2;

      var assetIssueStoreV2 = dbManager.getAssetIssueV2Store();
      assetIssueCapsuleV2 = assetIssueStoreV2.get(accountCapsule.getAssetIssuedID().toByteArray());
      assetIssueCapsuleV2.setFreeAssetNetLimit(newLimit);
      assetIssueCapsuleV2.setPublicFreeAssetNetLimit(newPublicLimit);
      assetIssueCapsuleV2.setUrl(newUrl);
      assetIssueCapsuleV2.setDescription(newDescription);

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        var assetIssueStore = dbManager.getAssetIssueStore();
        assetIssueCapsule = assetIssueStore.get(accountCapsule.getAssetIssuedName().toByteArray());
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
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    Assert.notNull(contract, "No contract!");
    Assert.notNull(dbManager, "No dbManager!");
    Assert.isTrue(this.contract.is(UpdateAssetContract.class), "contract type error,expected type [UpdateAssetContract],real type[" + contract.getClass() + "]");

    final UpdateAssetContract updateAssetContract;
    try {
      updateAssetContract = this.contract.unpack(UpdateAssetContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    var newLimit = updateAssetContract.getNewLimit();
    var newPublicLimit = updateAssetContract.getNewPublicLimit();
    var ownerAddress = updateAssetContract.getOwnerAddress().toByteArray();
    var newUrl = updateAssetContract.getUrl();
    var newDescription = updateAssetContract.getDescription();

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

    var publicFreeAssetNetLimit = newPublicLimit < 0 || newPublicLimit >=
            dbManager.getDynamicPropertiesStore().getOneDayNetLimit();
    Assert.isTrue(!publicFreeAssetNetLimit, "Invalid PublicFreeAssetNetLimit");

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AccountUpdateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
