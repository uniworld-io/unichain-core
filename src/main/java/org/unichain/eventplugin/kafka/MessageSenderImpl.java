package org.unichain.eventplugin.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.unichain.common.logsfilter.trigger.Trigger;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MessageSenderImpl{
    private static MessageSenderImpl instance = null;

    private String serverAddress = "";
    private boolean loaded = false;

    private Map<Integer, KafkaProducer> producerMap = new HashMap<>();

    private BlockingQueue<Object> triggerQueue = new LinkedBlockingQueue();

    private String blockTopic = "";
    private String transactionTopic = "";
    private String contractEventTopic = "";
    private String contractLogTopic = "";
    private String solidityTopic = "";
    private String solidityLogTopic = "";
    private String solidityEventTopic = "";


    private Thread triggerProcessThread;
    private boolean isRunTriggerProcessThread = true;


    public static MessageSenderImpl getInstance(){
        if (Objects.isNull(instance)) {
            synchronized (MessageSenderImpl.class){
                if (Objects.isNull(instance)){
                    instance = new MessageSenderImpl();
                }
            }
        }

        return instance;
    }

    public void setServerAddress(String address){
        this.serverAddress = address;
    }

    public void init(){

        if (loaded){
            return;
        }

        createProducer(Trigger.BLOCK_TRIGGER);
        createProducer(Trigger.TRANSACTION_TRIGGER);
        createProducer(Trigger.CONTRACT_LOG_TRIGGER);
        createProducer(Trigger.CONTRACT_EVENT_TRIGGER);
        createProducer(Trigger.SOLIDITY_TRIGGER);
        createProducer(Trigger.SOLIDITY_EVENT_TRIGGER);
        createProducer(Trigger.SOLIDITY_LOG_TRIGGER);

        triggerProcessThread = new Thread(triggerProcessLoop);
        triggerProcessThread.start();

        loaded = true;
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
        }
    }

    private KafkaProducer createProducer(int eventType){

        KafkaProducer producer = null;

        Thread currentThread = Thread.currentThread();
        ClassLoader savedClassLoader = currentThread.getContextClassLoader();

        currentThread.setContextClassLoader(null);

        Properties props = new Properties();
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("linger.ms", 1);
        props.put("bootstrap.servers", this.serverAddress);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        producer = new KafkaProducer<String, String>(props);

        producerMap.put(eventType, producer);

        currentThread.setContextClassLoader(savedClassLoader);

        return producer;
    }

    private void printTimestamp(String data){
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("hh:mm:ss:SSS");
        System.out.println(ft.format(date) + ": " + data);
    }

    public void sendKafkaRecord(int eventType, String kafkaTopic, Object data){
        KafkaProducer producer = producerMap.get(eventType);
        if (Objects.isNull(producer)){
            return;
        }

        ProducerRecord<String, String> record = new ProducerRecord(kafkaTopic, data);
        try {
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    logger.debug("sendKafkaRecord successfully");
                }
            });
        } catch (Exception e) {
            logger.error("sendKafkaRecord {}", e);
        }

        printTimestamp((String)data);
    }

    public void close() {
        for (Map.Entry<Integer, KafkaProducer> entry: producerMap.entrySet()){
            entry.getValue().close();
        }

        producerMap.clear();
    }

    public BlockingQueue<Object> getTriggerQueue(){
        return triggerQueue;
    }

    public void handleBlockEvent(Object data) {
        if (blockTopic == null || blockTopic.length() == 0){
            return;
        }

        MessageSenderImpl.getInstance().sendKafkaRecord(Trigger.BLOCK_TRIGGER, blockTopic, data);
    }

    public void handleTransactionTrigger(Object data) {
        if (Objects.isNull(data) || Objects.isNull(transactionTopic)){
            return;
        }

        MessageSenderImpl.getInstance().sendKafkaRecord(Trigger.TRANSACTION_TRIGGER, transactionTopic, data);
    }

    public void handleContractLogTrigger(Object data) {
        if (Objects.isNull(data) || Objects.isNull(contractLogTopic)){
            return;
        }

        MessageSenderImpl.getInstance().sendKafkaRecord(Trigger.CONTRACT_LOG_TRIGGER, contractLogTopic, data);
    }

    public void handleContractEventTrigger(Object data) {
        if (Objects.isNull(data) || Objects.isNull(contractEventTopic)){
            return;
        }

        MessageSenderImpl.getInstance().sendKafkaRecord(Trigger.CONTRACT_EVENT_TRIGGER, contractEventTopic, data);
    }

    public void handleSolidityTrigger(Object data) {
        if (Objects.isNull(data) || Objects.isNull(solidityTopic)){
            return;
        }
        MessageSenderImpl.getInstance().sendKafkaRecord(Trigger.SOLIDITY_TRIGGER, solidityTopic, data);
    }
    public void handleSolidityLogTrigger(Object data) {
        if (Objects.isNull(data) || Objects.isNull(solidityLogTopic)){
            return;
        }
        MessageSenderImpl.getInstance().sendKafkaRecord(Trigger.SOLIDITY_LOG_TRIGGER, solidityLogTopic, data);
    }
    public void handleSolidityEventTrigger(Object data) {
        if (Objects.isNull(data) || Objects.isNull(solidityEventTopic)){
            return;
        }
        MessageSenderImpl.getInstance().sendKafkaRecord(Trigger.SOLIDITY_EVENT_TRIGGER, solidityEventTopic, data);
    }

    private Runnable triggerProcessLoop =
            () -> {
                while (isRunTriggerProcessThread) {
                    try {
                        String triggerData = (String)triggerQueue.poll(1, TimeUnit.SECONDS);

                        if (Objects.isNull(triggerData)){
                            continue;
                        }

                        if (triggerData.contains(Trigger.BLOCK_TRIGGER_NAME)){
                            handleBlockEvent(triggerData);
                        }
                        else if (triggerData.contains(Trigger.TRANSACTION_TRIGGER_NAME)){
                            handleTransactionTrigger(triggerData);
                        }
                        else if (triggerData.contains(Trigger.CONTRACT_LOG_TRIGGER_NAME)){
                            handleContractLogTrigger(triggerData);
                        }
                        else if (triggerData.contains(Trigger.CONTRACT_EVENT_TRIGGER_NAME)){
                            handleContractEventTrigger(triggerData);
                        }
                        else if (triggerData.contains(Trigger.SOLIDITY_TRIGGER_NAME)) {
                            handleSolidityTrigger(triggerData);
                        }
                        else if (triggerData.contains(Trigger.SOLIDITY_LOG_TRIGGER_NAME)) {
                            handleSolidityLogTrigger(triggerData);
                        }
                        else if (triggerData.contains(Trigger.SOLIDITY_EVENT_TRIGGER_NAME)) {
                            handleSolidityEventTrigger(triggerData);
                        }
                    } catch (InterruptedException ex) {
                        logger.info(ex.getMessage());
                        Thread.currentThread().interrupt();
                    } catch (Exception ex) {
                        logger.error("unknown exception happened in process capsule loop", ex);
                    } catch (Throwable throwable) {
                        logger.error("unknown throwable happened in process capsule loop", throwable);
                    }
                }
            };
}
