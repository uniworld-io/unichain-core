package org.unichain.core.exception;

public class NonCommonBlockException extends UnichainException {

  public NonCommonBlockException() {
    super();
  }

  public NonCommonBlockException(String message) {
    super(message);
  }

  public NonCommonBlockException(String message, Throwable cause) {
    super(message, cause);
  }
}
