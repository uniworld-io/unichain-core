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
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.ItemNotFoundException;
import org.unichain.protos.Contract.ProposalDeleteContract;
import org.unichain.protos.Protocol.Proposal.State;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

import static org.unichain.core.actuator.ActuatorConstant.*;

@Slf4j(topic = "actuator")
public class ProposalDeleteActuator extends AbstractActuator {

  ProposalDeleteActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(ProposalDeleteContract.class);
      var proposalCapsule = (Objects.isNull(deposit)) ?
              dbManager.getProposalStore().get(ByteArray.fromLong(ctx.getProposalId()))
              : deposit.getProposalCapsule(ByteArray.fromLong(ctx.getProposalId()));
      proposalCapsule.setState(State.CANCELED);
      if (Objects.isNull(deposit)) {
        dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
      } else {
        deposit.putProposalValue(proposalCapsule.createDbKey(), proposalCapsule);
      }
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(this.contract, "No contract!");
      Assert.notNull(!(dbManager == null && (deposit == null || deposit.getDbManager() == null)), "No dbManager!");

      Assert.isTrue(this.contract.is(ProposalDeleteContract.class), "Contract type error,expected type [ProposalDeleteContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(ProposalDeleteContract.class);

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      if (!Objects.isNull(deposit)) {
        Assert.notNull(deposit.getAccount(ownerAddress), ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      } else {
        Assert.isTrue(dbManager.getAccountStore().has(ownerAddress), ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      var latestProposalNum = Objects.isNull(deposit) ? dbManager.getDynamicPropertiesStore().getLatestProposalNum() : deposit.getLatestProposalNum();
      Assert.isTrue(ctx.getProposalId() <= latestProposalNum, PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + NOT_EXIST_STR);

      ProposalCapsule proposalCapsule = Objects.isNull(getDeposit()) ?
                dbManager.getProposalStore().get(ByteArray.fromLong(ctx.getProposalId()))
                : deposit.getProposalCapsule(ByteArray.fromLong(ctx.getProposalId()));


      var now = dbManager.getHeadBlockTimeStamp();

      Assert.isTrue(proposalCapsule.getProposalAddress().equals(ctx.getOwnerAddress()), PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + "] " + "is not proposed by " + readableOwnerAddress);

      Assert.isTrue(now < proposalCapsule.getExpirationTime(), PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + "] expired");
      Assert.isTrue(proposalCapsule.getState() != State.CANCELED, PROPOSAL_EXCEPTION_STR + ctx.getProposalId() + "] canceled");

      return true;
    }
    catch (Exception e){
      logger.error(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ProposalDeleteContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
