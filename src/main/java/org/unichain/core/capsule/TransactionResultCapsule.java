package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.core.exception.BadItemException;
import org.unichain.protos.Protocol.Transaction;
import org.unichain.protos.Protocol.Transaction.Result;
import org.unichain.protos.Protocol.Transaction.Result.contractResult;

import java.util.ArrayList;
import java.util.List;

@Slf4j(topic = "capsule")
public class TransactionResultCapsule implements ProtoCapsule<Transaction.Result> {

  private Transaction.Result txResult;

  @Getter
  private List<NativeContractEvent> events;

  public TransactionResultCapsule(Transaction.Result unxRet) {
    this.txResult = unxRet;
    this.events = new ArrayList<>();
  }

  public TransactionResultCapsule(byte[] data) throws BadItemException {
    try {
      this.txResult = Transaction.Result.parseFrom(data);
      this.events = new ArrayList<>();
    } catch (InvalidProtocolBufferException e) {
      throw new BadItemException("TransactionResult proto data parse exception");
    }
  }

  public TransactionResultCapsule() {
    this.txResult = Transaction.Result.newBuilder().build();
    this.events = new ArrayList<>();
  }

  public TransactionResultCapsule(contractResult code) {
    this.txResult = Transaction.Result.newBuilder().setContractRet(code).build();
    this.events = new ArrayList<>();
  }

  public TransactionResultCapsule(Transaction.Result.code code, long fee) {
    this.txResult = Transaction.Result.newBuilder().setRet(code).setFee(fee).build();
    this.events = new ArrayList<>();
  }

  public void addEvent(NativeContractEvent event){
    this.events.add(event);
  }

  public void setStatus(long fee, Transaction.Result.code code) {
    long oldValue = txResult.getFee();
    this.txResult = this.txResult.toBuilder()
        .setFee(Math.addExact(oldValue, fee))
        .setRet(code).build();
  }

  public long getFee() {
    return txResult.getFee();
  }

  public void setUnfreezeAmount(long amount) {
    this.txResult = this.txResult.toBuilder().setUnfreezeAmount(amount).build();
  }

  public long getUnfreezeAmount() {
    return txResult.getUnfreezeAmount();
  }

  public void setAssetIssueID(String id) {
    this.txResult = this.txResult.toBuilder().setAssetIssueID(id).build();
  }

  public String getAssetIssueID() {
    return txResult.getAssetIssueID();
  }

  public void setWithdrawAmount(long amount) {
    this.txResult = this.txResult.toBuilder().setWithdrawAmount(amount).build();
  }

  public long getWithdrawAmount() {
    return txResult.getWithdrawAmount();
  }

  public void setExchangeReceivedAmount(long amount) {
    this.txResult = this.txResult.toBuilder().setExchangeReceivedAmount(amount)
        .build();
  }

  public long getExchangeReceivedAmount() {
    return txResult.getExchangeReceivedAmount();
  }

  public void setExchangeWithdrawAnotherAmount(long amount) {
    this.txResult = this.txResult.toBuilder()
        .setExchangeWithdrawAnotherAmount(amount)
        .build();
  }

  public long getExchangeWithdrawAnotherAmount() {
    return txResult.getExchangeWithdrawAnotherAmount();
  }

  public void setExchangeInjectAnotherAmount(long amount) {
    this.txResult = this.txResult.toBuilder()
        .setExchangeInjectAnotherAmount(amount)
        .build();
  }

  public long getExchangeId() {
    return txResult.getExchangeId();
  }

  public void setExchangeId(long id) {
    this.txResult = this.txResult.toBuilder()
        .setExchangeId(id)
        .build();
  }

  public long getExchangeInjectAnotherAmount() {
    return txResult.getExchangeInjectAnotherAmount();
  }

  public void setFee(long fee) {
    this.txResult = this.txResult.toBuilder().setFee(fee).build();
  }

  public void setErrorCode(Transaction.Result.code code) {
    this.txResult = this.txResult.toBuilder().setRet(code).build();
  }

  @Override
  public byte[] getData() {
    return this.txResult.toByteArray();
  }

  @Override
  public Result getInstance() {
    return this.txResult;
  }
}