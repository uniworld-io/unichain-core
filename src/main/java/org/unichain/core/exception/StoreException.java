package org.unichain.core.exception;

public class StoreException extends UnichainException {

  public StoreException() {
    super();
  }

  public StoreException(String message) {
    super(message);
  }

  public StoreException(String message, Throwable cause) {
    super(message, cause);
  }
}
