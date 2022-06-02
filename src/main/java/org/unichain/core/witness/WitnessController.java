package org.unichain.core.witness;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;
import org.joda.time.DateTime;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.StringUtil;
import org.unichain.common.utils.Time;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.VotesCapsule;
import org.unichain.core.capsule.WitnessCapsule;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.AccountStore;
import org.unichain.core.db.Manager;
import org.unichain.core.db.VotesStore;
import org.unichain.core.db.WitnessStore;
import org.unichain.core.exception.HeaderNotFound;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j(topic = "witness")
public class WitnessController {

  @Setter
  @Getter
  private Manager manager;

  private AtomicBoolean generatingBlock = new AtomicBoolean(false);

  public static WitnessController createInstance(Manager manager) {
    WitnessController instance = new WitnessController();
    instance.setManager(manager);
    return instance;
  }


  public void initWits() {
    // getWitnesses().clear();
    List<ByteString> witnessAddresses = new ArrayList<>();
    manager.getWitnessStore().getAllWitnesses().forEach(witnessCapsule -> {
      if (witnessCapsule.getIsJobs()) {
        witnessAddresses.add(witnessCapsule.getAddress());
      }
    });
    sortWitness(witnessAddresses);
    setActiveWitnesses(witnessAddresses);
    witnessAddresses.forEach(address -> {
      logger.info("initWits shuffled addresses:" + ByteArray.toHexString(address.toByteArray()));
    });
    setCurrentShuffledWitnesses(witnessAddresses);
  }

  public WitnessCapsule getWitnessByAddress(ByteString address) {
    return this.manager.getWitnessStore().get(address.toByteArray());
  }

  public List<ByteString> getActiveWitnesses() {
    return this.manager.getWitnessScheduleStore().getActiveWitnesses();
  }

  public void setActiveWitnesses(List<ByteString> addresses) {
    this.manager.getWitnessScheduleStore().saveActiveWitnesses(addresses);
  }

  public void addWitness(ByteString address) {
    List<ByteString> l = getActiveWitnesses();
    l.add(address);
    setActiveWitnesses(l);
  }

  public List<ByteString> getCurrentShuffledWitnesses() {
    return this.manager.getWitnessScheduleStore().getCurrentShuffledWitnesses();
  }

  public void setCurrentShuffledWitnesses(List<ByteString> addresses) {
    this.manager.getWitnessScheduleStore().saveCurrentShuffledWitnesses(addresses);
  }

  /**
   * get slot at time.
   */
  public long getSlotAtTime(long when) {
    long firstSlotTime = getSlotTime(1);
    if (when < firstSlotTime) {
      return 0;
    }
    logger
        .debug("nextFirstSlotTime:[{}],when[{}]", new DateTime(firstSlotTime), new DateTime(when));
    return (when - firstSlotTime) / ChainConstant.BLOCK_PRODUCED_INTERVAL + 1;
  }

  public BlockCapsule getGenesisBlock() {
    return manager.getGenesisBlock();
  }

  public BlockCapsule getHead() throws HeaderNotFound {
    return manager.getHead();
  }

  public boolean lastHeadBlockIsMaintenance() {
    return manager.lastHeadBlockIsMaintenance();
  }

  /**
   * get absolute Slot At Time
   */
  public long getAbSlotAtTime(long when) {
    return (when - getGenesisBlock().getTimeStamp()) / ChainConstant.BLOCK_PRODUCED_INTERVAL;
  }

  /**
   * get slot time.
   */
  public long getSlotTime(long slotNum) {
    if (slotNum == 0) {
      return Time.getCurrentMillis();
    }
    long interval = ChainConstant.BLOCK_PRODUCED_INTERVAL;

    if (manager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 0) {
      return getGenesisBlock().getTimeStamp() + slotNum * interval;
    }

    if (lastHeadBlockIsMaintenance()) {
      slotNum += manager.getSkipSlotInMaintenance();
    }

    long headSlotTime = manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
    headSlotTime = headSlotTime
        - ((headSlotTime - getGenesisBlock().getTimeStamp()) % interval);

    return headSlotTime + interval * slotNum;
  }

