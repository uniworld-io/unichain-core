package org.unichain.core.exception;

public class DupTransactionException extends UnichainException {

  public DupTransactionException() {
    super();
  }

  public DupTransactionException(String message) {
    super(message);
  }
}
