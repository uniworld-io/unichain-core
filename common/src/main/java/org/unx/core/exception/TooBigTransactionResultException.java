package org.unx.core.exception;

public class TooBigTransactionResultException extends UnxException {

  public TooBigTransactionResultException() {
    super("too big transaction result");
  }

  public TooBigTransactionResultException(String message) {
    super(message);
  }
}
