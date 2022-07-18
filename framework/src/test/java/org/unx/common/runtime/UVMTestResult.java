package org.unx.common.runtime;

import lombok.extern.slf4j.Slf4j;
import org.unx.core.capsule.ReceiptCapsule;

@Slf4j
public class UVMTestResult {

  private Runtime runtime;
  private ReceiptCapsule receipt;
  private byte[] contractAddress;

  public UVMTestResult(Runtime runtime, ReceiptCapsule receipt, byte[] contractAddress) {
    this.runtime = runtime;
    this.receipt = receipt;
    this.contractAddress = contractAddress;
  }

  public byte[] getContractAddress() {
    return contractAddress;
  }

  public UVMTestResult setContractAddress(byte[] contractAddress) {
    this.contractAddress = contractAddress;
    return this;
  }

  public Runtime getRuntime() {
    return runtime;
  }

  public UVMTestResult setRuntime(Runtime runtime) {
    this.runtime = runtime;
    return this;
  }

  public ReceiptCapsule getReceipt() {
    return receipt;
  }

  public UVMTestResult setReceipt(ReceiptCapsule receipt) {
    this.receipt = receipt;
    return this;
  }

}
