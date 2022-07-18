package org.unx.core.services.jsonrpc.types;

import static org.unx.core.services.jsonrpc.JsonRpcApiUtil.getEnergyUsageTotal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.Wallet;
import org.unx.core.capsule.BlockCapsule;
import org.unx.protos.Protocol.Block;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.TransactionInfo;

@JsonPropertyOrder(alphabetic = true)
public class BlockResult {

  @Getter
  @Setter
  private String number;
  @Getter
  @Setter
  private String hash;
  @Getter
  @Setter
  private String parentHash;
  @Getter
  @Setter
  private String nonce;
  @Getter
  @Setter
  private String sha3Uncles;
  @Getter
  @Setter
  private String logsBloom;
  @Getter
  @Setter
  private String transactionsRoot;
  @Getter
  @Setter
  private String stateRoot;
  @Getter
  @Setter
  private String receiptsRoot;
  @Getter
  @Setter
  private String miner;
  @Getter
  @Setter
  private String difficulty;
  @Getter
  @Setter
  private String totalDifficulty;
  @Getter
  @Setter
  private String extraData;
  @Getter
  @Setter
  private String size;
  @Getter
  @Setter
  private String gasLimit;
  @Getter
  @Setter
  private String gasUsed;
  @Getter
  @Setter
  private String timestamp;
  @Getter
  @Setter
  private Object[] transactions; //TransactionResult or byte32
  @Getter
  @Setter
  private String[] uncles;

  @Getter
  @Setter
  private String baseFeePerGas = null;
  @Getter
  @Setter
  private String mixHash = null;

  public BlockResult(Block block, boolean fullTx, Wallet wallet) {
    BlockCapsule blockCapsule = new BlockCapsule(block);

    number = ByteArray.toJsonHex(blockCapsule.getNum());
    hash = ByteArray.toJsonHex(blockCapsule.getBlockId().getBytes());
    parentHash =
        ByteArray.toJsonHex(block.getBlockHeader().getRawData().getParentHash().toByteArray());
    nonce = null; // no value
    sha3Uncles = null; // no value
    logsBloom = ByteArray.toJsonHex(new byte[256]); // no value
    transactionsRoot = ByteArray
        .toJsonHex(block.getBlockHeader().getRawData().getTxTrieRoot().toByteArray());
    stateRoot = ByteArray
        .toJsonHex(block.getBlockHeader().getRawData().getAccountStateRoot().toByteArray());
    receiptsRoot = null; // no value
    miner = ByteArray.toJsonHexAddress(blockCapsule.getWitnessAddress().toByteArray());
    difficulty = null; // no value
    totalDifficulty = null; // no value
    extraData = null; // no value
    size = ByteArray.toJsonHex(block.getSerializedSize());
    timestamp = ByteArray.toJsonHex(blockCapsule.getTimeStamp());

    long gasUsedInBlock = 0;
    long gasLimitInBlock = 0;

    List<Object> txes = new ArrayList<>();
    List<Transaction> transactionsList = block.getTransactionsList();
    List<TransactionInfo> transactionInfoList =
        wallet.getTransactionInfoByBlockNum(blockCapsule.getNum()).getTransactionInfoList();
    if (fullTx) {
      long energyFee = wallet.getEnergyFee(blockCapsule.getTimeStamp());

      for (int i = 0; i < transactionsList.size(); i++) {
        Transaction transaction = transactionsList.get(i);
        gasLimitInBlock += transaction.getRawData().getFeeLimit();

        long energyUsageTotal = getEnergyUsageTotal(transactionInfoList, i, blockCapsule.getNum());
        gasUsedInBlock += energyUsageTotal;

        txes.add(new TransactionResult(blockCapsule, i, transaction,
            energyUsageTotal, energyFee, wallet));
      }
    } else {
      for (int i = 0; i < transactionsList.size(); i++) {
        gasLimitInBlock += transactionsList.get(i).getRawData().getFeeLimit();
        gasUsedInBlock += getEnergyUsageTotal(transactionInfoList, i, blockCapsule.getNum());

        byte[] txHash = Sha256Hash
            .hash(CommonParameter.getInstance().isECKeyCryptoEngine(),
                transactionsList.get(i).getRawData().toByteArray());
        txes.add(ByteArray.toJsonHex(txHash));
      }
    }
    transactions = txes.toArray();

    gasLimit = ByteArray.toJsonHex(gasLimitInBlock);
    gasUsed = ByteArray.toJsonHex(gasUsedInBlock);
    uncles = new String[0];
  }
}