package org.unichain.common.logsfilter.capsule;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;
import org.unichain.common.logsfilter.EventPluginLoader;
import org.unichain.common.logsfilter.trigger.InternalTransactionPojo;
import org.unichain.common.logsfilter.trigger.TransactionLogTrigger;
import org.unichain.common.runtime.vm.program.InternalTransaction;
import org.unichain.common.runtime.vm.program.ProgramResult;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.db.TransactionTrace;
import org.unichain.protos.Contract.TransferAssetContract;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.unichain.protos.Protocol.Transaction.Contract.ContractType.TransferAssetContract;
import static org.unichain.protos.Protocol.Transaction.Contract.ContractType.TransferContract;

@Slf4j
public class TransactionLogTriggerCapsule extends TriggerCapsule {
  @Getter
  @Setter
  TransactionLogTrigger trigger;

  public TransactionLogTriggerCapsule(TransactionCapsule txCap, BlockCapsule blockCapsule, long latestSolidifiedBlockNumber) {
    trigger = new TransactionLogTrigger();
    trigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
    if (Objects.nonNull(blockCapsule)) {
      trigger.setBlockHash(blockCapsule.getBlockId().toString());
    }
    trigger.setTransactionId(txCap.getTransactionId().toString());
    trigger.setTimeStamp(blockCapsule.getTimeStamp());
    trigger.setBlockNumber(txCap.getBlockNum());

    TransactionTrace txTrace = txCap.getTxTrace();

    if (Objects.nonNull(txCap.getContractRet())) {
      trigger.setResult(txCap.getContractRet().toString());
    }

    if (Objects.nonNull(txCap.getInstance().getRawData())) {
      trigger.setFeeLimit(txCap.getInstance().getRawData().getFeeLimit());

      Protocol.Transaction.Contract contract = txCap.getInstance().getRawData().getContract(0);
      Any contractParameter = null;
      if (Objects.nonNull(contract)) {
        Protocol.Transaction.Contract.ContractType contractType = contract.getType();
        if (Objects.nonNull(contractType)) {
          trigger.setContractType(contractType.toString());
        }
        contractParameter = contract.getParameter();
        trigger.setContractCallValue(TransactionCapsule.getCallValue(contract));
      }

      if (Objects.nonNull(contractParameter) && Objects.nonNull(contract)) {
        try {
          if (contract.getType() == TransferContract) {
            TransferContract contractTransfer = contractParameter.unpack(TransferContract.class);

            if (Objects.nonNull(contractTransfer)) {
              trigger.setAssetName("unw");

              if (Objects.nonNull(contractTransfer.getOwnerAddress())) {
                trigger.setFromAddress(Wallet.encode58Check(contractTransfer.getOwnerAddress().toByteArray()));
              }

              if (Objects.nonNull(contractTransfer.getToAddress())) {
                trigger.setToAddress(Wallet.encode58Check(contractTransfer.getToAddress().toByteArray()));
              }

              trigger.setAssetAmount(contractTransfer.getAmount());
            }
          } else if (contract.getType() == TransferAssetContract) {
            TransferAssetContract contractTransfer = contractParameter.unpack(TransferAssetContract.class);

            if (Objects.nonNull(contractTransfer)) {
              if (Objects.nonNull(contractTransfer.getAssetName())) {
                trigger.setAssetName(contractTransfer.getAssetName().toStringUtf8());
              }

              if (Objects.nonNull(contractTransfer.getOwnerAddress())) {
                trigger.setFromAddress(Wallet.encode58Check(contractTransfer.getOwnerAddress().toByteArray()));
              }

              if (Objects.nonNull(contractTransfer.getToAddress())) {
                trigger.setToAddress(Wallet.encode58Check(contractTransfer.getToAddress().toByteArray()));
              }

              trigger.setAssetAmount(contractTransfer.getAmount());
            }
          }
        } catch (Exception e) {
          logger.error("failed to load transferAssetContract, error'{}'", e);
        }
      }
    }

    // receipt
    if (Objects.nonNull(txTrace) && Objects.nonNull(txTrace.getReceipt())) {
      trigger.setEnergyFee(txTrace.getReceipt().getEnergyFee());
      trigger.setOriginEnergyUsage(txTrace.getReceipt().getOriginEnergyUsage());
      trigger.setEnergyUsageTotal(txTrace.getReceipt().getEnergyUsageTotal());
      trigger.setNetUsage(txTrace.getReceipt().getNetUsage());
      trigger.setNetFee(txTrace.getReceipt().getNetFee());
      trigger.setEnergyUsage(txTrace.getReceipt().getEnergyUsage());
    }

    //program result
    if (Objects.nonNull(txTrace) && Objects.nonNull(txTrace.getRuntime()) &&  Objects.nonNull(txTrace.getRuntime().getResult())) {
      ProgramResult programResult = txTrace.getRuntime().getResult();
      ByteString contractResult = ByteString.copyFrom(programResult.getHReturn());
      ByteString contractAddress = ByteString.copyFrom(programResult.getContractAddress());

      if (Objects.nonNull(contractResult) && contractResult.size() > 0) {
        trigger.setContractResult(Hex.toHexString(contractResult.toByteArray()));
      }

      if (Objects.nonNull(contractAddress) && contractAddress.size() > 0) {
        trigger.setContractAddress(Wallet.encode58Check((contractAddress.toByteArray())));
      }

      trigger.setInternalTransactionList(getInternalTransactionList(programResult.getInternalTransactions()));
    }
  }

  private List<InternalTransactionPojo> getInternalTransactionList(
      List<InternalTransaction> internalTransactionList) {
    List<InternalTransactionPojo> pojoList = new ArrayList<>();

    internalTransactionList.forEach(internalTransaction -> {
      InternalTransactionPojo item = new InternalTransactionPojo();

      item.setHash(Hex.toHexString(internalTransaction.getHash()));
      item.setCallValue(internalTransaction.getValue());
      item.setTokenInfo(internalTransaction.getTokenInfo());
      item.setCaller_address(Hex.toHexString(internalTransaction.getSender()));
      item.setTransferTo_address(Hex.toHexString(internalTransaction.getTransferToAddress()));
      item.setData(Hex.toHexString(internalTransaction.getData()));
      item.setRejected(internalTransaction.isRejected());
      item.setNote(internalTransaction.getNote());

      pojoList.add(item);
    });

    return pojoList;
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postTransactionTrigger(trigger);
  }
}
