package org.unichain.eventplugin.mongodb;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unichain.common.logsfilter.trigger.Trigger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.*;

@Slf4j
public class MongodbSenderImpl {
  private static MongodbSenderImpl instance = null;
  private static final Logger log = LoggerFactory.getLogger(MongodbSenderImpl.class);
  private ExecutorService service = Executors.newFixedThreadPool(8);

  private boolean loaded = false;
  private BlockingQueue<Object> triggerQueue = new LinkedBlockingQueue<>();

  private String blockTopic = "";
  private String transactionTopic = "";
  private String contractEventTopic = "";
  private String contractLogTopic = "";
  private String nativeEventTopic = "";
  private String solidityTopic = "";
  private String solidityEventTopic = "";
  private String solidityLogTopic = "";

  private Thread triggerProcessThread;
  private boolean isRunTriggerProcessThread = true;

  private MongoManager mongoManager;
  private Map<String, MongoTemplate> mongoTemplateMap;

  private String dbName;
  private String dbUserName;
  private String dbPassword;
  private int version; // 1: no index, 2: has index

  private MongoConfig mongoConfig;

  public static MongodbSenderImpl getInstance() {
    if (Objects.isNull(instance)) {
      synchronized (MongodbSenderImpl.class) {
        if (Objects.isNull(instance)) {
          instance = new MongodbSenderImpl();
        }
      }
    }

    return instance;
  }

  public void setDBConfig(String dbConfig) {
    if (StringUtils.isEmpty(dbConfig)) {
      return;
    }

    String[] params = dbConfig.split("\\|");
    if (params.length != 3 && params.length != 4) {
      return;
    }

    dbName = params[0];
    dbUserName = params[1];
    dbPassword = params[2];
    version = 1;

    if (params.length == 4) {
      version = Integer.valueOf(params[3]);
    }

    loadMongoConfig();
  }

