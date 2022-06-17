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
import org.unichain.protos.Contract.*;
import org.unichain.protos.Protocol.Transaction.Contract;

import java.util.List;
import java.util.Map;

import static org.unichain.core.capsule.urc721.Urc721ContractCapsule.URC721_CREATE_CONTRACT_FIELD_MINTER;

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

    if (tx.getResultSerializedSize() > Math.multiplyExact(Constant.MAX_RESULT_SIZE_IN_TX, contracts.size())) {
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
        bytesSize = Math.addExact(bytesSize, Constant.MAX_RESULT_SIZE_IN_TX);
      }

      logger.debug("unxId {}, bandwidth cost :{}", tx.getTransactionId(), bytesSize);
      trace.setNetBill(bytesSize, 0);
      byte[] address = TransactionCapsule.getOwner(contract);
      AccountCapsule ownerAccountCap = dbManager.getAccountStore().get(address);
      if (ownerAccountCap == null) {
        throw new ContractValidateException("account not exists");
      }

      /*
        - create new account fee include 2 parts:
          - getCreateAccountFee: default 1000 Gas
          - getCreateNewAccountFeeInSystemContract: default 0 Gas
        - this part is getCreateAccountFe only!
        - if charged getCreateAccountFe, ignore bandwidth fee
       */
      try {
        if (isContractCreateNewAccount(contract)) {
          switch (contract.getType()){
            case TransferTokenContract:
              consumeCreateNewAccountIfUrc30Transfer(ownerAccountCap, contract, trace);
              break;
            case Urc20TransferFromContract:
            case Urc20TransferContract:
            case Urc20ApproveContract:
              consumeCreateNewAccountIfUrc20Transfer(ownerAccountCap, contract, trace);
              break;
            default:
              consumeForCreateNewAccount(ownerAccountCap, trace);
              break;
          }

          //stop
          continue;
        }
      }
      catch (AccountResourceInsufficientException ex){
        throw new AccountResourceInsufficientException("Insufficient balance to create new account");
      }

      /**
        or else charge bw fee
       */
        try{
          switch (contract.getType()) {
            /**
             * urc30
             */
            case TransferTokenContract:
            {
              var tokenKey = Util.stringAsBytesUppercase(contract.getParameter().unpack(TransferTokenContract.class).getTokenName());
              if (useTransactionFee4Urc30Pool(tokenKey, bytesSize, trace))
                continue;
              break;
            }
            case WithdrawFutureTokenContract:
            {
              var tokenKey = Util.stringAsBytesUppercase(contract.getParameter().unpack(WithdrawFutureTokenContract.class).getTokenName());
              if (useTransactionFee4Urc30Pool(tokenKey, bytesSize, trace))
                continue;
              break;
            }

            /**
             * Urc20
             */
            case Urc20TransferFromContract:
            {
              var contractAddr = contract.getParameter().unpack(Urc20TransferFromContract.class).getAddress().toByteArray();
              if (useTransactionFee4Urc20Pool(contractAddr, bytesSize, trace))
                continue;
              break;
            }
            case Urc20TransferContract:
            {
              var contractAddr = contract.getParameter().unpack(Urc20TransferContract.class).getAddress().toByteArray();
              if (useTransactionFee4Urc20Pool(contractAddr, bytesSize, trace))
                continue;
              break;
            }
            case Urc20WithdrawFutureContract:
            {
              var tokenKey = contract.getParameter().unpack(Urc20WithdrawFutureContract.class).getAddress().toByteArray();
              if (useTransactionFee4Urc20Pool(tokenKey, bytesSize, trace))
                continue;
              break;
            }
            case Urc20ApproveContract:
            {
              var tokenKey = contract.getParameter().unpack(Urc20ApproveContract.class).getAddress().toByteArray();
              if (useTransactionFee4Urc20Pool(tokenKey, bytesSize, trace))
                continue;
              break;
            }
            case Urc20BurnContract:
            {
              var tokenKey = contract.getParameter().unpack(Urc20BurnContract.class).getAddress().toByteArray();
              if (useTransactionFee4Urc20Pool(tokenKey, bytesSize, trace))
                continue;
              break;
            }

            //other contract
            default:
            {
              if (useTransactionFee(ownerAccountCap, bytesSize, trace))
                continue;
              break;
            }
          }
        }
        catch (InvalidProtocolBufferException e){
          throw new RuntimeException(e);
        }

        long fee = dbManager.getDynamicPropertiesStore().getTransactionFee() * bytesSize;
        throw new AccountResourceInsufficientException("Account or token pool insufficient balance[" + fee + "]");
    }
  }

  protected boolean useTransactionFee(AccountCapsule accountCapsule, long bytes, TransactionTrace trace) {
    long bwFee = Math.multiplyExact(dbManager.getDynamicPropertiesStore().getTransactionFee(), bytes);
    if (consumeFee(accountCapsule, bwFee)) {
      trace.setNetBill(0, bwFee);
      dbManager.getDynamicPropertiesStore().addTotalTransactionCost(bwFee);
      return true;
    } else {
      return false;
    }
  }

  protected boolean useTransactionFee4Urc30Pool(byte[] tokenKey, long bytes, TransactionTrace trace) {
      long bwFee = Math.multiplyExact(dbManager.getDynamicPropertiesStore().getTransactionFee(), bytes);
      if (consumeFeeUrc30Pool(tokenKey, bwFee)) {
        trace.setNetBill(0, bwFee);
        dbManager.getDynamicPropertiesStore().addTotalTransactionCost(bwFee);
        return true;
      } else {
        return false;
      }
  }

  protected boolean useTransactionFee4Urc20Pool(byte[] contractAddr, long bytes, TransactionTrace trace) {
    long bwFee = Math.multiplyExact(dbManager.getDynamicPropertiesStore().getTransactionFee(), bytes);
    if (consumeFeeUrc20Pool(contractAddr, bwFee)) {
      trace.setNetBill(0, bwFee);
      dbManager.getDynamicPropertiesStore().addTotalTransactionCost(bwFee);
      return true;
    } else {
      return false;
    }
  }

  protected void consumeForCreateNewAccount(AccountCapsule ownerAccountCapsule, TransactionTrace trace) throws AccountResourceInsufficientException {
    long fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
    if (consumeFee(ownerAccountCapsule, fee)) {
      trace.setNetBill(0, fee);
      dbManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
    }
    else
    {
      throw new AccountResourceInsufficientException();
    }
  }

  protected void consumeCreateNewAccountIfUrc30Transfer(AccountCapsule ownerAccountCapsule, Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    try {
      var ctx = contract.getParameter().unpack(TransferTokenContract.class);
      var fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
      if (consumeFeeUrc30Pool(Util.stringAsBytesUppercase(ctx.getTokenName()), fee)) {
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

  protected void consumeCreateNewAccountIfUrc20Transfer(AccountCapsule ownerAccountCapsule, Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    try {
      byte[] contractAddr;
      switch (contract.getType()){
        case Urc20TransferContract:
          contractAddr = contract.getParameter().unpack(Urc20TransferContract.class).getAddress().toByteArray();
          break;
        case Urc20TransferFromContract:
          contractAddr = contract.getParameter().unpack(Urc20TransferFromContract.class).getAddress().toByteArray();
          break;
        case Urc20ApproveContract:
          contractAddr = contract.getParameter().unpack(Urc20ApproveContract.class).getAddress().toByteArray();
          break;
        default:
          throw new Exception("unexpected urc20 contract");
      }

      var fee = dbManager.getDynamicPropertiesStore().getCreateAccountFee();
      if (consumeFeeUrc20Pool(contractAddr, fee)) {
        trace.setNetBill(0, fee);
        dbManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
        return;
      }
      throw new AccountResourceInsufficientException();
    }
    catch (Exception e){
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
        {
          var ctx = contract.getParameter().unpack(TransferContract.class);
          return !dbManager.getAccountStore().has(ctx.getToAddress().toByteArray());
        }
        case TransferAssetContract:
        {
          var ctx = contract.getParameter().unpack(TransferAssetContract.class);
          return !dbManager.getAccountStore().has(ctx.getToAddress().toByteArray());
        }
        case TransferTokenContract:
        {
          var ctx= contract.getParameter().unpack(TransferTokenContract.class);
          return !dbManager.getAccountStore().has(ctx.getToAddress().toByteArray());
        }
        case Urc20TransferFromContract:
        {
          var ctx= contract.getParameter().unpack(Urc20TransferFromContract.class);
          return !dbManager.getAccountStore().has(ctx.getTo().toByteArray());
        }
        case Urc20TransferContract:
        {
          var ctx= contract.getParameter().unpack(Urc20TransferContract.class);
          return !dbManager.getAccountStore().has(ctx.getTo().toByteArray());
        }
        case Urc20ApproveContract:
        {
          var ctx= contract.getParameter().unpack(Urc20ApproveContract.class);
          return !dbManager.getAccountStore().has(ctx.getSpender().toByteArray());
        }

        /**
         * urc721
         */
        case Urc721TransferFromContract:
        {
          var ctx= contract.getParameter().unpack(Urc721TransferFromContract.class);
          return !dbManager.getAccountStore().has(ctx.getTo().toByteArray());
        }
        case Urc721AddMinterContract:
        {
          var ctx= contract.getParameter().unpack(Urc721AddMinterContract.class);
          return !dbManager.getAccountStore().has(ctx.getMinter().toByteArray());
        }
        case Urc721ApproveContract:
        {
          var ctx= contract.getParameter().unpack(Urc721ApproveContract.class);
          return !dbManager.getAccountStore().has(ctx.getTo().toByteArray()) && ctx.getApprove();
        }
        case Urc721CreateContract:
        {
          var ctx= contract.getParameter().unpack(Urc721CreateContract.class);
          return ctx.hasField(URC721_CREATE_CONTRACT_FIELD_MINTER) && !dbManager.getAccountStore().has(ctx.getMinter().toByteArray());
        }
        case Urc721MintContract:
        {
          var ctx= contract.getParameter().unpack(Urc721MintContract.class);
          return !dbManager.getAccountStore().has(ctx.getToAddress().toByteArray());
        }
        case Urc721SetApprovalForAllContract:
        {
          var ctx= contract.getParameter().unpack(Urc721SetApprovalForAllContract.class);
          return ctx.getApprove() && !dbManager.getAccountStore().has(ctx.getToAddress().toByteArray());
        }
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
