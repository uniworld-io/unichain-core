package org.unichain.core.actuator;

import com.google.common.math.LongMath;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.WithdrawBalanceContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

import static org.unichain.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;

@Slf4j(topic = "actuator")
public class WithdrawBalanceActuator extends AbstractActuator {

  WithdrawBalanceActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    final WithdrawBalanceContract withdrawBalanceContract;
    try {
      withdrawBalanceContract = contract.unpack(WithdrawBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }

    dbManager.getDelegationService().withdrawReward(withdrawBalanceContract.getOwnerAddress().toByteArray(), getDeposit());

    var accountCapsule = (Objects.isNull(getDeposit())) ?
            dbManager.getAccountStore().get(withdrawBalanceContract.getOwnerAddress().toByteArray())
            : getDeposit().getAccount(withdrawBalanceContract.getOwnerAddress().toByteArray());
    var oldBalance = accountCapsule.getBalance();
    var allowance = accountCapsule.getAllowance();

    var now = dbManager.getHeadBlockTimeStamp();
    accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
        .setBalance(oldBalance + allowance)
        .setAllowance(0L)
        .setLatestWithdrawTime(now)
        .build());
    if (Objects.isNull(getDeposit())) {
      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
    } else {
      // cache
      deposit.putAccountValue(accountCapsule.createDbKey(), accountCapsule);
    }

    ret.setWithdrawAmount(allowance);
    ret.setStatus(fee, code.SUCESS);

    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    Assert.notNull(contract, "No contract!");
    var dbManagerCheck = dbManager == null && (getDeposit() == null || getDeposit().getDbManager() == null);
    Assert.isTrue(!dbManagerCheck, "No dbManager!");
    Assert.isTrue(this.contract.is(WithdrawBalanceContract.class), "contract type error,expected type [WithdrawBalanceContract],real type[" + contract.getClass() + "]");

    final WithdrawBalanceContract withdrawBalanceContract;
    try {
      withdrawBalanceContract = this.contract.unpack(WithdrawBalanceContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    var ownerAddress = withdrawBalanceContract.getOwnerAddress().toByteArray();
    Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

    var accountCapsule = Objects.isNull(getDeposit()) ? dbManager.getAccountStore().get(ownerAddress) : getDeposit().getAccount(ownerAddress);
    Assert.notNull(accountCapsule, ACCOUNT_EXCEPTION_STR + StringUtil.createReadableString(ownerAddress) + "] not exists");

    var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    var isGP = Args.getInstance()
                      .getGenesisBlock()
                      .getWitnesses()
                      .stream()
                      .anyMatch(witness -> Arrays.equals(ownerAddress, witness.getAddress()));
    Assert.isTrue(!isGP, ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] is a guard representative and is not allowed to withdraw Balance");

    var latestWithdrawTime = accountCapsule.getLatestWithdrawTime();
    var now = dbManager.getHeadBlockTimeStamp();
    var witnessAllowanceFrozenTime = Objects.isNull(getDeposit()) ?
        dbManager.getDynamicPropertiesStore().getWitnessAllowanceFrozenTime() * 86_400_000L :
        getDeposit().getWitnessAllowanceFrozenTime() * 86_400_000L;

    Assert.isTrue(now - latestWithdrawTime >= witnessAllowanceFrozenTime, "The last withdraw time is " + latestWithdrawTime + ",less than 24 hours");
    var witnessAccount = accountCapsule.getAllowance() <= 0 && dbManager.getDelegationService().queryReward(ownerAddress) <= 0;
    Assert.isTrue(!witnessAccount, "witnessAccount does not have any reward");
    try {
      LongMath.checkedAdd(accountCapsule.getBalance(), accountCapsule.getAllowance());
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(WithdrawBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
