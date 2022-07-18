package org.unx.core.vm.nativecontract;

import static org.unx.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;
import static org.unx.core.actuator.ActuatorConstant.STORE_NOT_EXIST;

import com.google.common.math.LongMath;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.utils.StringUtil;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.vm.nativecontract.param.WithdrawRewardParam;
import org.unx.core.vm.repository.Repository;
import org.unx.core.vm.utils.VoteRewardUtil;

@Slf4j(topic = "VMProcessor")
public class WithdrawRewardProcessor {

  public void validate(WithdrawRewardParam param, Repository repo) throws ContractValidateException {
    if (repo == null) {
      throw new ContractValidateException(STORE_NOT_EXIST);
    }

    byte[] ownerAddress = param.getOwnerAddress();

    boolean isGP = CommonParameter.getInstance()
        .getGenesisBlock().getWitnesses().stream().anyMatch(witness ->
            Arrays.equals(ownerAddress, witness.getAddress()));
    if (isGP) {
      throw new ContractValidateException(
          ACCOUNT_EXCEPTION_STR + StringUtil.encode58Check(ownerAddress)
              + "] is a guard representative and is not allowed to withdraw Balance");
    }
  }

  public long execute(WithdrawRewardParam param, Repository repo) throws ContractExeException {
    byte[] ownerAddress = param.getOwnerAddress();

    VoteRewardUtil.withdrawReward(ownerAddress, repo);

    AccountCapsule accountCapsule = repo.getAccount(ownerAddress);
    long oldBalance = accountCapsule.getBalance();
    long allowance = accountCapsule.getAllowance();
    long newBalance = 0;

    try {
      newBalance = LongMath.checkedAdd(oldBalance, allowance);
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractExeException(e.getMessage());
    }

    // If no allowance, do nothing and just return zero.
    if (allowance <= 0) {
      return 0;
    }

    accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
        .setBalance(newBalance)
        .setAllowance(0L)
        .setLatestWithdrawTime(param.getNowInMs())
        .build());

    repo.updateAccount(accountCapsule.createDbKey(), accountCapsule);
    return allowance;
  }
}
