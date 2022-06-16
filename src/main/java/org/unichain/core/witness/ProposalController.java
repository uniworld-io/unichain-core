package org.unichain.core.witness;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.core.capsule.ProposalCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.services.ProposalService;
import org.unichain.protos.Protocol.Proposal.State;

@Slf4j(topic = "witness")
public class ProposalController {

  @Setter
  @Getter
  private Manager manager;

  public static ProposalController createInstance(Manager manager) {
    ProposalController instance = new ProposalController();
    instance.setManager(manager);
    return instance;
  }


  public void processProposals() {
    long latestProposalNum = manager.getDynamicPropertiesStore().getLatestProposalNum();
    if (latestProposalNum == 0) {
      logger.info("latestProposalNum is 0,return");
      return;
    }

    long proposalNum = latestProposalNum;

    ProposalCapsule proposalCapsule = null;

    while (proposalNum > 0) {
      try {
        proposalCapsule = manager.getProposalStore().get(ProposalCapsule.calculateDbKey(proposalNum));
      } catch (Exception ex) {
        logger.error("", ex);
        continue;
      }

      if (proposalCapsule.hasProcessed()) {
        logger.info("Proposal has processed，id:[{}],skip it and before it", proposalCapsule.getID());
        break;
      }

      if (proposalCapsule.hasCanceled()) {
        logger.info("Proposal has canceled，id:[{}],skip it", proposalCapsule.getID());
        proposalNum--;
        continue;
      }

      long currentTime = manager.getDynamicPropertiesStore().getNextMaintenanceTime();
      if (proposalCapsule.hasExpired(currentTime)) {
        processProposal(proposalCapsule);
        proposalNum--;
        continue;
      }

      proposalNum--;
      logger.info("Proposal has not expired，id:[{}],skip it", proposalCapsule.getID());
    }
    logger.info("Processing proposals done, oldest proposal[{}]", proposalNum);
  }

  public void processProposal(ProposalCapsule proposal) {
    var activeWitnesses = this.manager.getWitnessScheduleStore().getActiveWitnesses();
    if(proposal.hasMostApprovals(activeWitnesses)) {
      logger.info("Processing proposal,id:{}, it has received most approvals, begin to set dynamic parameter:{}, and set proposal state as APPROVED", proposal.getID(), proposal.getParameters());
      ProposalService.process(manager, proposal);
      proposal.setState(State.APPROVED);
      manager.getProposalStore().put(proposal.createDbKey(), proposal);
    } else {
      logger.info("Processing proposal,id:{}, it has not received enough approvals, set proposal state as DISAPPROVED", proposal.getID());
      proposal.setState(State.DISAPPROVED);
      manager.getProposalStore().put(proposal.createDbKey(), proposal);
    }
  }
}
