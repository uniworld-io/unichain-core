package org.unx.consensus.base;

import org.unx.consensus.pbft.message.PbftBaseMessage;
import org.unx.core.capsule.BlockCapsule;

public interface PbftInterface {

  boolean isSyncing();

  void forwardMessage(PbftBaseMessage message);

  BlockCapsule getBlock(long blockNum) throws Exception;

}