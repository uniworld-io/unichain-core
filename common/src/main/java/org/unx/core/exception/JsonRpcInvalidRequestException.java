package org.unx.core.exception;

public class JsonRpcInvalidRequestException extends UnxException {

  public JsonRpcInvalidRequestException() {
    super();
  }

  public JsonRpcInvalidRequestException(String message) {
    super(message);
  }

  public JsonRpcInvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}