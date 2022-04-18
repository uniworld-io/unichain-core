package org.unichain.core.net.message;

import org.unichain.common.overlay.message.Message;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.protos.Protocol.Transaction;

public class TransactionMessage extends UnichainMessage {

  private TransactionCapsule transactionCapsule;

  public TransactionMessage(byte[] data) throws Exception {
    super(data);
    this.transactionCapsule = new TransactionCapsule(getCodedInputStream(data));
    this.type = MessageTypes.UNW.asByte();
    if (Message.isFilter()) {
      compareBytes(data, transactionCapsule.getInstance().toByteArray());
      transactionCapsule.validContractProto(transactionCapsule.getInstance().getRawData().getContract(0));
    }
  }

  public TransactionMessage(Transaction unx) {
    this.transactionCapsule = new TransactionCapsule(unx);
    this.type = MessageTypes.UNW.asByte();
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
