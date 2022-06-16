package org.unichain.common.logsfilter.trigger;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ContractLogTrigger extends ContractTrigger {
  /**
   * topic list produced by the smart contract LOG function
   */
  @Getter
  @Setter
  private List<String> topicList;

  /**
   * data produced by the smart contract LOG function
   */
  @Getter
  @Setter
  private String data;

  public ContractLogTrigger() {
    super();
    setTriggerName(Trigger.CONTRACT_LOG_TRIGGER_NAME);
  }

  public ContractLogTrigger(ContractEventTrigger eventTrigger) {
    super();
    setTriggerName(Trigger.CONTRACT_LOG_TRIGGER_NAME);

    setRawData(eventTrigger.getRawData());
    setLatestSolidifiedBlockNumber(eventTrigger.getLatestSolidifiedBlockNumber());
    setRemoved(eventTrigger.isRemoved());
    setUniqueId(eventTrigger.getUniqueId());
    setTransactionId(eventTrigger.getTransactionId());
    setContractAddress(eventTrigger.getContractAddress());
    setOriginAddress(eventTrigger.getOriginAddress());
    setCallerAddress("");
    setCreatorAddress(eventTrigger.getCreatorAddress());
    setBlockNumber(eventTrigger.getBlockNumber());
    setTimeStamp(eventTrigger.getTimeStamp());
    setBlockHash(eventTrigger.getBlockHash());
  }
}
