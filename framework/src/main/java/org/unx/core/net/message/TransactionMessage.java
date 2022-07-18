package org.unx.core.net.message;

import org.unx.common.overlay.message.Message;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.protos.Protocol.Transaction;

public class TransactionMessage extends UnxMessage {

  private TransactionCapsule transactionCapsule;

  public TransactionMessage(byte[] data) throws Exception {
    super(data);
    this.transactionCapsule = new TransactionCapsule(getCodedInputStream(data));
    this.type = MessageTypes.UNX.asByte();
    if (Message.isFilter()) {
      compareBytes(data, transactionCapsule.getInstance().toByteArray());
      transactionCapsule
          .validContractProto(transactionCapsule.getInstance().getRawData().getContract(0));
    }
  }

  public TransactionMessage(Transaction unx) {
    this.transactionCapsule = new TransactionCapsule(unx);
    this.type = MessageTypes.UNX.asByte();
    this.data = unx.toByteArray();
  }

  @Override
  public String toString() {
    return new StringBuilder().append(super.toString())
        .append("messageId: ").append(super.getMessageId()).toString();
  }

  @Override
  public Sha256Hash getMessageId() {
    return this.transactionCapsule.getTransactionId();
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  public TransactionCapsule getTransactionCapsule() {
    return this.transactionCapsule;
  }
}