  /**
   * validate witness schedule.
   */
  public boolean validateWitnessSchedule(BlockCapsule block) {
    var witnessAddress = block.getInstance().getBlockHeader().getRawData().getWitnessAddress();
    long timeStamp = block.getTimeStamp();
    return validateWitnessSchedule(witnessAddress, timeStamp);
  }

  public boolean validateWitnessSchedule(ByteString witnessAddress, long timeStamp) {
    //to deal with other condition later
    if (manager.getDynamicPropertiesStore().getLatestBlockHeaderNumber() == 0) {
      return true;
    }
    long blockAbSlot = getAbSlotAtTime(timeStamp);
    long headBlockAbSlot = getAbSlotAtTime(manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp());
    if (blockAbSlot <= headBlockAbSlot) {
      logger.warn("blockAbSlot is equals with headBlockAbSlot[" + blockAbSlot + "]");
      return false;
    }

    long slot = getSlotAtTime(timeStamp);
    final ByteString scheduledWitness = getScheduledWitness(slot);
    if (!scheduledWitness.equals(witnessAddress)) {
      logger.warn(
          "Witness is out of order, scheduledWitness[{}],blockWitnessAddress[{}],blockTimeStamp[{}],slot[{}]",
          ByteArray.toHexString(scheduledWitness.toByteArray()),
          ByteArray.toHexString(witnessAddress.toByteArray()), new DateTime(timeStamp),
          slot);
      return false;
    }

    logger.debug("Validate witnessSchedule successfully,scheduledWitness:{}", ByteArray.toHexString(witnessAddress.toByteArray()));
    return true;
  }

  public boolean activeWitnessesContain(final Set<ByteString> localWitnesses) {
    var activeWitnesses = this.getActiveWitnesses();
    for (ByteString witnessAddress : localWitnesses) {
      if (activeWitnesses.contains(witnessAddress))
        return true;
    }
    return false;
  }

  public ByteString getScheduledWitness(final long slot) {
    val currentSlot = getHeadSlot() + slot;
    if (currentSlot < 0) {
      throw new RuntimeException("currentSlot should be positive.");
    }

    int numberActiveWitness = this.getActiveWitnesses().size();
    int singleRepeat = ChainConstant.SINGLE_REPEAT;
    if (numberActiveWitness <= 0) {
      throw new RuntimeException("Active Witnesses is null.");
    }
    int witnessIndex = (int) currentSlot % (numberActiveWitness * singleRepeat);
    witnessIndex /= singleRepeat;
    logger.debug("currentSlot: {}, witnessIndex {}, currentActiveWitnesses size: {}",
            currentSlot, witnessIndex, numberActiveWitness);

    val scheduledWitness = this.getActiveWitnesses().get(witnessIndex);
    logger.info("scheduledWitness: {}, currentSlot: {}", ByteArray.toHexString(scheduledWitness.toByteArray()), currentSlot);
    return scheduledWitness;
  }

  public long getHeadSlot() {
    return (manager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - getGenesisBlock().getTimeStamp())
        / ChainConstant.BLOCK_PRODUCED_INTERVAL;
  }

  private Map<ByteString, Long> countVote(VotesStore votesStore) {
    final Map<ByteString, Long> countWitness = Maps.newHashMap();
    Iterator<Map.Entry<byte[], VotesCapsule>> dbIterator = votesStore.iterator();

    long sizeCount = 0;
    while (dbIterator.hasNext()) {
      Entry<byte[], VotesCapsule> next = dbIterator.next();
      VotesCapsule votes = next.getValue();

      // TODO add vote reward
      // long reward = Math.round(sum.get() * this.manager.getDynamicPropertiesStore()
      //    .getVoteRewardRate());
      //account.setBalance(account.getBalance() + reward);
      //accountStore.put(account.createDbKey(), account);

      votes.getOldVotes().forEach(vote -> {
        //TODO validate witness //active_witness
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        if (countWitness.containsKey(voteAddress)) {
          countWitness.put(voteAddress, countWitness.get(voteAddress) - voteCount);
        } else {
          countWitness.put(voteAddress, -voteCount);
        }
      });
      votes.getNewVotes().forEach(vote -> {
        //TODO validate witness //active_witness
        ByteString voteAddress = vote.getVoteAddress();
        long voteCount = vote.getVoteCount();
        if (countWitness.containsKey(voteAddress)) {
          countWitness.put(voteAddress, countWitness.get(voteAddress) + voteCount);
        } else {
          countWitness.put(voteAddress, voteCount);
        }
      });

      sizeCount++;
      votesStore.delete(next.getKey());
    }
    logger.info("there is {} new votes in this epoch", sizeCount);

    return countWitness;
  }

