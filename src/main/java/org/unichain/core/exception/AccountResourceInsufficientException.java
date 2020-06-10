package org.unichain.core.exception;

public class AccountResourceInsufficientException extends UnichainException {

  public AccountResourceInsufficientException() {
    super("Insufficient bandwidth and balance to create new account");
  }

  public AccountResourceInsufficientException(String message) {
    super(message);
  }
}

