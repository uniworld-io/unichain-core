package org.unx.consensus.base;

import org.unx.core.capsule.BlockCapsule;

public interface BlockHandle {

  State getState();

  Object getLock();

  BlockCapsule produce(Param.Miner miner, long blockTime, long timeout);

}