package org.unx.common.logsfilter.capsule;

import static org.unx.common.logsfilter.EventPluginLoader.matchFilter;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;
import org.pf4j.util.StringUtils;
import org.unx.common.crypto.Hash;
import org.unx.common.logsfilter.ContractEventParserAbi;
import org.unx.common.logsfilter.EventPluginLoader;
import org.unx.common.logsfilter.trigger.ContractEventTrigger;
import org.unx.common.logsfilter.trigger.ContractLogTrigger;
import org.unx.common.logsfilter.trigger.ContractTrigger;
import org.unx.common.runtime.vm.DataWord;
import org.unx.common.runtime.vm.LogInfo;
import org.unx.core.config.args.Args;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract.ABI;

@Slf4j(topic = "DB")
public class ContractTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private ContractTrigger contractTrigger;

  public ContractTriggerCapsule(ContractTrigger contractTrigger) {
    this.contractTrigger = contractTrigger;
  }

  public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
    contractTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
  }

  public void setBlockHash(String blockHash) {
    contractTrigger.setBlockHash(blockHash);
  }

  @Override
  public void processTrigger() {
    ContractTrigger event;
    boolean isEvent = false;
    LogInfo logInfo = contractTrigger.getLogInfo();
    ABI abi = contractTrigger.getAbi();
    List<DataWord> topics = logInfo.getTopics();

    String eventSignature = "";
    String eventSignatureFull = "fallback()";
    String entryName = "";
    ABI.Entry eventEntry = null;

    if (abi != null && abi.getEntrysCount() > 0 && topics != null && !topics.isEmpty()
        && !ArrayUtils.isEmpty(topics.get(0).getData())
        && Args.getInstance().getStorage().isContractParseSwitch()) {
      String logHash = topics.get(0).toString();

      for (ABI.Entry entry : abi.getEntrysList()) {
        if (entry.getType() != ABI.Entry.EntryType.Event || entry.getAnonymous()) {
          continue;
        }

        String signature = entry.getName() + "(";
        String signatureFull = entry.getName() + "(";
        StringBuilder signBuilder = new StringBuilder();
        StringBuilder signFullBuilder = new StringBuilder();
        for (ABI.Entry.Param param : entry.getInputsList()) {
          if (signBuilder.length() > 0) {
            signBuilder.append(",");
            signFullBuilder.append(",");
          }
          String type = param.getType();
          String name = param.getName();
          signBuilder.append(type);
          signFullBuilder.append(type);
          if (StringUtils.isNotNullOrEmpty(name)) {
            signFullBuilder.append(" ").append(name);
          }
        }
        signature += signBuilder.toString() + ")";
        signatureFull += signFullBuilder.toString() + ")";
        String sha3 = Hex.toHexString(Hash.sha3(signature.getBytes()));
        if (sha3.equals(logHash)) {
          eventSignature = signature;
          eventSignatureFull = signatureFull;
          entryName = entry.getName();
          eventEntry = entry;
          isEvent = true;
          break;
        }
      }
    }

    if (isEvent) {
      event = new ContractEventTrigger();
      ((ContractEventTrigger) event).setEventSignature(eventSignature);
      ((ContractEventTrigger) event).setEventSignatureFull(eventSignatureFull);
      ((ContractEventTrigger) event).setEventName(entryName);

      List<byte[]> topicList = logInfo.getClonedTopics();
      byte[] data = logInfo.getClonedData();

      ((ContractEventTrigger) event)
          .setTopicMap(ContractEventParserAbi.parseTopics(topicList, eventEntry));
      ((ContractEventTrigger) event)
          .setDataMap(ContractEventParserAbi.parseEventData(data, topicList, eventEntry));
    } else {
      event = new ContractLogTrigger();
      ((ContractLogTrigger) event).setTopicList(logInfo.getHexTopics());
      ((ContractLogTrigger) event).setData(logInfo.getHexData());
    }

    RawData rawData = new RawData(logInfo.getAddress(), logInfo.getTopics(), logInfo.getData());

    event.setRawData(rawData);

    event.setLatestSolidifiedBlockNumber(contractTrigger.getLatestSolidifiedBlockNumber());
    event.setRemoved(contractTrigger.isRemoved());
    event.setUniqueId(contractTrigger.getUniqueId());
    event.setTransactionId(contractTrigger.getTransactionId());
    event.setContractAddress(contractTrigger.getContractAddress());
    event.setOriginAddress(contractTrigger.getOriginAddress());
    event.setCallerAddress("");
    event.setCreatorAddress(contractTrigger.getCreatorAddress());
    event.setBlockNumber(contractTrigger.getBlockNumber());
    event.setTimeStamp(contractTrigger.getTimeStamp());
    event.setBlockHash(contractTrigger.getBlockHash());

    if (matchFilter(contractTrigger)) {
      if (isEvent) {
        if (EventPluginLoader.getInstance().isContractEventTriggerEnable()) {
          EventPluginLoader.getInstance().postContractEventTrigger((ContractEventTrigger) event);
        }

        if (EventPluginLoader.getInstance().isSolidityEventTriggerEnable()) {
          boolean result = Args.getSolidityContractEventTriggerMap().computeIfAbsent(event
              .getBlockNumber(), listBlk -> new LinkedBlockingQueue())
                  .offer((ContractEventTrigger) event);

          if (!result) {
            logger.info("too many triggers, solidity event trigger lost: {}",
                event.getUniqueId());
          }
        }

        // enable process contractEvent as contractLog
        if ((EventPluginLoader.getInstance().isContractLogTriggerEnable()
            && EventPluginLoader.getInstance().isContractLogTriggerRedundancy())
            || (EventPluginLoader.getInstance().isSolidityLogTriggerEnable()
            && EventPluginLoader.getInstance().isSolidityLogTriggerRedundancy())) {
          ContractLogTrigger logTrigger = new ContractLogTrigger((ContractEventTrigger) event);
          logTrigger.setTopicList(logInfo.getHexTopics());
          logTrigger.setData(logInfo.getHexData());

          if (EventPluginLoader.getInstance().isContractLogTriggerRedundancy()) {
            EventPluginLoader.getInstance().postContractLogTrigger(logTrigger);
          }

          if (EventPluginLoader.getInstance().isSolidityLogTriggerRedundancy()) {
            boolean result = Args.getSolidityContractLogTriggerMap().computeIfAbsent(event
                .getBlockNumber(), listBlk -> new LinkedBlockingQueue())
                .offer(logTrigger);

            if (!result) {
              logger.info("too many triggers, solidity log trigger lost: {}",
                  logTrigger.getUniqueId());
            }
          }
        }
      } else {
        if (EventPluginLoader.getInstance().isContractLogTriggerEnable()) {
          EventPluginLoader.getInstance().postContractLogTrigger((ContractLogTrigger) event);
        }

        if (EventPluginLoader.getInstance().isSolidityLogTriggerEnable()) {
          boolean result = Args.getSolidityContractLogTriggerMap().computeIfAbsent(event
              .getBlockNumber(), listBlk -> new LinkedBlockingQueue())
                  .offer((ContractLogTrigger) event);

          if (!result) {
            logger.info("too many triggers, solidity log trigger lost: {}",
                event.getUniqueId());
          }
        }
      }
    }
  }
}
