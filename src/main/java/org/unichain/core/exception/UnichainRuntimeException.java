package org.unichain.core.exception;

public class UnichainRuntimeException extends RuntimeException {

  public UnichainRuntimeException() {
    super();
  }

  public UnichainRuntimeException(String message) {
    super(message);
  }

  public UnichainRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnichainRuntimeException(Throwable cause) {
    super(cause);
  }

  protected UnichainRuntimeException(String message, Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }


}
