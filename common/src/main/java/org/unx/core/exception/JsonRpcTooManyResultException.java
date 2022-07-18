package org.unx.core.exception;

public class JsonRpcTooManyResultException extends UnxException {

  public JsonRpcTooManyResultException() {
    super();
  }

  public JsonRpcTooManyResultException(String message) {
    super(message);
  }

  public JsonRpcTooManyResultException(String message, Throwable cause) {
    super(message, cause);
  }
}