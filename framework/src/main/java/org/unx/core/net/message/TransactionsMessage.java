package org.unx.core.net.message;

import java.util.List;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.Transaction;

public class TransactionsMessage extends UnxMessage {

  private Protocol.Transactions transactions;

  public TransactionsMessage(List<Transaction> unxs) {
    Protocol.Transactions.Builder builder = Protocol.Transactions.newBuilder();
    unxs.forEach(unx -> builder.addTransactions(unx));
    this.transactions = builder.build();
    this.type = MessageTypes.UNXS.asByte();
    this.data = this.transactions.toByteArray();
  }

  public TransactionsMessage(byte[] data) throws Exception {
    super(data);
    this.type = MessageTypes.UNXS.asByte();
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