  /**
   * update witness.
   */
  public void updateWitness() {
    WitnessStore witnessStore = manager.getWitnessStore();
    VotesStore votesStore = manager.getVotesStore();
    AccountStore accountStore = manager.getAccountStore();

    tryRemoveThePowerOfTheGr();

    Map<ByteString, Long> countWitness = countVote(votesStore);

    //Only possible during the initialization phase
    if (countWitness.isEmpty()) {
      logger.info("No vote, no change to witness.");
    } else {
      List<ByteString> currentWits = getActiveWitnesses();

      List<ByteString> newWitnessAddressList = new ArrayList<>();
      witnessStore.getAllWitnesses().forEach(witnessCapsule -> {
        newWitnessAddressList.add(witnessCapsule.getAddress());
      });

      countWitness.forEach((address, voteCount) -> {
        final WitnessCapsule witnessCapsule = witnessStore
            .get(StringUtil.createDbKey(address));
        if (null == witnessCapsule) {
          logger.warn("witnessCapsule is null.address is {}",
              StringUtil.createReadableString(address));
          return;
        }

        AccountCapsule witnessAccountCapsule = accountStore
            .get(StringUtil.createDbKey(address));
        if (witnessAccountCapsule == null) {
          logger.warn(
              "witnessAccount[" + StringUtil.createReadableString(address) + "] not exists");
        } else {
          long vote = witnessCapsule.getVoteCount() + voteCount;
          witnessCapsule.setVoteCount(vote);
          witnessStore.put(witnessCapsule.createDbKey(), witnessCapsule);
          logger.info("address is {}  ,countVote is {}", witnessCapsule.createReadableString(),
              witnessCapsule.getVoteCount());
        }
      });

      sortWitness(newWitnessAddressList);
      if (newWitnessAddressList.size() > ChainConstant.MAX_ACTIVE_WITNESS_NUM) {
        setActiveWitnesses(newWitnessAddressList.subList(0, ChainConstant.MAX_ACTIVE_WITNESS_NUM));
      } else {
        setActiveWitnesses(newWitnessAddressList);
      }

      if (newWitnessAddressList.size() > ChainConstant.WITNESS_STANDBY_LENGTH) {
        payStandbyWitness(newWitnessAddressList.subList(0, ChainConstant.WITNESS_STANDBY_LENGTH));
      } else {
        payStandbyWitness(newWitnessAddressList);
      }

      List<ByteString> newWits = getActiveWitnesses();
      if (witnessSetChanged(currentWits, newWits)) {
        currentWits.forEach(address -> {
          WitnessCapsule witnessCapsule = getWitnessByAddress(address);
          witnessCapsule.setIsJobs(false);
          witnessStore.put(witnessCapsule.createDbKey(), witnessCapsule);
        });

        newWits.forEach(address -> {
          WitnessCapsule witnessCapsule = getWitnessByAddress(address);
          witnessCapsule.setIsJobs(true);
          witnessStore.put(witnessCapsule.createDbKey(), witnessCapsule);
        });
      }

      logger.info("updateWitness,before:{}, after {}", StringUtil.getAddressStringList(currentWits), StringUtil.getAddressStringList(newWits));
    }
    //update the delegation cycle
    if (manager.getDynamicPropertiesStore().allowChangeDelegation()) {
      long nextCycle = manager.getDynamicPropertiesStore().getCurrentCycleNumber() + 1;
      manager.getDynamicPropertiesStore().saveCurrentCycleNumber(nextCycle);
      witnessStore.getAllWitnesses().forEach(witnessCapsule -> {
        manager.getDelegationStore().setBrokerage(nextCycle, witnessCapsule.getAddress().toByteArray(), manager.getDelegationStore().getBrokerage(witnessCapsule.getAddress().toByteArray()));
        manager.getDelegationStore().setWitnessVote(nextCycle, witnessCapsule.getAddress().toByteArray(), witnessCapsule.getVoteCount());
      });
    }
  }

