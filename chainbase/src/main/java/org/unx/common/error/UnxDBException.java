package org.unx.common.error;

public class UnxDBException extends RuntimeException {
    public UnxDBException() {
  }

    public UnxDBException(String s) {
    super(s);
  }

    public UnxDBException(String s, Throwable throwable) {
    super(s, throwable);
  }

    public UnxDBException(Throwable throwable) {
    super(throwable);
  }
}
