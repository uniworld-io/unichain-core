package org.unx.core.net.message;

import org.unx.common.utils.Sha256Hash;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.BlockCapsule.BlockId;
import org.unx.core.capsule.TransactionCapsule;

public class BlockMessage extends UnxMessage {

  private BlockCapsule block;

  public BlockMessage(byte[] data) throws Exception {
    super(data);
    this.type = MessageTypes.BLOCK.asByte();
    this.block = new BlockCapsule(getCodedInputStream(data));
    if (isFilter()) {
      compareBytes(data, block.getInstance().toByteArray());
      TransactionCapsule.validContractProto(block.getInstance().getTransactionsList());
    }
  }

  public BlockMessage(BlockCapsule block) {
    data = block.getData();
    this.type = MessageTypes.BLOCK.asByte();
    this.block = block;
  }

  public BlockId getBlockId() {
    return getBlockCapsule().getBlockId();
  }

  public BlockCapsule getBlockCapsule() {
    return block;
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  @Override
  public Sha256Hash getMessageId() {
    return getBlockCapsule().getBlockId();
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return new StringBuilder().append(super.toString()).append(block.getBlockId().getString())
        .append(", unx size: ").append(block.getTransactions().size()).append("\n").toString();
  }
}
