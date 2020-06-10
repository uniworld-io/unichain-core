package org.unichain.core.exception;

public class UnichainException extends Exception {

  public UnichainException() {
    super();
  }

  public UnichainException(String message) {
    super(message);
  }

  public UnichainException(String message, Throwable cause) {
    super(message, cause);
  }

}
