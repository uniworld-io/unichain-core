package org.unx.core.db.api;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.unx.core.ChainBaseManager;
import org.unx.core.capsule.ProposalCapsule;
import org.unx.core.store.DynamicPropertiesStore;
import org.unx.core.utils.ProposalUtil.ProposalType;
import org.unx.protos.Protocol.Proposal.State;

@Slf4j(topic = "DB")
public class EnergyPriceHistoryLoader {

  private final ChainBaseManager chainBaseManager;
  private List<ProposalCapsule> proposalCapsuleList = new ArrayList<>();

  public EnergyPriceHistoryLoader(ChainBaseManager chainBaseManager) {
    this.chainBaseManager = chainBaseManager;
  }

  public void doWork() {
    long start = System.currentTimeMillis();
    logger.info("Start to load energy price");

    getEnergyProposals();
    if (!proposalCapsuleList.isEmpty()) {
      String energyPriceHistory = parseProposalsToStr();
      chainBaseManager.getDynamicPropertiesStore().saveEnergyPriceHistory(energyPriceHistory);
    }
    finish();

    logger.info(
        "Complete energy price load, total time: {} milliseconds, total proposal count: {}",
        System.currentTimeMillis() - start, proposalCapsuleList.size());
  }

  public void getEnergyProposals() {
    proposalCapsuleList = chainBaseManager.getProposalStore()
        .getSpecifiedProposals(State.APPROVED, ProposalType.ENERGY_FEE.getCode());
  }

  public String parseProposalsToStr() {
    StringBuilder builder = new StringBuilder(DynamicPropertiesStore.DEFAULT_ENERGY_PRICE_HISTORY);

    for (ProposalCapsule proposalCapsule : proposalCapsuleList) {
      builder.append(",")
          .append(proposalCapsule.getExpirationTime())
          .append(":")
          .append(proposalCapsule.getParameters().get(ProposalType.ENERGY_FEE.getCode()));
    }

    return builder.toString();
  }

  public void finish() {
    chainBaseManager.getDynamicPropertiesStore().saveEnergyPriceHistoryDone(1);
  }

}
