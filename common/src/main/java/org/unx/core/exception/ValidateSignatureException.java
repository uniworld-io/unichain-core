package org.unx.core.exception;

import org.unx.common.prometheus.MetricKeys;
import org.unx.common.prometheus.MetricLabels;
import org.unx.common.prometheus.Metrics;

public class ValidateSignatureException extends UnxException {

  public ValidateSignatureException() {
    super();
  }

  public ValidateSignatureException(String message) {
    super(message);
  }

  protected void report() {
    Metrics.counterInc(MetricKeys.Counter.TXS, 1,
        MetricLabels.Counter.TXS_FAIL, MetricLabels.Counter.TXS_FAIL_SIG);
  }

}
