package org.unichain.core.exception;

public class VMMemoryOverflowException extends UnichainException {

  public VMMemoryOverflowException() {
    super("VM memory overflow");
  }

  public VMMemoryOverflowException(String message) {
    super(message);
  }

}
