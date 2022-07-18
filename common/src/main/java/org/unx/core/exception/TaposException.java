package org.unx.core.exception;

import org.unx.common.prometheus.MetricKeys;
import org.unx.common.prometheus.MetricLabels;
import org.unx.common.prometheus.Metrics;

public class TaposException extends UnxException {

  public TaposException() {
    super();
  }

  public TaposException(String message) {
    super(message);
  }

  public TaposException(String message, Throwable cause) {
    super(message, cause);
  }

  protected void report() {
    Metrics.counterInc(MetricKeys.Counter.TXS, 1,
        MetricLabels.Counter.TXS_FAIL, MetricLabels.Counter.TXS_FAIL_TAPOS);
  }

}
