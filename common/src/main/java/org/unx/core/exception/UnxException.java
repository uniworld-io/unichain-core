package org.unx.core.exception;

public class UnxException extends Exception {

  public UnxException() {
    super();
    report();
  }

  public UnxException(String message) {
    super(message);
    report();
  }

  public UnxException(String message, Throwable cause) {
    super(message, cause);
    report();
  }

  protected void report(){

  }

}
