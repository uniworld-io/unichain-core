package org.unichain.core.actuator;

import com.google.common.collect.Lists;
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
import org.unichain.core.capsule.*;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.UnfreezeBalanceContract;
import org.unichain.protos.Protocol.Account.AccountResource;
import org.unichain.protos.Protocol.Account.Frozen;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


@Slf4j(topic = "actuator")
public class UnfreezeBalanceActuator extends AbstractActuator {

  UnfreezeBalanceActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
        val ctx  = contract.unpack(UnfreezeBalanceContract.class);
        var ownerAddress = ctx.getOwnerAddress().toByteArray();
        dbManager.getDelegationService().withdrawReward(ownerAddress, getDeposit());
        AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
        var oldBalance = accountCapsule.getBalance();
        var unfreezeBalance = 0L;

        var receiverAddress = ctx.getReceiverAddress().toByteArray();
        //If the receiver is not included in the contract, unfreeze frozen balance for this account.
        //otherwise,unfreeze delegated frozen balance provided this account.
        if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
            var key = DelegatedResourceCapsule.createDbKey(ctx.getOwnerAddress().toByteArray(), ctx.getReceiverAddress().toByteArray());
            DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore().get(key);

            switch (ctx.getResource()) {
            case BANDWIDTH:
              unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForBandwidth();
              delegatedResourceCapsule.setFrozenBalanceForBandwidth(0, 0);
              accountCapsule.addDelegatedFrozenBalanceForBandwidth(-unfreezeBalance);
              break;
            case ENERGY:
              unfreezeBalance = delegatedResourceCapsule.getFrozenBalanceForEnergy();
              delegatedResourceCapsule.setFrozenBalanceForEnergy(0, 0);
              accountCapsule.addDelegatedFrozenBalanceForEnergy(-unfreezeBalance);
              break;
            default:
              //this should never happen
              break;
          }

            AccountCapsule receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
            if (dbManager.getDynamicPropertiesStore().getAllowTvmConstantinople() == 0 ||
                (receiverCapsule != null && receiverCapsule.getType() != AccountType.Contract)) {
              switch (ctx.getResource()) {
                case BANDWIDTH:
                  if (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1
                          && receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth() < unfreezeBalance) {
                    receiverCapsule.setAcquiredDelegatedFrozenBalanceForBandwidth(0);
                  } else {
                    receiverCapsule.addAcquiredDelegatedFrozenBalanceForBandwidth(-unfreezeBalance);
                  }
                  break;
                case ENERGY:
                  if (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1
                          && receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy() < unfreezeBalance) {
                    receiverCapsule.setAcquiredDelegatedFrozenBalanceForEnergy(0);
                  } else {
                    receiverCapsule.addAcquiredDelegatedFrozenBalanceForEnergy(-unfreezeBalance);
                  }
                  break;
                default:
                  //this should never happen
                  break;
              }
              dbManager.getAccountStore().put(receiverCapsule.createDbKey(), receiverCapsule);
            }

            accountCapsule.setBalance(oldBalance + unfreezeBalance);

            if (delegatedResourceCapsule.getFrozenBalanceForBandwidth() == 0
                && delegatedResourceCapsule.getFrozenBalanceForEnergy() == 0) {
              dbManager.getDelegatedResourceStore().delete(key);

              //modify DelegatedResourceAccountIndexStore
              {
                DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = dbManager
                    .getDelegatedResourceAccountIndexStore()
                    .get(ownerAddress);
                if (delegatedResourceAccountIndexCapsule != null) {
                  List<ByteString> toAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule.getToAccountsList());
                  toAccountsList.remove(ByteString.copyFrom(receiverAddress));
                  delegatedResourceAccountIndexCapsule.setAllToAccounts(toAccountsList);
                  dbManager.getDelegatedResourceAccountIndexStore().put(ownerAddress, delegatedResourceAccountIndexCapsule);
                }
              }

