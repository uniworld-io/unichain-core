package org.unichain.common.logsfilter;

import org.pf4j.ExtensionPoint;

//@todo review plugin
public interface IPluginEventListener extends ExtensionPoint {
  void setServerAddress(String address);

  void setTopic(int eventType, String topic);

  void setDBConfig(String dbConfig);

  // start should be called after setServerAddress, setTopic, setDBConfig
  void start();

  void stop();

  void handleBlockEvent(Object trigger);

  void handleTransactionTrigger(Object trigger);

  void handleContractLogTrigger(Object trigger);

  void handleContractEventTrigger(Object trigger);

  //@todo new handler
  void handleSolidityTrigger(Object trigger);

  void handleSolidityLogTrigger(Object trigger);

  void handleSolidityEventTrigger(Object trigger);
}