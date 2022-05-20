package org.unichain.core.actuator;

import com.google.common.math.LongMath;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.storage.Deposit;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.VotesCapsule;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.VoteWitnessContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

import static org.unichain.core.actuator.ActuatorConstant.*;

@Slf4j(topic = "actuator")
public class VoteWitnessActuator extends AbstractActuator {

  public VoteWitnessActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(VoteWitnessContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      countVoteAccount(ctx, getDeposit());
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      var dbManagerCheck = (dbManager == null) && (getDeposit() == null || getDeposit().getDbManager() == null);
      Assert.isTrue(!dbManagerCheck, "No contract!");
      Assert.isTrue(this.contract.is(VoteWitnessContract.class), "contract type error,expected type [VoteWitnessContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(VoteWitnessContract.class);

      Assert.isTrue(Wallet.addressValid(ctx.getOwnerAddress().toByteArray()), "Invalid address");

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      var accountStore = dbManager.getAccountStore();
      var witnessStore = dbManager.getWitnessStore();
      Assert.isTrue(ctx.getVotesCount() > 0, "VoteNumber must more than 0");

      var maxVoteNumber = ChainConstant.MAX_VOTE_NUMBER;
      Assert.isTrue(ctx.getVotesCount() <= maxVoteNumber, "VoteNumber more than maxVoteNumber " + maxVoteNumber);

      var iterator = ctx.getVotesList().iterator();
      Long sum = 0L;
      while (iterator.hasNext()) {
        var vote = iterator.next();
        var witnessCandidate = vote.getVoteAddress().toByteArray();
        Assert.isTrue(Wallet.addressValid(witnessCandidate), "Invalid vote address!");

        var voteCount = vote.getVoteCount();
        Assert.isTrue(voteCount > 0, "Vote count must be greater than 0");

        var readableWitnessAddress = StringUtil.createReadableString(vote.getVoteAddress());

        if (!Objects.isNull(getDeposit())) {
          Assert.notNull(getDeposit().getAccount(witnessCandidate), ACCOUNT_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        } else {
          Assert.isTrue(accountStore.has(witnessCandidate), ACCOUNT_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }

        if (!Objects.isNull(getDeposit())) {
          Assert.notNull(getDeposit().getWitness(witnessCandidate), WITNESS_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        } else {
          Assert.isTrue(witnessStore.has(witnessCandidate), WITNESS_EXCEPTION_STR + readableWitnessAddress + NOT_EXIST_STR);
        }

        sum = LongMath.checkedAdd(sum, vote.getVoteCount());
      }

      var accountCapsule = (Objects.isNull(getDeposit())) ? accountStore.get(ownerAddress) : getDeposit().getAccount(ownerAddress);
      Assert.notNull(accountCapsule, ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);

      var unichainPower = accountCapsule.getUnichainPower();

      sum = LongMath.checkedMultiply(sum, 1000000L); //unx -> drop. The vote count is based on UNW
      Assert.isTrue(sum <= unichainPower, "The total number of votes[" + sum + "] is greater than the unichainPower[" + unichainPower + "]");

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
  }

  private void countVoteAccount(VoteWitnessContract voteContract, Deposit deposit) {
    var ownerAddress = voteContract.getOwnerAddress().toByteArray();

    VotesCapsule votesCapsule;
    var votesStore = dbManager.getVotesStore();
    var accountStore = dbManager.getAccountStore();

    //withdraw reward
    dbManager.getDelegationService().withdrawReward(ownerAddress, getDeposit());

    var accountCapsule = (Objects.isNull(getDeposit())) ? accountStore.get(ownerAddress) : getDeposit().getAccount(ownerAddress);

    if (!Objects.isNull(getDeposit())) {
      var vCapsule = getDeposit().getVotesCapsule(ownerAddress);
      if (Objects.isNull(vCapsule)) {
        votesCapsule = new VotesCapsule(voteContract.getOwnerAddress(), accountCapsule.getVotesList());
      } else {
        votesCapsule = vCapsule;
      }
    } else if (!votesStore.has(ownerAddress)) {
      votesCapsule = new VotesCapsule(voteContract.getOwnerAddress(), accountCapsule.getVotesList());
    } else {
      votesCapsule = votesStore.get(ownerAddress);
    }

    accountCapsule.clearVotes();
    votesCapsule.clearNewVotes();

    voteContract.getVotesList().forEach(vote -> {
      votesCapsule.addNewVotes(vote.getVoteAddress(), vote.getVoteCount());
      accountCapsule.addVotes(vote.getVoteAddress(), vote.getVoteCount());
    });

    if (Objects.isNull(deposit)) {
      accountStore.put(accountCapsule.createDbKey(), accountCapsule);
      votesStore.put(ownerAddress, votesCapsule);
    } else {
      deposit.putAccountValue(accountCapsule.createDbKey(), accountCapsule);
      deposit.putVoteValue(ownerAddress, votesCapsule);
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(VoteWitnessContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
