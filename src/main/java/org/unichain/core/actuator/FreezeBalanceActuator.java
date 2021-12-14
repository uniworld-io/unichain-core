package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.Assert;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.DelegatedResourceAccountIndexCapsule;
import org.unichain.core.capsule.DelegatedResourceCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.FreezeBalanceContract;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.List;

@Slf4j(topic = "actuator")
public class FreezeBalanceActuator extends AbstractActuator {

  FreezeBalanceActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    final FreezeBalanceContract freezeBalanceContract;
    try {
      freezeBalanceContract = contract.unpack(FreezeBalanceContract.class);
      var txOwnerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
      var ownerAccountCapsule = dbManager.getAccountStore().get(txOwnerAddress);

      long now = dbManager.getHeadBlockTimeStamp();
      long duration = freezeBalanceContract.getFrozenDuration() * 86_400_000;

      long newBalance = ownerAccountCapsule.getBalance() - freezeBalanceContract.getFrozenBalance();

      long frozenBalance = freezeBalanceContract.getFrozenBalance();
      long expireTime = now + duration;
      var ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
      var receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();

      switch (freezeBalanceContract.getResource()) {
        case BANDWIDTH:
          if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
            delegateResource(ownerAddress, receiverAddress, true, frozenBalance, expireTime);
            ownerAccountCapsule.addDelegatedFrozenBalanceForBandwidth(frozenBalance);
          } else {
            long newFrozenBalanceForBandwidth = frozenBalance + ownerAccountCapsule.getFrozenBalance();
            ownerAccountCapsule.setFrozenForBandwidth(newFrozenBalanceForBandwidth, expireTime);
          }
          dbManager.getDynamicPropertiesStore().addTotalNetWeight(frozenBalance / 1000_000L);
          break;
        case ENERGY:
          if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
            delegateResource(ownerAddress, receiverAddress, false, frozenBalance, expireTime);
            ownerAccountCapsule.addDelegatedFrozenBalanceForEnergy(frozenBalance);
          } else {
            long newFrozenBalanceForEnergy = frozenBalance + ownerAccountCapsule.getAccountResource()
                    .getFrozenBalanceForEnergy()
                    .getFrozenBalance();
            ownerAccountCapsule.setFrozenForEnergy(newFrozenBalanceForEnergy, expireTime);
          }
          dbManager.getDynamicPropertiesStore().addTotalEnergyWeight(frozenBalance / 1000_000L);
          break;
      }

      ownerAccountCapsule.setBalance(newBalance);
      dbManager.getAccountStore().put(ownerAccountCapsule.createDbKey(), ownerAccountCapsule);

      chargeFee(txOwnerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }


  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(FreezeBalanceContract.class), "contract type error,expected type [FreezeBalanceContract],real type[" + contract.getClass() + "]");

      val freezeBalanceContract = this.contract.unpack(FreezeBalanceContract.class);
      var ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      long frozenBalance = freezeBalanceContract.getFrozenBalance();
      Assert.isTrue(frozenBalance > 0, "frozenBalance must be positive");
      Assert.isTrue(frozenBalance >= 1_000_000L, "frozenBalance must be more than 1 UNW");

      int frozenCount = accountCapsule.getFrozenCount();
      Assert.isTrue((frozenCount == 0 || frozenCount == 1), "frozenCount must be 0 or 1");
      Assert.isTrue(frozenBalance <= accountCapsule.getBalance(), "frozenBalance must be less than accountBalance");

