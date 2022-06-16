package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.ProposalCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.ItemNotFoundException;
import org.unichain.protos.Contract.ProposalApproveContract;
import org.unichain.protos.Protocol.Proposal.State;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

import static org.unichain.core.actuator.ActuatorConstant.*;

@Slf4j(topic = "actuator")
public class ProposalApproveActuator extends AbstractActuator {

  public ProposalApproveActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(ProposalApproveContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var proposalCapsule = (Objects.isNull(getDeposit())) ? dbManager.getProposalStore().get(ByteArray.fromLong(ctx.getProposalId()))
                  : getDeposit().getProposalCapsule(ByteArray.fromLong(ctx.getProposalId()));
      var committeeAddress = ctx.getOwnerAddress();
      if (ctx.getIsAddApproval()) {
        proposalCapsule.addApproval(committeeAddress);
      } else {
        proposalCapsule.removeApproval(committeeAddress);
      }
      if (Objects.isNull(deposit)) {
        dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
      } else {
        deposit.putProposalValue(proposalCapsule.createDbKey(), proposalCapsule);
      }

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (ItemNotFoundException | InvalidProtocolBufferException | BalanceInsufficientException e) {
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
      Assert.isTrue(!dbManagerCheck, "No dbManager!");
      Assert.isTrue(this.contract.is(ProposalApproveContract.class), "Contract type error,expected type [ProposalApproveContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(ProposalApproveContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      if (!Objects.isNull(getDeposit())) {
        Assert.notNull(getDeposit().getAccount(ownerAddress), ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      } else {
        Assert.isTrue(dbManager.getAccountStore().has(ownerAddress), ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      if (!Objects.isNull(getDeposit())) {
        Assert.notNull(getDeposit().getWitness(ownerAddress), WITNESS_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      } else {
        Assert.isTrue(dbManager.getWitnessStore().has(ownerAddress), WITNESS_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      var latestProposalNum = Objects.isNull(getDeposit()) ? dbManager.getDynamicPropertiesStore().getLatestProposalNum()
              : getDeposit().getLatestProposalNum();
      Assert.isTrue(ctx.getProposalId() <= latestProposalNum, PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + NOT_EXIST_STR);

      var now = dbManager.getHeadBlockTimeStamp();
      ProposalCapsule proposalCapsule;
      try {
        proposalCapsule = Objects.isNull(getDeposit()) ? dbManager.getProposalStore().
            get(ByteArray.fromLong(ctx.getProposalId())) :
            getDeposit().getProposalCapsule(ByteArray.fromLong(ctx.getProposalId()));
      } catch (ItemNotFoundException ex) {
        throw new ContractValidateException(PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + NOT_EXIST_STR);
      }

      Assert.isTrue(now < proposalCapsule.getExpirationTime(), PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + "] expired");
      Assert.isTrue(proposalCapsule.getState() != State.CANCELED, PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + "] canceled");

      if (!ctx.getIsAddApproval()) {
        Assert.isTrue(proposalCapsule.getApprovals().contains(ctx.getOwnerAddress()), WITNESS_EXCEPTION_STR + readableOwnerAddress + "]has not approved proposal[" + ctx.getProposalId() + "] before");
      } else {
        Assert.isTrue(!proposalCapsule.getApprovals().contains(ctx.getOwnerAddress()), WITNESS_EXCEPTION_STR + readableOwnerAddress + "]has approved proposal[" + ctx.getProposalId() + "] before");
      }

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ProposalApproveContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
