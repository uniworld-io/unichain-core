package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.core.exception.BadItemException;
import org.unichain.protos.Protocol.Transaction;
import org.unichain.protos.Protocol.Transaction.Result;
import org.unichain.protos.Protocol.Transaction.Result.contractResult;

@Slf4j(topic = "capsule")
public class TransactionResultCapsule implements ProtoCapsule<Transaction.Result> {

  private Transaction.Result transactionResult;

  /**
   * constructor TransactionCapsule.
   */
  public TransactionResultCapsule(Transaction.Result unxRet) {
    this.transactionResult = unxRet;
  }

  public TransactionResultCapsule(byte[] data) throws BadItemException {
    try {
      this.transactionResult = Transaction.Result.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      throw new BadItemException("TransactionResult proto data parse exception");
    }
  }

  public TransactionResultCapsule() {
    this.transactionResult = Transaction.Result.newBuilder().build();
  }

  public TransactionResultCapsule(contractResult code) {
    this.transactionResult = Transaction.Result.newBuilder().setContractRet(code).build();
  }

  public TransactionResultCapsule(Transaction.Result.code code, long fee) {
    this.transactionResult = Transaction.Result.newBuilder().setRet(code).setFee(fee).build();
  }

  public void setStatus(long fee, Transaction.Result.code code) {
    long oldValue = transactionResult.getFee();
    this.transactionResult = this.transactionResult.toBuilder()
        .setFee(Math.addExact(oldValue, fee))
        .setRet(code).build();
  }

  public long getFee() {
    return transactionResult.getFee();
  }

  public void setUnfreezeAmount(long amount) {
    this.transactionResult = this.transactionResult.toBuilder().setUnfreezeAmount(amount).build();
  }

  public long getUnfreezeAmount() {
    return transactionResult.getUnfreezeAmount();
  }

  public void setAssetIssueID(String id) {
    this.transactionResult = this.transactionResult.toBuilder().setAssetIssueID(id).build();
  }

  public String getAssetIssueID() {
    return transactionResult.getAssetIssueID();
  }

  public void setWithdrawAmount(long amount) {
    this.transactionResult = this.transactionResult.toBuilder().setWithdrawAmount(amount).build();
  }

  public long getWithdrawAmount() {
    return transactionResult.getWithdrawAmount();
  }

  public void setExchangeReceivedAmount(long amount) {
    this.transactionResult = this.transactionResult.toBuilder().setExchangeReceivedAmount(amount)
        .build();
  }

  public long getExchangeReceivedAmount() {
    return transactionResult.getExchangeReceivedAmount();
  }


  public void setExchangeWithdrawAnotherAmount(long amount) {
    this.transactionResult = this.transactionResult.toBuilder()
        .setExchangeWithdrawAnotherAmount(amount)
        .build();
  }

  public long getExchangeWithdrawAnotherAmount() {
    return transactionResult.getExchangeWithdrawAnotherAmount();
  }


  public void setExchangeInjectAnotherAmount(long amount) {
    this.transactionResult = this.transactionResult.toBuilder()
        .setExchangeInjectAnotherAmount(amount)
        .build();
  }

  public long getExchangeId() {
    return transactionResult.getExchangeId();
  }

  public void setExchangeId(long id) {
    this.transactionResult = this.transactionResult.toBuilder()
        .setExchangeId(id)
        .build();
  }

  public long getExchangeInjectAnotherAmount() {
    return transactionResult.getExchangeInjectAnotherAmount();
  }

  public void setFee(long fee) {
    this.transactionResult = this.transactionResult.toBuilder().setFee(fee).build();
  }

  public void setErrorCode(Transaction.Result.code code) {
    this.transactionResult = this.transactionResult.toBuilder().setRet(code).build();
  }

  @Override
  public byte[] getData() {
    return this.transactionResult.toByteArray();
  }

  @Override
  public Result getInstance() {
    return this.transactionResult;
  }
}