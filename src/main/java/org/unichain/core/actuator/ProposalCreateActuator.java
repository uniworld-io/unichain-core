package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.ProposalCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.ProposalService;
import org.unichain.protos.Contract.ProposalCreateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Map;
import java.util.Objects;

import static org.unichain.core.actuator.ActuatorConstant.*;

@Slf4j(topic = "actuator")
public class ProposalCreateActuator extends AbstractActuator {

  public ProposalCreateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(ProposalCreateContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var id = (Objects.isNull(getDeposit())) ?
          Math.incrementExact(dbManager.getDynamicPropertiesStore().getLatestProposalNum()) :
          Math.incrementExact(getDeposit().getLatestProposalNum());

      var proposalCapsule = new ProposalCapsule(ctx.getOwnerAddress(), id);

      proposalCapsule.setParameters(ctx.getParametersMap());

      var now = dbManager.getHeadBlockTimeStamp();
      var maintenanceTimeInterval = (Objects.isNull(getDeposit())) ?
          dbManager.getDynamicPropertiesStore().getMaintenanceTimeInterval() :
          getDeposit().getMaintenanceTimeInterval();
      proposalCapsule.setCreateTime(now);

      var currentMaintenanceTime = (Objects.isNull(getDeposit())) ? dbManager.getDynamicPropertiesStore().getNextMaintenanceTime()
              : getDeposit().getNextMaintenanceTime();
      var now3 = Math.addExact(now, Args.getInstance().getProposalExpireTime());
      var round = Math.subtractExact(now3, currentMaintenanceTime) / maintenanceTimeInterval;
      var expirationTime = Math.addExact(currentMaintenanceTime, Math.multiplyExact(round + 1, maintenanceTimeInterval));
      proposalCapsule.setExpirationTime(expirationTime);

      if (Objects.isNull(deposit)) {
        dbManager.getProposalStore().put(proposalCapsule.createDbKey(), proposalCapsule);
        dbManager.getDynamicPropertiesStore().saveLatestProposalNum(id);
      } else {
        deposit.putProposalValue(proposalCapsule.createDbKey(), proposalCapsule);
        deposit.putDynamicPropertiesWithLatestProposalNum(id);
      }

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");

      var dbManagerCheck = (dbManager == null) && (deposit == null || deposit.getDbManager() == null);
      Assert.isTrue(!dbManagerCheck, "No dbManager!");
      Assert.isTrue(this.contract.is(ProposalCreateContract.class), "contract type error,expected type [ProposalCreateContract],real type[" + contract.getClass() + "]");

      val contract = this.contract.unpack(ProposalCreateContract.class);
      var ownerAddress = contract.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      if (!Objects.isNull(deposit)) {
        Assert.notNull(deposit.getAccount(ownerAddress), ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      } else {
        Assert.isTrue(dbManager.getAccountStore().has(ownerAddress), ACCOUNT_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      if (!Objects.isNull(getDeposit())) {
        Assert.notNull(getDeposit().getWitness(ownerAddress), WITNESS_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      } else {
        Assert.isTrue(dbManager.getWitnessStore().has(ownerAddress), WITNESS_EXCEPTION_STR + readableOwnerAddress + NOT_EXIST_STR);
      }

      Assert.isTrue(contract.getParametersMap().size() != 0, "This proposal has no parameter.");

      for (Map.Entry<Long, Long> entry : contract.getParametersMap().entrySet()) {
        validateValue(entry);
      }

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  private void validateValue(Map.Entry<Long, Long> entry) throws ContractValidateException {
    ProposalService.validator(dbManager, entry.getKey(), entry.getValue());
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ProposalCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
