package org.unx.core.exception;

public class JsonRpcMethodNotFoundException extends UnxException {

  public JsonRpcMethodNotFoundException() {
    super();
  }

  public JsonRpcMethodNotFoundException(String msg) {
    super(msg);
  }

  public JsonRpcMethodNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}