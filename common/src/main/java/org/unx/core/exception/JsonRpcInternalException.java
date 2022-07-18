package org.unx.core.exception;

public class JsonRpcInternalException extends UnxException {

  public JsonRpcInternalException() {
    super();
  }

  public JsonRpcInternalException(String message) {
    super(message);
  }

  public JsonRpcInternalException(String message, Throwable cause) {
    super(message, cause);
  }
}