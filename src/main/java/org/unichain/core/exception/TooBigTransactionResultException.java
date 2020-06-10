package org.unichain.core.exception;

public class TooBigTransactionResultException extends UnichainException {

  public TooBigTransactionResultException() {
    super("too big transaction result");
  }

  public TooBigTransactionResultException(String message) {
    super(message);
  }
}