//    long maxFrozenNumber = dbManager.getDynamicPropertiesStore().getMaxFrozenNumber();
//    if (accountCapsule.getFrozenCount() >= maxFrozenNumber) {
//      throw new ContractValidateException("max frozen number is: " + maxFrozenNumber);
//    }

      long frozenDuration = freezeBalanceContract.getFrozenDuration();
      long minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
      long maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();
      boolean needCheckFrozeTime = Args.getInstance().getCheckFrozenTime() == 1;//for test
      Assert.isTrue(!(needCheckFrozeTime && !(frozenDuration >= minFrozenTime && frozenDuration <= maxFrozenTime)), "frozenDuration must be less than " + maxFrozenTime + " days " + "and more than " + minFrozenTime + " days");

      switch (freezeBalanceContract.getResource()) {
        case BANDWIDTH:
          break;
        case ENERGY:
          break;
        default:
          throw new ContractValidateException("ResourceCode error, valid ResourceCode[BANDWIDTH、ENERGY]");
      }

      //todo：need version control and config for delegating resource
      var receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();
      //If the receiver is included in the contract, the receiver will receive the resource.
      if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
        Assert.isTrue(!(Arrays.equals(receiverAddress, ownerAddress)), "receiverAddress must not be the same as ownerAddress");
        Assert.isTrue(Wallet.addressValid(receiverAddress), "Invalid receiverAddress");

        var receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
        Assert.notNull(receiverCapsule, "Account[" + StringUtil.createReadableString(receiverAddress) + "] not exists");

        boolean delegate = (dbManager.getDynamicPropertiesStore().getAllowTvmConstantinople() == 1 && receiverCapsule.getType() == AccountType.Contract);
        Assert.isTrue(!delegate, "Do not allow delegate resources to contract addresses");
      }

      return true;
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(FreezeBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

  private void delegateResource(byte[] ownerAddress, byte[] receiverAddress, boolean isBandwidth, long balance, long expireTime) {
    var key = DelegatedResourceCapsule.createDbKey(ownerAddress, receiverAddress);
    //modify DelegatedResourceStore
    var delegatedResourceCapsule = dbManager.getDelegatedResourceStore().get(key);
    if (delegatedResourceCapsule != null) {
      if (isBandwidth) {
        delegatedResourceCapsule.addFrozenBalanceForBandwidth(balance, expireTime);
      } else {
        delegatedResourceCapsule.addFrozenBalanceForEnergy(balance, expireTime);
      }
    } else {
      delegatedResourceCapsule = new DelegatedResourceCapsule(ByteString.copyFrom(ownerAddress), ByteString.copyFrom(receiverAddress));
      if (isBandwidth) {
        delegatedResourceCapsule.setFrozenBalanceForBandwidth(balance, expireTime);
      } else {
        delegatedResourceCapsule.setFrozenBalanceForEnergy(balance, expireTime);
      }
    }

    dbManager.getDelegatedResourceStore().put(key, delegatedResourceCapsule);

    //modify DelegatedResourceAccountIndexStore
    {
      var delegatedResourceAccountIndexCapsule = dbManager
          .getDelegatedResourceAccountIndexStore()
          .get(ownerAddress);
      if (delegatedResourceAccountIndexCapsule == null) {
        delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(ByteString.copyFrom(ownerAddress));
      }
      var toAccountsList = delegatedResourceAccountIndexCapsule.getToAccountsList();
      if (!toAccountsList.contains(ByteString.copyFrom(receiverAddress))) {
        delegatedResourceAccountIndexCapsule.addToAccount(ByteString.copyFrom(receiverAddress));
      }
      dbManager.getDelegatedResourceAccountIndexStore().put(ownerAddress, delegatedResourceAccountIndexCapsule);
    }

    {
      var delegatedResourceAccountIndexCapsule = dbManager.getDelegatedResourceAccountIndexStore().get(receiverAddress);
      if (delegatedResourceAccountIndexCapsule == null) {
        delegatedResourceAccountIndexCapsule = new DelegatedResourceAccountIndexCapsule(ByteString.copyFrom(receiverAddress));
      }
      var fromAccountsList = delegatedResourceAccountIndexCapsule.getFromAccountsList();
      if (!fromAccountsList.contains(ByteString.copyFrom(ownerAddress))) {
        delegatedResourceAccountIndexCapsule.addFromAccount(ByteString.copyFrom(ownerAddress));
      }
      dbManager.getDelegatedResourceAccountIndexStore().put(receiverAddress, delegatedResourceAccountIndexCapsule);
    }

    //modify AccountStore
    var receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
    if (isBandwidth) {
      receiverCapsule.addAcquiredDelegatedFrozenBalanceForBandwidth(balance);
    } else {
      receiverCapsule.addAcquiredDelegatedFrozenBalanceForEnergy(balance);
    }

    dbManager.getAccountStore().put(receiverCapsule.createDbKey(), receiverCapsule);
  }
}