  public void setServerAddress(final String serverAddress) {
    if (StringUtils.isEmpty(serverAddress)) {
      return;
    }

    String[] params = serverAddress.split(":");
    if (params.length != 2) {
      return;
    }

    String mongoHostName = "";
    int mongoPort = -1;

    try {
      mongoHostName = params[0];
      mongoPort = Integer.valueOf(params[1]);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    if (Objects.isNull(mongoConfig)) {
      mongoConfig = new MongoConfig();
    }

    mongoConfig.setHost(mongoHostName);
    mongoConfig.setPort(mongoPort);
  }

  public void init() {
    try {
      log.info("MongodbSenderImpl starting ...");
      if (loaded) {
        return;
      }
      if (Objects.isNull(mongoManager)) {
        mongoManager = new MongoManager();
        mongoManager.initConfig(mongoConfig);
      }

      mongoTemplateMap = new HashMap<>();
      createCollections();

      triggerProcessThread = new Thread(triggerProcessLoop);
      triggerProcessThread.start();

      loaded = true;
      log.info("MongodbSenderImpl started!");
    }
    catch (Exception e){
      log.error("MongodbSenderImpl start failed --> ", e);
      loaded = false;
    }
  }

  private void createCollections() {
    if (mongoConfig.enabledIndexes()) {
      Map<String, Boolean> indexOptions = new HashMap<>();
      indexOptions.put("blockNumber", true);
      mongoManager.createCollection(blockTopic, indexOptions);

      indexOptions = new HashMap<>();
      indexOptions.put("transactionId", true);
      mongoManager.createCollection(transactionTopic, indexOptions);

      indexOptions = new HashMap<>();
      indexOptions.put("latestSolidifiedBlockNumber", true);
      mongoManager.createCollection(solidityTopic, indexOptions);

      indexOptions = new HashMap<>();
      indexOptions.put("uniqueId", true);
      mongoManager.createCollection(solidityEventTopic, indexOptions);
      mongoManager.createCollection(contractEventTopic, indexOptions);

      indexOptions = new HashMap<>();
      indexOptions.put("uniqueId", true);
      indexOptions.put("contractAddress", false);
      mongoManager.createCollection(solidityLogTopic, indexOptions);
      mongoManager.createCollection(contractLogTopic, indexOptions);

      indexOptions = new HashMap<>();
      mongoManager.createCollection(nativeEventTopic, indexOptions);
    } else {
      mongoManager.createCollection(blockTopic);
      mongoManager.createCollection(transactionTopic);
      mongoManager.createCollection(contractLogTopic);
      mongoManager.createCollection(contractEventTopic);
      mongoManager.createCollection(solidityTopic);
      mongoManager.createCollection(solidityEventTopic);
      mongoManager.createCollection(solidityLogTopic);
      mongoManager.createCollection(nativeEventTopic);
    }

    createMongoTemplate(blockTopic);
    createMongoTemplate(transactionTopic);
    createMongoTemplate(contractLogTopic);
    createMongoTemplate(contractEventTopic);
    createMongoTemplate(solidityTopic);
    createMongoTemplate(solidityEventTopic);
    createMongoTemplate(solidityLogTopic);
    createMongoTemplate(nativeEventTopic);
  }

  private void loadMongoConfig() {
    if (Objects.isNull(mongoConfig)) {
      mongoConfig = new MongoConfig();
    }

    if (StringUtils.isEmpty(dbName)) {
      return;
    }

    Properties properties = new Properties();

    try {
      InputStream input = getClass().getClassLoader().getResourceAsStream("mongodb.properties");
      if (Objects.isNull(input)) {
        return;
      }
      properties.load(input);

      int connectionsPerHost = Integer.parseInt(properties.getProperty("mongo.connectionsPerHost"));
      int threadsAllowedToBlockForConnectionMultiplie = Integer.parseInt(properties.getProperty("mongo.threadsAllowedToBlockForConnectionMultiplier"));

      mongoConfig.setDbName(dbName);
      mongoConfig.setUsername(dbUserName);
      mongoConfig.setPassword(dbPassword);
      mongoConfig.setVersion(version);
      mongoConfig.setConnectionsPerHost(connectionsPerHost);
      mongoConfig.setThreadsAllowedToBlockForConnectionMultiplier(threadsAllowedToBlockForConnectionMultiplie);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private MongoTemplate createMongoTemplate(final String collectionName) {

    MongoTemplate template = mongoTemplateMap.get(collectionName);
    if (Objects.nonNull(template)) {
      return template;
    }

    template = new MongoTemplate(mongoManager) {
      @Override
      protected String collectionName() {
        return collectionName;
      }

      @Override
      protected <T> Class<T> getReferencedClass() {
        return null;
      }
    };

    mongoTemplateMap.put(collectionName, template);

    return template;
  }

  public void setTopic(int triggerType, String topic) {
    if (triggerType == Trigger.BLOCK_TRIGGER) {
      blockTopic = topic;
    } else if (triggerType == Trigger.TRANSACTION_TRIGGER) {
      transactionTopic = topic;
    } else if (triggerType == Trigger.CONTRACT_EVENT_TRIGGER) {
      contractEventTopic = topic;
    } else if (triggerType == Trigger.CONTRACT_LOG_TRIGGER) {
      contractLogTopic = topic;
    } else if (triggerType == Trigger.SOLIDITY_TRIGGER) {
      solidityTopic = topic;
    } else if (triggerType == Trigger.SOLIDITY_EVENT_TRIGGER) {
      solidityEventTopic = topic;
    } else if (triggerType == Trigger.SOLIDITY_LOG_TRIGGER) {
      solidityLogTopic = topic;
    } else if (triggerType == Trigger.NATIVE_EVENT_TRIGGER) {
      nativeEventTopic = topic;
    } else {
      return;
    }
  }

  public void close() {
  }

  public BlockingQueue<Object> getTriggerQueue() {
    return triggerQueue;
  }

  public void upsertEntityLong(MongoTemplate template, Object data, String indexKey) {
    String dataStr = (String) data;
    try {
      JSONObject jsStr = JSON.parseObject(dataStr);
      Long indexValue = jsStr.getLong(indexKey);
      if (indexValue != null) {
        template.upsertEntity(indexKey, indexValue, dataStr);
      } else {
        template.addEntity(dataStr);
      }
    } catch (Exception ex) {
      log.error("upsertEntityLong exception happened in parse object ", ex);
    }
  }

  public void upsertEntityString(MongoTemplate template, Object data, String indexKey) {
    String dataStr = (String) data;
    try {
      JSONObject jsStr = JSON.parseObject(dataStr);
      String indexValue = jsStr.getString(indexKey);
      if (indexValue != null) {
        template.upsertEntity(indexKey, indexValue, dataStr);
      } else {
        template.addEntity(dataStr);
      }
    } catch (Exception ex) {
      log.error("upsertEntityLong exception happened in parse object ", ex);
    }
  }

  public void handleBlockEvent(Object data) {
    if (blockTopic == null || blockTopic.length() == 0) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(blockTopic);
    if (Objects.nonNull(template)) {
      service.execute(new Runnable() {
        @Override
        public void run() {
          if (mongoConfig.enabledIndexes()) {
            upsertEntityLong(template, data, "blockNumber");
          } else {
            template.addEntity((String) data);
          }
        }
      });
    }
  }

  public void handleTransactionTrigger(Object data) {
    if (Objects.isNull(data) || Objects.isNull(transactionTopic)) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(transactionTopic);
    if (Objects.nonNull(template)) {
      service.execute(new Runnable() {
        @Override
        public void run() {
          if (mongoConfig.enabledIndexes()) {
            upsertEntityString(template, data, "transactionId");
          } else {
            template.addEntity((String) data);
          }
        }
      });
    }
  }

  public void handleSolidityTrigger(Object data) {
    if (Objects.isNull(data) || Objects.isNull(solidityTopic)) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(solidityTopic);
    if (Objects.nonNull(template)) {
      service.execute(() -> {
        if (mongoConfig.enabledIndexes()) {
          upsertEntityLong(template, data, "latestSolidifiedBlockNumber");
        } else {
          template.addEntity((String) data);
        }
      });
    }
  }

  public void handleInsertContractTrigger(MongoTemplate template, Object data, String indexKey) {
    if (mongoConfig.enabledIndexes()) {
      upsertEntityString(template, data, indexKey);
    } else {
      template.addEntity((String) data);
    }
  }

  // will not delete when removed is set to true
  public void handleContractLogTrigger(Object data) {
    if (Objects.isNull(data) || Objects.isNull(contractLogTopic)) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(contractLogTopic);
    if (Objects.nonNull(template)) {
      service.execute(new Runnable() {
        @Override
        public void run() {
          handleInsertContractTrigger(template, data, "uniqueId");
        }
      });
    }
  }

  public void handleContractEventTrigger(Object data) {
    if (Objects.isNull(data) || Objects.isNull(contractEventTopic)) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(contractEventTopic);
    if (Objects.nonNull(template)) {
      service.execute(() -> {
        String dataStr = (String) data;
        if (dataStr.contains("\"removed\":true")) {
          try {
            JSONObject jsStr = JSON.parseObject(dataStr);
            String uniqueId = jsStr.getString("uniqueId");
            if (uniqueId != null) {
              template.delete("uniqueId", uniqueId);
            }
          } catch (Exception ex) {
            log.error("unknown exception happened in parse object ", ex);
          }
        } else {
          handleInsertContractTrigger(template, data, "uniqueId");
        }
      });
    }
  }

  public void handleSolidityLogTrigger(Object data) {
    if (Objects.isNull(data) || Objects.isNull(solidityLogTopic)) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(solidityLogTopic);
    if (Objects.nonNull(template)) {
      service.execute(() -> handleInsertContractTrigger(template, data, "uniqueId"));
    }
  }

  public void handleSolidityEventTrigger(Object data) {
    if (Objects.isNull(data) || Objects.isNull(solidityEventTopic)) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(solidityEventTopic);
    if (Objects.nonNull(template)) {
      service.execute(() -> handleInsertContractTrigger(template, data, "uniqueId"));
    }
  }

  public void handleNativeEventTrigger(Object data) {
    if (Objects.isNull(data) || Objects.isNull(nativeEventTopic)) {
      return;
    }

    MongoTemplate template = mongoTemplateMap.get(nativeEventTopic);
    if (Objects.nonNull(template)) {
      service.execute(() -> handleInsertContractTrigger(template, data, "uniqueId"));
    }
  }

  private Runnable triggerProcessLoop =
      () -> {
        while (isRunTriggerProcessThread) {
          try {
            String triggerData = (String) triggerQueue.poll(1, TimeUnit.SECONDS);

            if (Objects.isNull(triggerData)) {
              continue;
            }

            if (triggerData.contains(Trigger.BLOCK_TRIGGER_NAME)) {
              handleBlockEvent(triggerData);
            } else if (triggerData.contains(Trigger.TRANSACTION_TRIGGER_NAME)) {
              handleTransactionTrigger(triggerData);
            } else if (triggerData.contains(Trigger.CONTRACT_LOG_TRIGGER_NAME)) {
              handleContractLogTrigger(triggerData);
            } else if (triggerData.contains(Trigger.CONTRACT_EVENT_TRIGGER_NAME)) {
              handleContractEventTrigger(triggerData);
            } else if (triggerData.contains(Trigger.SOLIDITY_TRIGGER_NAME)) {
              handleSolidityTrigger(triggerData);
            } else if (triggerData.contains(Trigger.SOLIDITY_LOG_TRIGGER_NAME)) {
              handleSolidityLogTrigger(triggerData);
            } else if (triggerData.contains(Trigger.SOLIDITY_EVENT_TRIGGER_NAME)) {
              handleSolidityEventTrigger(triggerData);
            } else if (triggerData.contains(Trigger.NATIVE_EVENT_TRIGGER_NAME)) {
              handleNativeEventTrigger(triggerData);
            }


          } catch (InterruptedException ex) {
            log.info(ex.getMessage());
            Thread.currentThread().interrupt();
          } catch (Exception ex) {
            log.error("unknown exception happened in process capsule loop", ex);
          } catch (Throwable throwable) {
            log.error("unknown throwable happened in process capsule loop", throwable);
          }
        }
      };
}
