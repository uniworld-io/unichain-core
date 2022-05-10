package org.unichain.common.logsfilter;

import org.pf4j.ExtensionPoint;

//@todo review new trigger
public interface IPluginEventListener extends ExtensionPoint {
  void setServerAddress(String address);

  void setTopic(int eventType, String topic);

  void setDBConfig(String dbConfig);

  void start();

  void stop();

  void handleBlockEvent(Object trigger);

  void handleTransactionTrigger(Object trigger);

  void handleContractLogTrigger(Object trigger);

  void handleContractEventTrigger(Object trigger);

  void handleNativeEventTrigger(Object trigger);

  void handleSolidityTrigger(Object trigger);

  void handleSolidityLogTrigger(Object trigger);

  void handleSolidityEventTrigger(Object trigger);
}