              {
                DelegatedResourceAccountIndexCapsule delegatedResourceAccountIndexCapsule = dbManager
                    .getDelegatedResourceAccountIndexStore()
                    .get(receiverAddress);
                if (delegatedResourceAccountIndexCapsule != null) {
                  List<ByteString> fromAccountsList = new ArrayList<>(delegatedResourceAccountIndexCapsule.getFromAccountsList());
                  fromAccountsList.remove(ByteString.copyFrom(ownerAddress));
                  delegatedResourceAccountIndexCapsule.setAllFromAccounts(fromAccountsList);
                  dbManager.getDelegatedResourceAccountIndexStore().put(receiverAddress, delegatedResourceAccountIndexCapsule);
                }
              }

            } else {
              dbManager.getDelegatedResourceStore().put(key, delegatedResourceCapsule);
            }
        } else {
          switch (ctx.getResource()) {
            case BANDWIDTH:
              List<Frozen> frozenList = Lists.newArrayList();
              frozenList.addAll(accountCapsule.getFrozenList());
              Iterator<Frozen> iterator = frozenList.iterator();
              long now = dbManager.getHeadBlockTimeStamp();
              while (iterator.hasNext()) {
                Frozen next = iterator.next();
                if (next.getExpireTime() <= now) {
                  unfreezeBalance += next.getFrozenBalance();
                  iterator.remove();
                }
              }

              accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                  .setBalance(oldBalance + unfreezeBalance)
                  .clearFrozen().addAllFrozen(frozenList).build());

              break;
            case ENERGY:
              unfreezeBalance = accountCapsule.getAccountResource().getFrozenBalanceForEnergy().getFrozenBalance();

              AccountResource newAccountResource = accountCapsule.getAccountResource().toBuilder()
                  .clearFrozenBalanceForEnergy().build();
              accountCapsule.setInstance(accountCapsule.getInstance().toBuilder()
                  .setBalance(oldBalance + unfreezeBalance)
                  .setAccountResource(newAccountResource).build());

              break;
            default:
              //this should never happen
              break;
          }
        }

        switch (ctx.getResource()) {
        case BANDWIDTH:
          dbManager.getDynamicPropertiesStore().addTotalNetWeight(-unfreezeBalance / 1_000_000L);
          break;
        case ENERGY:
          dbManager.getDynamicPropertiesStore().addTotalEnergyWeight(-unfreezeBalance / 1_000_000L);
          break;
        default:
          //this should never happen
          break;
        }

        VotesCapsule votesCapsule;
        if (!dbManager.getVotesStore().has(ownerAddress)) {
        votesCapsule = new VotesCapsule(ctx.getOwnerAddress(), accountCapsule.getVotesList());
        } else {
        votesCapsule = dbManager.getVotesStore().get(ownerAddress);
        }

        accountCapsule.clearVotes();
        votesCapsule.clearNewVotes();

        dbManager.getAccountStore().put(ownerAddress, accountCapsule);
        dbManager.getVotesStore().put(ownerAddress, votesCapsule);
        chargeFee(ownerAddress, fee);
        ret.setUnfreezeAmount(unfreezeBalance);
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
      Assert.isTrue(this.contract.is(UnfreezeBalanceContract.class), "contract type error,expected type [UnfreezeBalanceContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(UnfreezeBalanceContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      var now = dbManager.getHeadBlockTimeStamp();
      var receiverAddress = ctx.getReceiverAddress().toByteArray();
      //If the receiver is not included in the contract, unfreeze frozen balance for this account.
      //otherwise,unfreeze delegated frozen balance provided this account.
      if (!ArrayUtils.isEmpty(receiverAddress) && dbManager.getDynamicPropertiesStore().supportDR()) {
        Assert.isTrue(!Arrays.equals(receiverAddress, ownerAddress),"receiverAddress must not be the same as ownerAddress");
        Assert.isTrue(Wallet.addressValid(receiverAddress), "Invalid receiverAddress");

        var receiverCapsule = dbManager.getAccountStore().get(receiverAddress);
        Assert.isTrue(!(dbManager.getDynamicPropertiesStore().getAllowTvmConstantinople() == 0 && receiverCapsule == null), "Receiver Account[" + StringUtil.createReadableString(receiverAddress) + "] not exists");

        var key = DelegatedResourceCapsule.createDbKey(ctx.getOwnerAddress().toByteArray(), ctx.getReceiverAddress().toByteArray());
        DelegatedResourceCapsule delegatedResourceCapsule = dbManager.getDelegatedResourceStore().get(key);
        Assert.notNull(delegatedResourceCapsule, "delegated Resource not exists");

        switch (ctx.getResource()) {
          case BANDWIDTH:
            Assert.isTrue(delegatedResourceCapsule.getFrozenBalanceForBandwidth() > 0, "no delegatedFrozenBalance(BANDWIDTH)");

            if (dbManager.getDynamicPropertiesStore().getAllowTvmConstantinople() == 0) {
              if (receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth()
                      < delegatedResourceCapsule.getFrozenBalanceForBandwidth()) {
                throw new ContractValidateException("AcquiredDelegatedFrozenBalanceForBandwidth["
                        + receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth() + "] < delegatedBandwidth["
                        + delegatedResourceCapsule.getFrozenBalanceForBandwidth()
                        + "]");
              }
            } else {
              if (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() != 1
                      && receiverCapsule != null
                      && receiverCapsule.getType() != AccountType.Contract
                      && receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth() < delegatedResourceCapsule.getFrozenBalanceForBandwidth()) {
                throw new ContractValidateException("AcquiredDelegatedFrozenBalanceForBandwidth["
                                + receiverCapsule.getAcquiredDelegatedFrozenBalanceForBandwidth() + "] < delegatedBandwidth["
                                + delegatedResourceCapsule.getFrozenBalanceForBandwidth()
                                + "]");
              }
            }

            Assert.isTrue(delegatedResourceCapsule.getExpireTimeForBandwidth() <= now, "It's not time to unfreeze.");
            break;
          case ENERGY:
            Assert.isTrue(delegatedResourceCapsule.getFrozenBalanceForEnergy() > 0, "no delegateFrozenBalance(Energy)");

            if (dbManager.getDynamicPropertiesStore().getAllowTvmConstantinople() == 0) {
              if (receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy() < delegatedResourceCapsule.getFrozenBalanceForEnergy()) {
                throw new ContractValidateException("AcquiredDelegatedFrozenBalanceForEnergy["
                        + receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy()
                        + "] < delegatedEnergy["
                        + delegatedResourceCapsule.getFrozenBalanceForEnergy()
                        + "]");
              }
            } else {
              if (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() != 1
                      && receiverCapsule != null
                      && receiverCapsule.getType() != AccountType.Contract
                      && receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy() < delegatedResourceCapsule.getFrozenBalanceForEnergy()) {
                throw new ContractValidateException("AcquiredDelegatedFrozenBalanceForEnergy["
                        + receiverCapsule.getAcquiredDelegatedFrozenBalanceForEnergy()
                        + "] < delegatedEnergy["
                        + delegatedResourceCapsule.getFrozenBalanceForEnergy()
                        + "]");
              }
            }

            Assert.isTrue(delegatedResourceCapsule.getExpireTimeForEnergy(dbManager) <= now, "It's not time to unfreeze.");
            break;
          default:
            throw new ContractValidateException("ResourceCode error.valid ResourceCode[BANDWIDTH、Energy]");
        }
      } else {
        switch (ctx.getResource()) {
          case BANDWIDTH:
            Assert.isTrue(accountCapsule.getFrozenCount() > 0,"no frozenBalance(BANDWIDTH)");
            long allowedUnfreezeCount = accountCapsule.getFrozenList().stream().filter(frozen -> frozen.getExpireTime() <= now).count();
            Assert.isTrue(allowedUnfreezeCount > 0, "It's not time to unfreeze(BANDWIDTH).");
            break;
          case ENERGY:
            Frozen frozenBalanceForEnergy = accountCapsule.getAccountResource().getFrozenBalanceForEnergy();
            Assert.isTrue(frozenBalanceForEnergy.getFrozenBalance() > 0, "no frozenBalance(Energy)");
            Assert.isTrue(frozenBalanceForEnergy.getExpireTime() <= now, "It's not time to unfreeze(Energy).");
            break;
          default:
            throw new ContractValidateException("ResourceCode error.valid ResourceCode[BANDWIDTH、ENERGY]");
        }
      }

      return true;
    }
    catch (Exception e){
      logger.error(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UnfreezeBalanceContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
