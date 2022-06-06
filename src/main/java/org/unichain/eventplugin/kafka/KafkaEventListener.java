package org.unichain.eventplugin.kafka;

import lombok.extern.slf4j.Slf4j;
import org.unichain.common.logsfilter.IPluginEventListener;

import java.util.Objects;

@Slf4j
public class KafkaEventListener implements IPluginEventListener {
    @Override
    public void setServerAddress(String address) {
        if (Objects.isNull(address) || address.length() == 0){
            return;
        }
        MessageSenderImpl.getInstance().setServerAddress(address);
    }

    @Override
    public void setTopic(int eventType, String topic) {
        MessageSenderImpl.getInstance().setTopic(eventType, topic);
    }

    @Override
    public void setDBConfig(String dbConfig) {
    }

    @Override
    public void start() {
        MessageSenderImpl.getInstance().init();
    }

    @Override
    public void stop() {
        MessageSenderImpl.getInstance().close();
    }

    @Override
    public void handleBlockEvent(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleTransactionTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleSolidityTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleSolidityLogTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleSolidityEventTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleContractLogTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleContractEventTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }

    @Override
    public void handleNativeEventTrigger(Object data) {
        if (Objects.isNull(data)){
            return;
        }
        MessageSenderImpl.getInstance().getTriggerQueue().offer(data);
    }
}
