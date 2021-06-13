package org.unichain.core.net.message;

import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction;

import java.util.List;

public class TransactionsMessage extends UnichainMessage {

  private Protocol.Transactions transactions;

  public TransactionsMessage(List<Transaction> unxs) {
    Protocol.Transactions.Builder builder = Protocol.Transactions.newBuilder();
    unxs.forEach(unx -> builder.addTransactions(unx));
    this.transactions = builder.build();
    this.type = MessageTypes.UNWS.asByte();
    this.data = this.transactions.toByteArray();
  }

  public TransactionsMessage(byte[] data) throws Exception {
    super(data);
    this.type = MessageTypes.UNWS.asByte();
    this.transactions = Protocol.Transactions.parseFrom(getCodedInputStream(data));
    if (isFilter()) {
      compareBytes(data, transactions.toByteArray());
      TransactionCapsule.validContractProto(transactions.getTransactionsList());
    }
  }

  public Protocol.Transactions getTransactions() {
    return transactions;
  }

  @Override
  public String toString() {
    return new StringBuilder().append(super.toString()).append("unx size: ")
        .append(this.transactions.getTransactionsList().size()).toString();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

}
