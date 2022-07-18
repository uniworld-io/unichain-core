package org.unx.common.logsfilter.capsule;

import lombok.Getter;
import lombok.Setter;
import org.unx.common.logsfilter.EventPluginLoader;
import org.unx.common.logsfilter.trigger.SolidityTrigger;

public class SolidityTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  private SolidityTrigger solidityTrigger;

  public SolidityTriggerCapsule(long latestSolidifiedBlockNum) {
    solidityTrigger = new SolidityTrigger();
    solidityTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNum);
  }

  public void setTimeStamp(long timeStamp) {
    solidityTrigger.setTimeStamp(timeStamp);
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postSolidityTrigger(solidityTrigger);
  }
}

