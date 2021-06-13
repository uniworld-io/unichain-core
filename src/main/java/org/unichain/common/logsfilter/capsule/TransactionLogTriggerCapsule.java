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
  TransactionLogTrigger transactionLogTrigger;

  public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
    transactionLogTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
  }

  public TransactionLogTriggerCapsule(TransactionCapsule unwCasule, BlockCapsule blockCapsule) {
    transactionLogTrigger = new TransactionLogTrigger();
    if (Objects.nonNull(blockCapsule)) {
      transactionLogTrigger.setBlockHash(blockCapsule.getBlockId().toString());
    }
    transactionLogTrigger.setTransactionId(unwCasule.getTransactionId().toString());
    transactionLogTrigger.setTimeStamp(blockCapsule.getTimeStamp());
    transactionLogTrigger.setBlockNumber(unwCasule.getBlockNum());

    TransactionTrace unwTrace = unwCasule.getUnxTrace();

    //result
    if (Objects.nonNull(unwCasule.getContractRet())) {
      transactionLogTrigger.setResult(unwCasule.getContractRet().toString());
    }

    if (Objects.nonNull(unwCasule.getInstance().getRawData())) {
      // feelimit
      transactionLogTrigger.setFeeLimit(unwCasule.getInstance().getRawData().getFeeLimit());

      Protocol.Transaction.Contract contract = unwCasule.getInstance().getRawData().getContract(0);
      Any contractParameter = null;
      // contract type
      if (Objects.nonNull(contract)) {
        Protocol.Transaction.Contract.ContractType contractType = contract.getType();
        if (Objects.nonNull(contractType)) {
          transactionLogTrigger.setContractType(contractType.toString());
        }

        contractParameter = contract.getParameter();

        transactionLogTrigger.setContractCallValue(TransactionCapsule.getCallValue(contract));
      }

      if (Objects.nonNull(contractParameter) && Objects.nonNull(contract)) {
        try {
          if (contract.getType() == TransferContract) {
            TransferContract contractTransfer = contractParameter.unpack(TransferContract.class);

            if (Objects.nonNull(contractTransfer)) {
              transactionLogTrigger.setAssetName("unw");

              if (Objects.nonNull(contractTransfer.getOwnerAddress())) {
                transactionLogTrigger.setFromAddress(
                    Wallet.encode58Check(contractTransfer.getOwnerAddress().toByteArray()));
              }

              if (Objects.nonNull(contractTransfer.getToAddress())) {
                transactionLogTrigger.setToAddress(
                    Wallet.encode58Check(contractTransfer.getToAddress().toByteArray()));
              }

              transactionLogTrigger.setAssetAmount(contractTransfer.getAmount());
            }

          } else if (contract.getType() == TransferAssetContract) {
            TransferAssetContract contractTransfer = contractParameter
                .unpack(TransferAssetContract.class);

            if (Objects.nonNull(contractTransfer)) {
              if (Objects.nonNull(contractTransfer.getAssetName())) {
                transactionLogTrigger.setAssetName(contractTransfer.getAssetName().toStringUtf8());
              }

              if (Objects.nonNull(contractTransfer.getOwnerAddress())) {
                transactionLogTrigger.setFromAddress(
                    Wallet.encode58Check(contractTransfer.getOwnerAddress().toByteArray()));
              }

              if (Objects.nonNull(contractTransfer.getToAddress())) {
                transactionLogTrigger.setToAddress(
                    Wallet.encode58Check(contractTransfer.getToAddress().toByteArray()));
              }
              transactionLogTrigger.setAssetAmount(contractTransfer.getAmount());
            }
          }
        } catch (Exception e) {
          logger.error("failed to load transferAssetContract, error'{}'", e);
        }
      }
    }

    // receipt
    if (Objects.nonNull(unwTrace) && Objects.nonNull(unwTrace.getReceipt())) {
      transactionLogTrigger.setEnergyFee(unwTrace.getReceipt().getEnergyFee());
      transactionLogTrigger.setOriginEnergyUsage(unwTrace.getReceipt().getOriginEnergyUsage());
      transactionLogTrigger.setEnergyUsageTotal(unwTrace.getReceipt().getEnergyUsageTotal());
      transactionLogTrigger.setNetUsage(unwTrace.getReceipt().getNetUsage());
      transactionLogTrigger.setNetFee(unwTrace.getReceipt().getNetFee());
      transactionLogTrigger.setEnergyUsage(unwTrace.getReceipt().getEnergyUsage());
    }

    // program result
    if (Objects.nonNull(unwTrace) && Objects.nonNull(unwTrace.getRuntime()) &&  Objects.nonNull(unwTrace.getRuntime().getResult())) {
      ProgramResult programResult = unwTrace.getRuntime().getResult();
      ByteString contractResult = ByteString.copyFrom(programResult.getHReturn());
      ByteString contractAddress = ByteString.copyFrom(programResult.getContractAddress());

      if (Objects.nonNull(contractResult) && contractResult.size() > 0) {
        transactionLogTrigger.setContractResult(Hex.toHexString(contractResult.toByteArray()));
      }

      if (Objects.nonNull(contractAddress) && contractAddress.size() > 0) {
        transactionLogTrigger
            .setContractAddress(Wallet.encode58Check((contractAddress.toByteArray())));
      }

      // internal transaction
      transactionLogTrigger.setInternalTrananctionList(
          getInternalTransactionList(programResult.getInternalTransactions()));
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
    EventPluginLoader.getInstance().postTransactionTrigger(transactionLogTrigger);
  }
}
