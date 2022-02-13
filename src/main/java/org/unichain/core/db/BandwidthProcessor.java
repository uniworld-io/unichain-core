package org.unichain.core.db;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Constant;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.AssetIssueCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.exception.AccountResourceInsufficientException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.TooBigTransactionResultException;
import org.unichain.protos.Contract.TransferAssetContract;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Protocol.Transaction.Contract;

import java.util.List;
import java.util.Map;

import static org.unichain.protos.Protocol.Transaction.Contract.ContractType.TransferAssetContract;

@Slf4j(topic = "DB")
public class BandwidthProcessor extends ResourceProcessor {

  public BandwidthProcessor(Manager manager) {
    super(manager);
  }

  @Override
  public void updateUsage(AccountCapsule accountCapsule) {
    long now = dbManager.getWitnessController().getHeadSlot();
    updateUsage(accountCapsule, now);
  }

  private void updateUsage(AccountCapsule accountCapsule, long now) {
    long oldNetUsage = accountCapsule.getNetUsage();
    long latestConsumeTime = accountCapsule.getLatestConsumeTime();
    accountCapsule.setNetUsage(increase(oldNetUsage, 0, latestConsumeTime, now));
    long oldFreeNetUsage = accountCapsule.getFreeNetUsage();
    long latestConsumeFreeTime = accountCapsule.getLatestConsumeFreeTime();
    accountCapsule.setFreeNetUsage(increase(oldFreeNetUsage, 0, latestConsumeFreeTime, now));

    if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      Map<String, Long> assetMap = accountCapsule.getAssetMap();
      assetMap.forEach((assetName, balance) -> {
        long oldFreeAssetNetUsage = accountCapsule.getFreeAssetNetUsage(assetName);
        long latestAssetOperationTime = accountCapsule.getLatestAssetOperationTime(assetName);
        accountCapsule.putFreeAssetNetUsage(assetName, increase(oldFreeAssetNetUsage, 0, latestAssetOperationTime, now));
      });
    }
    Map<String, Long> assetMapV2 = accountCapsule.getAssetMapV2();
    assetMapV2.forEach((assetName, balance) -> {
      long oldFreeAssetNetUsage = accountCapsule.getFreeAssetNetUsageV2(assetName);
      long latestAssetOperationTime = accountCapsule.getLatestAssetOperationTimeV2(assetName);
      accountCapsule.putFreeAssetNetUsageV2(assetName, increase(oldFreeAssetNetUsage, 0, latestAssetOperationTime, now));
    });
  }

  @Override
  public void consume(TransactionCapsule unx, TransactionTrace trace) throws ContractValidateException, AccountResourceInsufficientException, TooBigTransactionResultException {
    List<Contract> contracts = unx.getInstance().getRawData().getContractList();
    if (unx.getResultSerializedSize() > Math.multiplyExact(Constant.MAX_RESULT_SIZE_IN_TX , contracts.size())) {
      throw new TooBigTransactionResultException();
    }

    long bytesSize;

    if (dbManager.getDynamicPropertiesStore().supportVM()) {
      bytesSize = unx.getInstance().toBuilder().clearRet().build().getSerializedSize();
    } else {
      bytesSize = unx.getSerializedSize();
    }

    for (Contract contract : contracts) {
      if (dbManager.getDynamicPropertiesStore().supportVM()) {
        bytesSize = Math.addExact(bytesSize, Constant.MAX_RESULT_SIZE_IN_TX);
      }

      logger.debug("unxId {},bandwidth cost :{}", unx.getTransactionId(), bytesSize);
      trace.setNetBill(bytesSize, 0);
      byte[] address = TransactionCapsule.getOwner(contract);
      AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
      if (accountCapsule == null) {
        throw new ContractValidateException("account not exists");
      }
      long now = dbManager.getWitnessController().getHeadSlot();

      if (contractCreateNewAccount(contract)) {
        consumeForCreateNewAccount(accountCapsule, bytesSize, now, trace);
        continue;
      }

      if (contract.getType() == TransferAssetContract && useAssetAccountNet(contract,
          accountCapsule, now, bytesSize)) {
        continue;
      }

      if (useAccountNet(accountCapsule, bytesSize, now)) {
        continue;
      }

      if (useFreeNet(accountCapsule, bytesSize, now)) {
        continue;
      }

      if (useTransactionFee(accountCapsule, bytesSize, trace)) {
        continue;
      }

      long fee = Math.multiplyExact(dbManager.getDynamicPropertiesStore().getTransactionFee(), bytesSize);
      throw new AccountResourceInsufficientException("Account Insufficient bandwidth[" + bytesSize + "] and balance[" + fee + "] to create new account");
    }
  }

  private boolean useTransactionFee(AccountCapsule accountCapsule, long bytes, TransactionTrace trace) {
    long fee = Math.multiplyExact(dbManager.getDynamicPropertiesStore().getTransactionFee(), bytes);
    if (consumeFee(accountCapsule, fee)) {
      trace.setNetBill(0, fee);
      dbManager.getDynamicPropertiesStore().addTotalTransactionCost(fee);
      return true;
    } else {
      return false;
    }
  }

  private void consumeForCreateNewAccount(AccountCapsule accountCapsule, long bytes, long now, TransactionTrace trace) throws AccountResourceInsufficientException {
    boolean ret = consumeBandwidthForCreateNewAccount(accountCapsule, bytes, now);
    if (!ret) {
      ret = consumeFeeForCreateNewAccount(accountCapsule, trace);
      if (!ret) {
        throw new AccountResourceInsufficientException();
      }
    }
  }

  public boolean consumeBandwidthForCreateNewAccount(AccountCapsule accountCapsule, long bytes, long now) {
    long createNewAccountBandwidthRatio = dbManager.getDynamicPropertiesStore().getCreateNewAccountBandwidthRate();
    long netUsage = accountCapsule.getNetUsage();
    long latestConsumeTime = accountCapsule.getLatestConsumeTime();
    long netLimit = calculateGlobalNetLimit(accountCapsule);

    long newNetUsage = increase(netUsage, 0, latestConsumeTime, now);

    if (Math.multiplyExact(bytes, createNewAccountBandwidthRatio) <= Math.subtractExact(netLimit, newNetUsage)) {
      latestConsumeTime = now;
      long latestOperationTime = dbManager.getHeadBlockTimeStamp();
      newNetUsage = increase(newNetUsage, Math.multiplyExact(bytes, createNewAccountBandwidthRatio), latestConsumeTime, now);
      accountCapsule.setLatestConsumeTime(latestConsumeTime);
      accountCapsule.setLatestOperationTime(latestOperationTime);
      accountCapsule.setNetUsage(newNetUsage);
      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      return true;
    }
    return false;
  }

  public boolean consumeFeeForCreateNewAccount(AccountCapsule accountCapsule, TransactionTrace trace) {
    long fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
    if (consumeFee(accountCapsule, fee)) {
      trace.setNetBill(0, fee);
      dbManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
      return true;
    } else {
      return false;
    }
  }

  public boolean contractCreateNewAccount(Contract contract) {
    AccountCapsule toAccount;
    switch (contract.getType()) {
      case AccountCreateContract:
        return true;
      case TransferContract:
        TransferContract transferContract;
        try {
          transferContract = contract.getParameter().unpack(TransferContract.class);
        } catch (Exception ex) {
          throw new RuntimeException(ex.getMessage());
        }
        toAccount = dbManager.getAccountStore().get(transferContract.getToAddress().toByteArray());
        return toAccount == null;
      case TransferAssetContract:
        TransferAssetContract transferAssetContract;
        try {
          transferAssetContract = contract.getParameter().unpack(TransferAssetContract.class);
        } catch (Exception ex) {
          throw new RuntimeException(ex.getMessage());
        }
        toAccount = dbManager.getAccountStore().get(transferAssetContract.getToAddress().toByteArray());
        return toAccount == null;
      default:
        return false;
    }
  }

  private boolean useAssetAccountNet(Contract contract, AccountCapsule accountCapsule, long now, long bytes) throws ContractValidateException {
    ByteString assetName;
    try {
      assetName = contract.getParameter().unpack(TransferAssetContract.class).getAssetName();
    } catch (Exception ex) {
      throw new RuntimeException(ex.getMessage());
    }

    AssetIssueCapsule assetIssueCapsule, assetIssueCapsuleV2;
    assetIssueCapsule = dbManager.getAssetIssueStoreFinal().get(assetName.toByteArray());
    if (assetIssueCapsule == null) {
      throw new ContractValidateException("asset not exists");
    }

    String tokenName = ByteArray.toStr(assetName.toByteArray());
    String tokenID = assetIssueCapsule.getId();
    if (assetIssueCapsule.getOwnerAddress() == accountCapsule.getAddress()) {
      return useAccountNet(accountCapsule, bytes, now);
    }

    long publicFreeAssetNetLimit = assetIssueCapsule.getPublicFreeAssetNetLimit();
    long publicFreeAssetNetUsage = assetIssueCapsule.getPublicFreeAssetNetUsage();
    long publicLatestFreeNetTime = assetIssueCapsule.getPublicLatestFreeNetTime();

    long newPublicFreeAssetNetUsage = increase(publicFreeAssetNetUsage, 0, publicLatestFreeNetTime, now);

    if (bytes > Math.subtractExact(publicFreeAssetNetLimit, newPublicFreeAssetNetUsage)) {
      logger.debug("The " + tokenID + " public free bandwidth is not enough");
      return false;
    }

    long freeAssetNetLimit = assetIssueCapsule.getFreeAssetNetLimit();

    long freeAssetNetUsage, latestAssetOperationTime;
    if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      freeAssetNetUsage = accountCapsule.getFreeAssetNetUsage(tokenName);
      latestAssetOperationTime = accountCapsule.getLatestAssetOperationTime(tokenName);
    } else {
      freeAssetNetUsage = accountCapsule.getFreeAssetNetUsageV2(tokenID);
      latestAssetOperationTime = accountCapsule.getLatestAssetOperationTimeV2(tokenID);
    }

    long newFreeAssetNetUsage = increase(freeAssetNetUsage, 0, latestAssetOperationTime, now);

    if (bytes > Math.subtractExact(freeAssetNetLimit, newFreeAssetNetUsage)) {
      logger.warn("The " + tokenID + " free bandwidth is not enough");
      return false;
    }

    AccountCapsule issuerAccountCapsule = dbManager.getAccountStore().get(assetIssueCapsule.getOwnerAddress().toByteArray());

    long issuerNetUsage = issuerAccountCapsule.getNetUsage();
    long latestConsumeTime = issuerAccountCapsule.getLatestConsumeTime();
    long issuerNetLimit = calculateGlobalNetLimit(issuerAccountCapsule);

    long newIssuerNetUsage = increase(issuerNetUsage, 0, latestConsumeTime, now);

    if (bytes > Math.subtractExact(issuerNetLimit, newIssuerNetUsage)) {
      logger.debug("The " + tokenID + " issuer's bandwidth is not enough");
      return false;
    }

    latestConsumeTime = now;
    latestAssetOperationTime = now;
    publicLatestFreeNetTime = now;
    long latestOperationTime = dbManager.getHeadBlockTimeStamp();

    newIssuerNetUsage = increase(newIssuerNetUsage, bytes, latestConsumeTime, now);
    newFreeAssetNetUsage = increase(newFreeAssetNetUsage, bytes, latestAssetOperationTime, now);
    newPublicFreeAssetNetUsage = increase(newPublicFreeAssetNetUsage, bytes, publicLatestFreeNetTime, now);

    issuerAccountCapsule.setNetUsage(newIssuerNetUsage);
    issuerAccountCapsule.setLatestConsumeTime(latestConsumeTime);

    assetIssueCapsule.setPublicFreeAssetNetUsage(newPublicFreeAssetNetUsage);
    assetIssueCapsule.setPublicLatestFreeNetTime(publicLatestFreeNetTime);

    accountCapsule.setLatestOperationTime(latestOperationTime);
    if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      accountCapsule.putLatestAssetOperationTimeMap(tokenName, latestAssetOperationTime);
      accountCapsule.putFreeAssetNetUsage(tokenName, newFreeAssetNetUsage);
      accountCapsule.putLatestAssetOperationTimeMapV2(tokenID, latestAssetOperationTime);
      accountCapsule.putFreeAssetNetUsageV2(tokenID, newFreeAssetNetUsage);

      dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

      assetIssueCapsuleV2 = dbManager.getAssetIssueV2Store().get(assetIssueCapsule.createDbV2Key());
      assetIssueCapsuleV2.setPublicFreeAssetNetUsage(newPublicFreeAssetNetUsage);
      assetIssueCapsuleV2.setPublicLatestFreeNetTime(publicLatestFreeNetTime);
      dbManager.getAssetIssueV2Store().put(assetIssueCapsuleV2.createDbV2Key(), assetIssueCapsuleV2);
    } else {
      accountCapsule.putLatestAssetOperationTimeMapV2(tokenID, latestAssetOperationTime);
      accountCapsule.putFreeAssetNetUsageV2(tokenID, newFreeAssetNetUsage);
      dbManager.getAssetIssueV2Store().put(assetIssueCapsule.createDbV2Key(), assetIssueCapsule);
    }

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    dbManager.getAccountStore().put(issuerAccountCapsule.createDbKey(), issuerAccountCapsule);
    return true;
  }

  public long calculateGlobalNetLimit(AccountCapsule accountCapsule) {
    long frozeBalance = accountCapsule.getAllFrozenBalanceForBandwidth();
    if (frozeBalance < 1_000_000L) {
      return 0;
    }
    long netWeight = frozeBalance / 1_000_000L;
    long totalNetLimit = dbManager.getDynamicPropertiesStore().getTotalNetLimit();
    long totalNetWeight = dbManager.getDynamicPropertiesStore().getTotalNetWeight();
    if (totalNetWeight == 0) {
      return 0;
    }
    return (long) (netWeight * ((double) totalNetLimit / totalNetWeight));
  }

  private boolean useAccountNet(AccountCapsule accountCapsule, long bytes, long now) {
    long netUsage = accountCapsule.getNetUsage();
    long latestConsumeTime = accountCapsule.getLatestConsumeTime();
    long netLimit = calculateGlobalNetLimit(accountCapsule);
    long newNetUsage = increase(netUsage, 0, latestConsumeTime, now);

    if (bytes > Math.subtractExact(netLimit, newNetUsage)) {
      logger.debug("net usage is running out. now use free net usage");
      return false;
    }

    latestConsumeTime = now;
    long latestOperationTime = dbManager.getHeadBlockTimeStamp();
    newNetUsage = increase(newNetUsage, bytes, latestConsumeTime, now);
    accountCapsule.setNetUsage(newNetUsage);
    accountCapsule.setLatestOperationTime(latestOperationTime);
    accountCapsule.setLatestConsumeTime(latestConsumeTime);

    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    return true;
  }

  private boolean useFreeNet(AccountCapsule accountCapsule, long bytes, long now) {
    long freeNetLimit = dbManager.getDynamicPropertiesStore().getFreeNetLimit();
    long freeNetUsage = accountCapsule.getFreeNetUsage();
    long latestConsumeFreeTime = accountCapsule.getLatestConsumeFreeTime();
    long newFreeNetUsage = increase(freeNetUsage, 0, latestConsumeFreeTime, now);

    if (bytes > (freeNetLimit - newFreeNetUsage)) {
      logger.debug("free net usage is running out");
      return false;
    }

    long publicNetLimit = dbManager.getDynamicPropertiesStore().getPublicNetLimit();
    long publicNetUsage = dbManager.getDynamicPropertiesStore().getPublicNetUsage();
    long publicNetTime = dbManager.getDynamicPropertiesStore().getPublicNetTime();

    long newPublicNetUsage = increase(publicNetUsage, 0, publicNetTime, now);

    if (bytes > Math.subtractExact(publicNetLimit, newPublicNetUsage)) {
      logger.debug("free public net usage is running out");
      return false;
    }

    latestConsumeFreeTime = now;
    long latestOperationTime = dbManager.getHeadBlockTimeStamp();
    publicNetTime = now;
    newFreeNetUsage = increase(newFreeNetUsage, bytes, latestConsumeFreeTime, now);
    newPublicNetUsage = increase(newPublicNetUsage, bytes, publicNetTime, now);
    accountCapsule.setFreeNetUsage(newFreeNetUsage);
    accountCapsule.setLatestConsumeFreeTime(latestConsumeFreeTime);
    accountCapsule.setLatestOperationTime(latestOperationTime);

    dbManager.getDynamicPropertiesStore().savePublicNetUsage(newPublicNetUsage);
    dbManager.getDynamicPropertiesStore().savePublicNetTime(publicNetTime);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    return true;
  }
}


