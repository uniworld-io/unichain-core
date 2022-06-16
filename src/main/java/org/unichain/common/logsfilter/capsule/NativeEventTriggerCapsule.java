package org.unichain.common.logsfilter.capsule;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.logsfilter.EventPluginLoader;
import org.unichain.common.logsfilter.trigger.NativeEventTrigger;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.TransactionCapsule;

import java.util.Objects;
import java.util.Optional;

@Slf4j
public class NativeEventTriggerCapsule extends TriggerCapsule {
  @Getter
  @Setter
  NativeEventTrigger trigger;

  public NativeEventTriggerCapsule(TransactionCapsule txCap, BlockCapsule blockCapsule, long latestSolidifiedBlockNumber, NativeContractEvent event) {
    trigger = new NativeEventTrigger();
    trigger.setTopic(event.topic);
    trigger.setRawData(event.rawData);
    trigger.setSignature("");
    trigger.setIndex(event.index);
    if (Objects.nonNull(blockCapsule)) {
      trigger.setBlockHash(blockCapsule.getBlockId().toString());
      trigger.setTimeStamp(blockCapsule.getTimeStamp());
    }
    trigger.setTransactionId(txCap.getTransactionId().toString());
    trigger.setBlockNumber(txCap.getBlockNum());
    trigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);

    Optional.ofNullable(txCap.getInstance().getRawData().getContract(0))
            .ifPresent(contract -> Optional.ofNullable(contract.getType())
                    .ifPresent(contractType -> trigger.setContractType(contractType.toString())));
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postTransactionTrigger(trigger);
  }
}
