package org.unx.core.services.jsonrpc.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.Getter;
import org.unx.core.Wallet;
import org.unx.core.exception.JsonRpcInvalidParamsException;
import org.unx.core.services.jsonrpc.JsonRpc.FilterRequest;
import org.unx.core.services.jsonrpc.JsonRpc.LogFilterElement;

public class LogFilterAndResult extends FilterResult<LogFilterElement> {

  @Getter
  private final LogFilterWrapper logFilterWrapper;

  public LogFilterAndResult(FilterRequest fr, long currentMaxBlockNum, Wallet wallet)
      throws JsonRpcInvalidParamsException {
    this.logFilterWrapper = new LogFilterWrapper(fr, currentMaxBlockNum, wallet);
    result = new LinkedBlockingQueue<>();
    this.updateExpireTime();
  }

  @Override
  public void add(LogFilterElement logFilterElement) {
    result.add(logFilterElement);
  }

  @Override
  public List<LogFilterElement> popAll() {
    List<LogFilterElement> elements = new ArrayList<>();
    result.drainTo(elements);
    return elements;
  }
}