  public void tryRemoveThePowerOfTheGr() {
    if (manager.getDynamicPropertiesStore().getRemoveThePowerOfTheGr() == 1) {
      var witnessStore = manager.getWitnessStore();
      Args.getInstance().getGenesisBlock().getWitnesses().forEach(witnessInGenesisBlock -> {
        WitnessCapsule witnessCapsule = witnessStore.get(witnessInGenesisBlock.getAddress());
        witnessCapsule.setVoteCount(witnessCapsule.getVoteCount() - witnessInGenesisBlock.getVoteCount());
        witnessStore.put(witnessCapsule.createDbKey(), witnessCapsule);
      });

      manager.getDynamicPropertiesStore().saveRemoveThePowerOfTheGr(-1);
    }
  }

  private static boolean witnessSetChanged(List<ByteString> list1, List<ByteString> list2) {
    return !CollectionUtils.isEqualCollection(list1, list2);
  }

  public int calculateParticipationRate() {
    return manager.getDynamicPropertiesStore().calculateFilledSlotsCount();
  }

  public void dumpParticipationLog() {
    var builder = new StringBuilder();
    int[] blockFilledSlots = manager.getDynamicPropertiesStore().getBlockFilledSlots();
    builder.append("dump participation log \n ")
            .append("blockFilledSlots:")
            .append(Arrays.toString(blockFilledSlots)).append(",");
    long headSlot = getHeadSlot();
    builder.append("\n").append(" headSlot:").append(headSlot).append(",");

    var activeWitnesses = getActiveWitnesses();
    activeWitnesses.forEach(a -> {
      var witnessCapsule = manager.getWitnessStore().get(a.toByteArray());
      builder.append("\n")
              .append(" witness:")
              .append(witnessCapsule.createReadableString())
              .append(",")
              .append("latestBlockNum:")
              .append(witnessCapsule.getLatestBlockNum()).
              append(",")
              .append("LatestSlotNum:")
              .append(witnessCapsule.getLatestSlotNum())
              .append(".");
    });
    logger.debug(builder.toString());
  }


  private void sortWitness(List<ByteString> list) {
    list.sort(Comparator.comparingLong((ByteString b) -> getWitnessByAddress(b).getVoteCount())
        .reversed()
        .thenComparing(Comparator.comparingInt(ByteString::hashCode).reversed()));
  }

  private void payStandbyWitness(List<ByteString> witnessAddresses) {
    if (manager.getDynamicPropertiesStore().allowChangeDelegation()) {
      return;
    }
    long voteSum = 0;
    long totalPay = manager.getDynamicPropertiesStore().getWitnessStandbyAllowance();
    for (var witness : witnessAddresses) {
      voteSum += getWitnessByAddress(witness).getVoteCount();
    }
    if (voteSum > 0) {
      for (ByteString b : witnessAddresses) {
        long pay = (long) (getWitnessByAddress(b).getVoteCount() * ((double) totalPay / voteSum));
        AccountCapsule accountCapsule = manager.getAccountStore().get(b.toByteArray());
        accountCapsule.setAllowance(accountCapsule.getAllowance() + pay);
        manager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      }
    }
  }

  public boolean isGeneratingBlock() {
    return generatingBlock.get();
  }

  public void setGeneratingBlock(boolean generatingBlock) {
    this.generatingBlock.set(generatingBlock);
  }
}
