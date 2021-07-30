package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.unichain.core.Constant;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.exception.AccountResourceInsufficientException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.TooBigTransactionResultException;
import org.unichain.protos.Contract.TransferAssetContract;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Protocol.Transaction.Contract;

import java.util.List;
import java.util.Map;

/**
 * @note charge bandwidth directly from account balance:
 * - dont use global net
 * - dont use account net
 * - dont use account free net
 */
@Slf4j(topic = "DB")
public class BandwidthProcessorV2 extends ResourceProcessor {

  public BandwidthProcessorV2(Manager manager) {
    super(manager);
  }

  @Deprecated
  @Override
  public void updateUsage(AccountCapsule accountCapsule) {
    long now = dbManager.getWitnessController().getHeadSlot();
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
  public void consume(TransactionCapsule tx, TransactionTrace trace) throws ContractValidateException, AccountResourceInsufficientException, TooBigTransactionResultException {
    List<Contract> contracts = tx.getInstance().getRawData().getContractList();

    if (tx.getResultSerializedSize() > Constant.MAX_RESULT_SIZE_IN_TX * contracts.size()) {
      throw new TooBigTransactionResultException();
    }

    long bytesSize;

    if (dbManager.getDynamicPropertiesStore().supportVM()) {
      bytesSize = tx.getInstance().toBuilder().clearRet().build().getSerializedSize();
    } else {
      bytesSize = tx.getSerializedSize();
    }

    for (Contract contract : contracts) {
      if (dbManager.getDynamicPropertiesStore().supportVM()) {
        bytesSize += Constant.MAX_RESULT_SIZE_IN_TX;
      }

      logger.debug("unxId {}, bandwidth cost :{}", tx.getTransactionId(), bytesSize);
      trace.setNetBill(bytesSize, 0);
      byte[] address = TransactionCapsule.getOwner(contract);
      AccountCapsule ownerAccountCap = dbManager.getAccountStore().get(address);
      if (ownerAccountCap == null) {
        throw new ContractValidateException("account not exists");
      }

      //if create new account, just charge create acc fee, don't charge bw fee
      /*
        @todo if transfer token:
          - and if create new account due to transfer to non-existing account
          - just charge create account fee to owner token
       */
      if (isContractCreateNewAccount(contract)){
        consumeForCreateNewAccount(ownerAccountCap, trace);
        continue;
      }

      //or else charge bw fee
      //@todo if transfer token: charge token pool fee
      if (useTransactionFee(ownerAccountCap, bytesSize, trace)) {
        continue;
      }

      long fee = dbManager.getDynamicPropertiesStore().getTransactionFee() * bytesSize;
      throw new AccountResourceInsufficientException("Account Insufficient bandwidth[" + bytesSize + "] and balance[" + fee + "] to create new account");
    }
  }

  private boolean useTransactionFee(AccountCapsule accountCapsule, long bytes, TransactionTrace trace) {
    long bwFee = dbManager.getDynamicPropertiesStore().getTransactionFee() * bytes;
    if (consumeFee(accountCapsule, bwFee)) {
      trace.setNetBill(0, bwFee);
      dbManager.getDynamicPropertiesStore().addTotalTransactionCost(bwFee);
      return true;
    } else {
      return false;
    }
  }

  private void consumeForCreateNewAccount(AccountCapsule accountCapsule, TransactionTrace trace) throws AccountResourceInsufficientException {
    long fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
    if (consumeFee(accountCapsule, fee)) {
      trace.setNetBill(0, fee);
      dbManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
      return;
    } else {
      throw new AccountResourceInsufficientException();
    }
  }

  public boolean isContractCreateNewAccount(Contract contract) {
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

  @Deprecated
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
}
