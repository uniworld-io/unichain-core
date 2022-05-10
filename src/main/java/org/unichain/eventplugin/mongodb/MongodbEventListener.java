package org.unichain.eventplugin.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.unichain.common.logsfilter.IPluginEventListener;

import java.util.Objects;

@Slf4j(topic = "MongoDBEventListener")
public class MongodbEventListener implements IPluginEventListener {
    @Override
    public void setServerAddress(String address) {
        if (Objects.isNull(address) || address.length() == 0){
            return;
        }
        MongodbSenderImpl.getInstance().setServerAddress(address);
    }

    @Override
    public void setTopic(int eventType, String topic) {
        MongodbSenderImpl.getInstance().setTopic(eventType, topic);
    }

    @Override
    public void setDBConfig(String dbConfig) {
        MongodbSenderImpl.getInstance().setDBConfig(dbConfig);
    }

    @Override
    public void start() {
        MongodbSenderImpl.getInstance().init();
    }

    @Override
    public void stop() {
        MongodbSenderImpl.getInstance().close();
    }

    @Override
    public void handleBlockEvent(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleBlockEvent --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleTransactionTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleTransactionTrigger --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleContractLogTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleContractLogTrigger --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleContractEventTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleContractEventTrigger --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleNativeEventTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleNativeEventTrigger --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleSolidityTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleSolidityTrigger --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleSolidityLogTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleSolidityLogTrigger --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleSolidityEventTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        logger.info("handleSolidityEventTrigger --> " + data);
        MongodbSenderImpl.getInstance().getTriggerQueue().offer(data);
    }
}
