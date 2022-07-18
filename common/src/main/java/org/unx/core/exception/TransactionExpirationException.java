package org.unx.core.exception;

import org.unx.common.prometheus.MetricKeys;
import org.unx.common.prometheus.MetricLabels;
import org.unx.common.prometheus.Metrics;

public class TransactionExpirationException extends UnxException {

  public TransactionExpirationException() {
    super();
  }

  public TransactionExpirationException(String message) {
    super(message);
  }

  protected void report() {
    Metrics.counterInc(MetricKeys.Counter.TXS, 1,
        MetricLabels.Counter.TXS_FAIL, MetricLabels.Counter.TXS_FAIL_EXPIRED);

  }

}