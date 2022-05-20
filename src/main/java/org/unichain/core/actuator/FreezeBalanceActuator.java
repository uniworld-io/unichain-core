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

@Slf4j(topic = "actuator")
public class FreezeBalanceActuator extends AbstractActuator {

  public FreezeBalanceActuator(Any contract, Manager dbManager) {
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
      var duration = Math.multiplyExact(ctx.getFrozenDuration(), 86_400_000L);

      var newBalance = Math.subtractExact(ownerAccountCapsule.getBalance(), ctx.getFrozenBalance());

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
            var newFrozenBalanceForBandwidth = Math.addExact(frozenBalance, ownerAccountCapsule.getFrozenBalance());
            ownerAccountCapsule.setFrozenForBandwidth(newFrozenBalanceForBandwidth, expireTime);
          }
          dbManager.getDynamicPropertiesStore().addTotalNetWeight(frozenBalance / 1000_000L);
          break;
        case ENERGY:
          if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
            delegateResource(ownerAddress, receiverAddress, false, frozenBalance, expireTime);
            ownerAccountCapsule.addDelegatedFrozenBalanceForEnergy(frozenBalance);
          } else {
            var newFrozenBalanceForEnergy = Math.addExact(frozenBalance, ownerAccountCapsule.getAccountResource()
                    .getFrozenBalanceForEnergy()
                    .getFrozenBalance());
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
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }


  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(FreezeBalanceContract.class), "Contract type error,expected type [FreezeBalanceContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(FreezeBalanceContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      var frozenBalance = ctx.getFrozenBalance();
      Assert.isTrue(frozenBalance > 0, "FrozenBalance must be positive");
      Assert.isTrue(frozenBalance >= 1_000_000L, "FrozenBalance must be more than 1 UNW");

      var frozenCount = accountCapsule.getFrozenCount();
      Assert.isTrue(frozenCount == 0 || frozenCount == 1, "FrozenCount must be 0 or 1");
      Assert.isTrue(frozenBalance <= accountCapsule.getBalance(), "FrozenBalance must be less than accountBalance");

//    long maxFrozenNumber = dbManager.getDynamicPropertiesStore().getMaxFrozenNumber();
//    if (accountCapsule.getFrozenCount() >= maxFrozenNumber) {
//      throw new ContractValidateException("max frozen number is: " + maxFrozenNumber);
//    }

      var frozenDuration = ctx.getFrozenDuration();
      var minFrozenTime = dbManager.getDynamicPropertiesStore().getMinFrozenTime();
      var maxFrozenTime = dbManager.getDynamicPropertiesStore().getMaxFrozenTime();

      var needCheckFrozeTime = Args.getInstance().getCheckFrozenTime() == 1;//for test
      var frozenDurationCheck = needCheckFrozeTime && !(frozenDuration >= minFrozenTime && frozenDuration <= maxFrozenTime);
      Assert.isTrue(!frozenDurationCheck, "FrozenDuration must be less than " + maxFrozenTime + " days " + "and more than " + minFrozenTime + " days");

      switch (ctx.getResource()) {
        case BANDWIDTH:
        case ENERGY:
          break;
        default:
          throw new ContractValidateException("ResourceCode error, valid ResourceCode[BANDWIDTH、ENERGY]");
      }

      //todo：need version control and config for delegating resource
      var receiverAddress = ctx.getReceiverAddress().toByteArray();
      //If the receiver is included in the contract, the receiver will receive the resource.
      if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
        var checkAddress = Arrays.equals(receiverAddress, ownerAddress);
        Assert.isTrue(!checkAddress, "ReceiverAddress must not be the same as ownerAddress");
        Assert.isTrue(Wallet.addressValid(receiverAddress), "Invalid receiverAddress");

        var receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
        Assert.notNull(receiverCapsule, "Account[" + StringUtil.createReadableString(receiverAddress) + "] not exists");

        var checkDelegateResource = dbManager.getDynamicPropertiesStore().getAllowTvmConstantinople() == 1 && receiverCapsule.getType() == AccountType.Contract;
        Assert.isTrue(!checkDelegateResource, "Do not allow delegate resources to contract addresses");
      }

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
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
      DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = dbManager.getDelegatedResourceAccountIndexStore().get(receiverAddress);
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
