package org.unichain.core.db;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.core.Constant;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.exception.AccountResourceInsufficientException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.TooBigTransactionResultException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.TransferAssetContract;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Contract.TransferTokenContract;
import org.unichain.protos.Protocol.Transaction.Contract;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;
import java.util.List;
import java.util.Map;
import org.unichain.protos.Contract.WithdrawFutureTokenContract;
/**
 * Charge bandwidth directly from account balance:
 * - don't use global net
 * - don't use account net
 * - don't use account free net
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

      /**
       * @todo double fee: this phase & actuator phase ?
       */
      try {
        if (isContractCreateNewAccount(contract)) {
          if (contract.getType() == ContractType.TransferTokenContract)
            consumeForCreateNewAccount4TokenTransfer(contract, trace);
          else
            consumeForCreateNewAccount(ownerAccountCap, trace);
          continue;
        }
      }
      catch (AccountResourceInsufficientException ex){
        throw new AccountResourceInsufficientException("Account Insufficient balance to create new account");
      }

      /**
       * or else charge bw fee
       */
        try{
          switch (contract.getType()) {
            case TransferTokenContract:
              byte[] tokenKey1 = Util.stringAsBytesUppercase(contract.getParameter().unpack(TransferTokenContract.class).getTokenName());
              if (useTransactionFee4TokenPool(tokenKey1, bytesSize, trace))
                continue;
              break;
            case WithdrawFutureTokenContract:
              byte[] tokenKey2 = Util.stringAsBytesUppercase(contract.getParameter().unpack(WithdrawFutureTokenContract.class).getTokenName());
              if (useTransactionFee4TokenPool(tokenKey2, bytesSize, trace))
                continue;
              break;
            default:
              if (useTransactionFee(ownerAccountCap, bytesSize, trace))
                continue;
              break;
          }
        }
        catch (InvalidProtocolBufferException e){
          throw new RuntimeException(e);
        }

        long fee = dbManager.getDynamicPropertiesStore().getTransactionFee() * bytesSize;
        throw new AccountResourceInsufficientException("Account or token pool insufficient balance[" + fee + "]");
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

  private boolean useTransactionFee4TokenPool(byte[] tokenKey, long bytes, TransactionTrace trace) {
      long bwFee = dbManager.getDynamicPropertiesStore().getTransactionFee() * bytes;
      if (consumeFeeTokenPool(tokenKey, bwFee)) {
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

  private void consumeForCreateNewAccount4TokenTransfer(Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    try {
      var ctx = contract.getParameter().unpack(TransferTokenContract.class);
      long fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
      if (consumeFeeTokenPool(Util.stringAsBytesUppercase(ctx.getTokenName()), fee)) {
        trace.setNetBill(0, fee);
        dbManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
        return;
      }
      throw new AccountResourceInsufficientException();
    }
    catch (InvalidProtocolBufferException e){
        logger.error("bad contract format {}", e.getMessage(), e);
        throw new ContractValidateException("bad contract format:" + e.getMessage());
    }
  }

  public boolean isContractCreateNewAccount(Contract contract) throws ContractValidateException {
    try {
      switch (contract.getType()) {
        case AccountCreateContract:
          return true;
        case TransferContract:
            var transferContract = contract.getParameter().unpack(TransferContract.class);
            return !dbManager.getAccountStore().has(transferContract.getToAddress().toByteArray());
        case TransferAssetContract:
            var transferAssetContract = contract.getParameter().unpack(TransferAssetContract.class);
           return !dbManager.getAccountStore().has(transferAssetContract.getToAddress().toByteArray());
        case TransferTokenContract:
            var transferTokenContract= contract.getParameter().unpack(TransferTokenContract.class);
            return !dbManager.getAccountStore().has(transferTokenContract.getToAddress().toByteArray());
        default:
          return false;
      }
    }
    catch (InvalidProtocolBufferException e){
      logger.error("bad contract format {}", e.getMessage(), e);
      throw new ContractValidateException("bad contract format:" + e.getMessage());
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
