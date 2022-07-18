package org.unx.core.services.jsonrpc.types;

import static org.unx.core.services.jsonrpc.JsonRpcApiUtil.getToAddress;
import static org.unx.core.services.jsonrpc.JsonRpcApiUtil.getTransactionAmount;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import lombok.Getter;
import lombok.ToString;
import org.unx.common.utils.ByteArray;
import org.unx.core.Wallet;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract;

@JsonPropertyOrder(alphabetic = true)
@ToString
public class TransactionResult {

  @Getter
  private final String hash;
  @Getter
  private final String nonce;
  @Getter
  private final String blockHash;
  @Getter
  private final String blockNumber;
  @Getter
  private final String transactionIndex;

  @Getter
  private final String from;
  @Getter
  private final String to;
  @Getter
  private final String gas;
  @Getter
  private final String gasPrice;
  @Getter
  private final String value;
  @Getter
  private final String input;
  @Getter
  private final String type = "0x0";

  @Getter
  private String v;
  @Getter
  private String r;
  @Getter
  private String s;

  private void parseSignature(Transaction tx) {

    if (tx.getSignatureCount() == 0) {
      v = null;
      r = null;
      s = null;
      return;
    }

    ByteString signature = tx.getSignature(0); // r[32] + s[32] + v[1]
    byte[] signData = signature.toByteArray();
    byte[] rByte = Arrays.copyOfRange(signData, 0, 32);
    byte[] sByte = Arrays.copyOfRange(signData, 32, 64);
    byte vByte = signData[64];
    if (vByte < 27) {
      vByte += 27;
    }
    v = ByteArray.toJsonHex(vByte);
    r = ByteArray.toJsonHex(rByte);
    s = ByteArray.toJsonHex(sByte);
  }

  public TransactionResult(BlockCapsule blockCapsule, int index, Protocol.Transaction tx,
      long energyUsageTotal, long energyFee, Wallet wallet) {
    byte[] txId = new TransactionCapsule(tx).getTransactionId().getBytes();
    hash = ByteArray.toJsonHex(txId);
    nonce = null; // no value
    blockHash = ByteArray.toJsonHex(blockCapsule.getBlockId().getBytes());
    blockNumber = ByteArray.toJsonHex(blockCapsule.getNum());
    transactionIndex = ByteArray.toJsonHex(index);

    if (!tx.getRawData().getContractList().isEmpty()) {
      Contract contract = tx.getRawData().getContract(0);
      byte[] fromByte = TransactionCapsule.getOwner(contract);
      byte[] toByte = getToAddress(tx);
      from = ByteArray.toJsonHexAddress(fromByte);
      to = ByteArray.toJsonHexAddress(toByte);
      value = ByteArray.toJsonHex(getTransactionAmount(contract, hash, wallet));
    } else {
      from = null;
      to = null;
      value = null;
    }

    gas = ByteArray.toJsonHex(energyUsageTotal);
    gasPrice = ByteArray.toJsonHex(energyFee);
    input = ByteArray.toJsonHex(tx.getRawData().getData().toByteArray());

    parseSignature(tx);
  }

  public TransactionResult(Transaction tx, Wallet wallet) {
    byte[] txid = new TransactionCapsule(tx).getTransactionId().getBytes();
    hash = ByteArray.toJsonHex(txid);
    nonce = null; // no value
    blockHash = "0x";
    blockNumber = "0x";
    transactionIndex = "0x";

    if (!tx.getRawData().getContractList().isEmpty()) {
      Contract contract = tx.getRawData().getContract(0);
      byte[] fromByte = TransactionCapsule.getOwner(contract);
      byte[] toByte = getToAddress(tx);
      from = ByteArray.toJsonHexAddress(fromByte);
      to = ByteArray.toJsonHexAddress(toByte);
      value = ByteArray.toJsonHex(getTransactionAmount(contract, hash, wallet));
    } else {
      from = null;
      to = null;
      value = null;
    }

    gas = "0x0";
    gasPrice = "0x";
    input = ByteArray.toJsonHex(tx.getRawData().getData().toByteArray());

    parseSignature(tx);
  }
}