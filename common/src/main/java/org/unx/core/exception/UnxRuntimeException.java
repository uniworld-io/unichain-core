package org.unx.core.exception;

public class UnxRuntimeException extends RuntimeException {

  public UnxRuntimeException() {
    super();
  }

  public UnxRuntimeException(String message) {
    super(message);
  }

  public UnxRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnxRuntimeException(Throwable cause) {
    super(cause);
  }

  protected UnxRuntimeException(String message, Throwable cause,
                                boolean enableSuppression,
                                boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }


}
