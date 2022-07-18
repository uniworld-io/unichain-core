package org.unx.consensus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.consensus.base.ConsensusInterface;
import org.unx.consensus.base.Param;
import org.unx.consensus.dpos.DposService;
import org.unx.core.capsule.BlockCapsule;

@Slf4j(topic = "consensus")
@Component
public class Consensus {

  @Autowired
  private DposService dposService;

  private ConsensusInterface consensusInterface;

  public void start(Param param) {
    consensusInterface = dposService;
    consensusInterface.start(param);
  }

  public void stop() {
    consensusInterface = dposService;
    consensusInterface.stop();
  }

  public void receiveBlock(BlockCapsule blockCapsule) {
    consensusInterface.receiveBlock(blockCapsule);
  }

  public boolean validBlock(BlockCapsule blockCapsule) {
    return consensusInterface.validBlock(blockCapsule);
  }

  public boolean applyBlock(BlockCapsule blockCapsule) {
    return consensusInterface.applyBlock(blockCapsule);
  }

}