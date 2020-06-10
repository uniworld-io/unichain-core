package org.unichain.core.exception;

public class UnLinkedBlockException extends UnichainException {

  public UnLinkedBlockException() {
    super();
  }

  public UnLinkedBlockException(String message) {
    super(message);
  }

  public UnLinkedBlockException(String message, Throwable cause) {
    super(message, cause);
  }
}
