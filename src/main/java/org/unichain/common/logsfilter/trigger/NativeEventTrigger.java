package org.unichain.common.logsfilter.trigger;

import lombok.Getter;
import lombok.Setter;

public class NativeEventTrigger extends Trigger {

  @Getter
  @Setter
  private String id;

  @Override
  public void setTimeStamp(long ts) {
    super.timeStamp = ts;
  }

  @Getter
  @Setter
  private String blockHash;

  @Getter
  @Setter
  private long blockNumber = -1;

  @Getter
  @Setter
  private long latestSolidifiedBlockNumber;

  @Getter
  @Setter
  private String transactionId;

  @Getter
  @Setter
  private long index;

  @Getter
  @Setter
  private String contractAddress;

  @Getter
  @Setter
  private String contractType;

  @Getter
  @Setter
  private String topic;

  @Getter
  @Setter
  private String signature;

  @Getter
  @Setter
  private Object rawData;

  public NativeEventTrigger() {
    setTriggerName(Trigger.NATIVE_EVENT_TRIGGER_NAME);
  }
}
