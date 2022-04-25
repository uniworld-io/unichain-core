package org.unichain.common.logsfilter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.logsfilter.nativequeue.NativeMessageQueue;
import org.unichain.common.logsfilter.trigger.*;
import org.unichain.eventplugin.mongodb.MongodbEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class EventPluginLoader {

  private static EventPluginLoader instance;

  private List<IPluginEventListener> eventListeners = new ArrayList<>();

  private ObjectMapper objectMapper = new ObjectMapper();

  private String serverAddress;

  private String dbConfig;

  private List<TriggerConfig> triggerConfigList;

  private boolean blockLogTriggerEnable = false;

  private boolean transactionLogTriggerEnable = false;

  private boolean contractEventTriggerEnable = false;

  private boolean contractLogTriggerEnable = false;

  private FilterQuery filterQuery;

  private boolean useNativeQueue = false;

  public static EventPluginLoader getInstance() {
    if (Objects.isNull(instance)) {
      synchronized (EventPluginLoader.class) {
        if (Objects.isNull(instance)) {
          instance = new EventPluginLoader();
        }
      }
    }
    return instance;
  }

  private boolean launchNativeQueue(EventPluginConfig config){

    if (!NativeMessageQueue.getInstance().start(config.getBindPort(), config.getSendQueueLength())){
      return false;
    }

    if (Objects.isNull(triggerConfigList)){
      logger.error("trigger config is null");
      return false;
    }

    triggerConfigList.forEach(triggerConfig -> {
      setSingleTriggerConfig(triggerConfig);
    });

    return true;
  }

  private boolean launchEventPlugin(EventPluginConfig config){
    this.serverAddress = config.getServerAddress();
    this.dbConfig = config.getDbConfig();

    if (!startPlugins()) {
      logger.error("failed to load plugins");
      return false;
    }

    setPluginConfig();
    eventListeners.forEach(listener -> listener.start());
    return true;
  }

  public boolean start(EventPluginConfig config) {
    if (Objects.isNull(config)) {
      return false;
    }

    triggerConfigList = config.getTriggerConfigList();
    useNativeQueue = config.isUseNativeQueue();

    if (config.isUseNativeQueue()){
      return launchNativeQueue(config);
    }
    else
    {
      return launchEventPlugin(config);
    }
  }

  private void setPluginConfig() {
    eventListeners.forEach(listener -> listener.setServerAddress(this.serverAddress));
    eventListeners.forEach(listener -> listener.setDBConfig(this.dbConfig));
    triggerConfigList.forEach(triggerConfig -> setSingleTriggerConfig(triggerConfig));
  }

  private void setSingleTriggerConfig(TriggerConfig triggerConfig){
    if (EventPluginConfig.BLOCK_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())) {
      if (triggerConfig.isEnabled()) {
        blockLogTriggerEnable = true;
      } else {
        blockLogTriggerEnable = false;
      }

      if (!useNativeQueue){
        setPluginTopic(Trigger.BLOCK_TRIGGER, triggerConfig.getTopic());
      }
    } else if (EventPluginConfig.TRANSACTION_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())) {
      if (triggerConfig.isEnabled()) {
        transactionLogTriggerEnable = true;
      } else {
        transactionLogTriggerEnable = false;
      }

      if (!useNativeQueue){
        setPluginTopic(Trigger.TRANSACTION_TRIGGER, triggerConfig.getTopic());
      }

    } else if (EventPluginConfig.CONTRACTEVENT_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())) {
      if (triggerConfig.isEnabled()) {
        contractEventTriggerEnable = true;
      } else {
        contractEventTriggerEnable = false;
      }

      if (!useNativeQueue){
        setPluginTopic(Trigger.CONTRACTEVENT_TRIGGER, triggerConfig.getTopic());
      }

    } else if (EventPluginConfig.CONTRACTLOG_TRIGGER_NAME.equalsIgnoreCase(triggerConfig.getTriggerName())) {
      if (triggerConfig.isEnabled()) {
        contractLogTriggerEnable = true;
      } else {
        contractLogTriggerEnable = false;
      }

      if (!useNativeQueue){
        setPluginTopic(Trigger.CONTRACTLOG_TRIGGER, triggerConfig.getTopic());
      }
    }
  }

  public synchronized boolean isBlockLogTriggerEnable() {
    return blockLogTriggerEnable;
  }

  public synchronized boolean isTransactionLogTriggerEnable() {
    return transactionLogTriggerEnable;
  }

  public synchronized boolean isContractEventTriggerEnable() {
    return contractEventTriggerEnable;
  }

  public synchronized boolean isContractLogTriggerEnable() {
    return contractLogTriggerEnable;
  }

  private void setPluginTopic(int eventType, String topic) {
    eventListeners.forEach(listener -> listener.setTopic(eventType, topic));
  }

  private boolean startPlugins() {
    try {
      eventListeners.add(new MongodbEventListener());
      return true;
    }
    catch (Exception e){
      logger.error("failed to start plugins -->", e);
      return false;
    }
  }

  public void stopPlugins() {
    logger.info("EventPlugin stopping...");
    eventListeners.forEach(IPluginEventListener::stop);
    NativeMessageQueue.getInstance().stop();
    logger.info("EventPlugin stopped!");
  }

  public void postBlockTrigger(BlockLogTrigger trigger) {
    if (useNativeQueue){
      NativeMessageQueue.getInstance().publishTrigger(toJsonString(trigger), trigger.getTriggerName());
    }
    else {
      eventListeners.forEach(listener -> listener.handleBlockEvent(toJsonString(trigger)));
    }
  }

  public void postTransactionTrigger(TransactionLogTrigger trigger) {
    if (useNativeQueue){
      NativeMessageQueue.getInstance().publishTrigger(toJsonString(trigger), trigger.getTriggerName());
    }
    else {
      eventListeners.forEach(listener -> listener.handleTransactionTrigger(toJsonString(trigger)));
    }
  }

  public void postContractLogTrigger(ContractLogTrigger trigger) {
    if (useNativeQueue){
      NativeMessageQueue.getInstance().publishTrigger(toJsonString(trigger), trigger.getTriggerName());
    }
    else {
      eventListeners.forEach(listener -> listener.handleContractLogTrigger(toJsonString(trigger)));
    }
  }

  public void postContractEventTrigger(ContractEventTrigger trigger) {
    if (useNativeQueue){
      NativeMessageQueue.getInstance().publishTrigger(toJsonString(trigger), trigger.getTriggerName());
    }
    else {
      eventListeners.forEach(listener -> listener.handleContractEventTrigger(toJsonString(trigger)));
    }
  }

  private String toJsonString(Object data) {
    String jsonData = "";

    try {
      jsonData = objectMapper.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      logger.error("'{}'", e);
    }

    return jsonData;
  }

  public synchronized void setFilterQuery(FilterQuery filterQuery) {
    this.filterQuery = filterQuery;
  }

  public synchronized FilterQuery getFilterQuery() {
    return filterQuery;
  }
}
