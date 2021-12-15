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

import static org.unichain.protos.Contract.ResourceCode.BANDWIDTH;
import static org.unichain.protos.Contract.ResourceCode.ENERGY;

@Slf4j(topic = "actuator")
public class FreezeBalanceActuator extends AbstractActuator {

  FreezeBalanceActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = contract.unpack(FreezeBalanceContract.class);
      var txOwnerAddress = ctx.getOwnerAddress().toByteArray();
      var ownerAccountCapsule = dbManager.getAccountStore().get(txOwnerAddress);

      var now = dbManager.getHeadBlockTimeStamp();
      var duration = ctx.getFrozenDuration() * 86_400_000;

      var newBalance = ownerAccountCapsule.getBalance() - ctx.getFrozenBalance();

      var frozenBalance = ctx.getFrozenBalance();
      var expireTime = now + duration;
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var receiverAddress = ctx.getReceiverAddress().toByteArray();

      switch (ctx.getResource()) {
        case BANDWIDTH:
          if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
            delegateResource(ownerAddress, receiverAddress, true, frozenBalance, expireTime);
            ownerAccountCapsule.addDelegatedFrozenBalanceForBandwidth(frozenBalance);
          } else {
            var newFrozenBalanceForBandwidth = frozenBalance + ownerAccountCapsule.getFrozenBalance();
            ownerAccountCapsule.setFrozenForBandwidth(newFrozenBalanceForBandwidth, expireTime);
          }
          dbManager.getDynamicPropertiesStore().addTotalNetWeight(frozenBalance / 1000_000L);
          break;
        case ENERGY:
          if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
            delegateResource(ownerAddress, receiverAddress, false, frozenBalance, expireTime);
            ownerAccountCapsule.addDelegatedFrozenBalanceForEnergy(frozenBalance);
          } else {
            var newFrozenBalanceForEnergy = frozenBalance + ownerAccountCapsule.getAccountResource()
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
      logger.error(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }


  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(FreezeBalanceContract.class), "Contract type error,expected type [FreezeBalanceContract],real type[" + contract.getClass() + "]");

      val freezeBalanceContract = this.contract.unpack(FreezeBalanceContract.class);
      var ownerAddress = freezeBalanceContract.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      var frozenBalance = freezeBalanceContract.getFrozenBalance();
      Assert.isTrue(frozenBalance > 0, "FrozenBalance must be positive");
      Assert.isTrue(frozenBalance >= 1_000_000L, "FrozenBalance must be more than 1 UNW");

      int frozenCount = accountCapsule.getFrozenCount();
      Assert.isTrue((frozenCount == 0 || frozenCount == 1), "FrozenCount must be 0 or 1");
      Assert.isTrue(frozenBalance <= accountCapsule.getBalance(), "FrozenBalance must be less than accountBalance");

//    long maxFrozenNumber = dbManager.getDynamicPropertiesStore().getMaxFrozenNumber();
//    if (accountCapsule.getFrozenCount() >= maxFrozenNumber) {
//      throw new ContractValidateException("max frozen number is: " + maxFrozenNumber);
//    }

      var frozenDuration = freezeBalanceContract.getFrozenDuration();
      var minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
      var maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();
      var needCheckFrozeTime = (Args.getInstance().getCheckFrozenTime() == 1);//for test
      Assert.isTrue(!(needCheckFrozeTime && !(frozenDuration >= minFrozenTime && frozenDuration <= maxFrozenTime)), "frozenDuration must be less than " + maxFrozenTime + " days " + "and more than " + minFrozenTime + " days");
      var resourceCode = freezeBalanceContract.getResource();
      Assert.isTrue((resourceCode == BANDWIDTH) || (resourceCode == ENERGY), "ResourceCode error, valid ResourceCode[BANDWIDTH、ENERGY]");

      //todo：need version control and config for delegating resource
      var receiverAddress = freezeBalanceContract.getReceiverAddress().toByteArray();
      //If the receiver is included in the contract, the receiver will receive the resource.
      if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
        Assert.isTrue(!(Arrays.equals(receiverAddress, ownerAddress)), "ReceiverAddress must not be the same as ownerAddress");
        Assert.isTrue(Wallet.addressValid(receiverAddress), "Invalid receiverAddress");

        var receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
        Assert.notNull(receiverCapsule, "Account[" + StringUtil.createReadableString(receiverAddress) + "] not exists");

        var delegate = (dbManager.getDynamicPropertiesStore().getAllowTvmConstantinople() == 1 && receiverCapsule.getType() == AccountType.Contract);
        Assert.isTrue(!delegate, "Do not allow delegate resources to contract addresses");
      }

      return true;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
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