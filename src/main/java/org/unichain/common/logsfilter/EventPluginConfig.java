package org.unichain.common.logsfilter;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class EventPluginConfig {
  public static final String BLOCK_TRIGGER_NAME = "block";
  public static final String TRANSACTION_TRIGGER_NAME = "transaction";
  public static final String CONTRACTEVENT_TRIGGER_NAME = "contractevent";
  public static final String CONTRACTLOG_TRIGGER_NAME = "contractlog";
  public static final String NATIVEEVENT_TRIGGER_NAME = "nativeevent";

  public static final String SOLIDITY_TRIGGER_NAME = "solidity";
  public static final String SOLIDITY_EVENT_NAME = "solidityevent";
  public static final String SOLIDITY_LOG_NAME = "soliditylog";

  @Getter
  @Setter
  private String pluginPath;

  @Getter
  @Setter
  private String serverAddress;

  @Getter
  @Setter
  private String dbConfig;

  @Getter
  @Setter
  private boolean useNativeQueue;

  @Getter
  @Setter
  private boolean enable;

  @Getter
  @Setter
  private boolean mongodb;

  @Getter
  @Setter
  private boolean kafka;

  @Getter
  @Setter
  private int bindPort;

  @Getter
  @Setter
  private int sendQueueLength;

  @Getter
  @Setter
  private List<TriggerConfig> triggerConfigList;

  public EventPluginConfig() {
    pluginPath = "";
    serverAddress = "";
    dbConfig = "";
    useNativeQueue = false;
    enable = false;
    bindPort = 0;
    sendQueueLength = 0;
    triggerConfigList = new ArrayList<>();
  }
}
