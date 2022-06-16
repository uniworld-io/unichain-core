package org.unichain.common.logsfilter.capsule;

import lombok.Getter;
import lombok.Setter;
import org.unichain.common.logsfilter.EventPluginLoader;
import org.unichain.common.logsfilter.trigger.BlockLogTrigger;
import org.unichain.core.capsule.BlockCapsule;

public class BlockLogTriggerCapsule extends TriggerCapsule {

  @Getter
  @Setter
  BlockLogTrigger blockLogTrigger;

  public BlockLogTriggerCapsule(BlockCapsule block) {
    blockLogTrigger = new BlockLogTrigger();
    blockLogTrigger.setBlockHash(block.getBlockId().toString());
    blockLogTrigger.setTimeStamp(block.getTimeStamp());
    blockLogTrigger.setBlockNumber(block.getNum());
    blockLogTrigger.setTransactionSize(block.getTransactions().size());
    block.getTransactions().forEach(unw -> blockLogTrigger.getTransactionList().add(unw.getTransactionId().toString()));
  }

  public void setLatestSolidifiedBlockNumber(long latestSolidifiedBlockNumber) {
    blockLogTrigger.setLatestSolidifiedBlockNumber(latestSolidifiedBlockNumber);
  }

  @Override
  public void processTrigger() {
    EventPluginLoader.getInstance().postBlockTrigger(blockLogTrigger);
  }
}
