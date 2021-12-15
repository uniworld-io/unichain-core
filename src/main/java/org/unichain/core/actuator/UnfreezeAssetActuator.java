package org.unichain.core.actuator;

import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.UnfreezeAssetContract;
import org.unichain.protos.Protocol.Account.Frozen;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Iterator;
import java.util.List;

@Slf4j(topic = "actuator")
public class UnfreezeAssetActuator extends AbstractActuator {

  UnfreezeAssetActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final UnfreezeAssetContract unfreezeAssetContract = contract.unpack(UnfreezeAssetContract.class);
      byte[] ownerAddress = unfreezeAssetContract.getOwnerAddress().toByteArray();

      AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      long unfreezeAsset = 0L;
      List<Frozen> frozenList = Lists.newArrayList();
      frozenList.addAll(accountCapsule.getFrozenSupplyList());
      Iterator<Frozen> iterator = frozenList.iterator();
      long now = dbManager.getHeadBlockTimeStamp();
      while (iterator.hasNext()) {
        Frozen next = iterator.next();
        if (next.getExpireTime() <= now) {
          unfreezeAsset += next.getFrozenBalance();
          iterator.remove();
        }
      }

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        accountCapsule.addAssetAmountV2(accountCapsule.getAssetIssuedName().toByteArray(), unfreezeAsset, dbManager);
      } else {
        accountCapsule.addAssetAmountV2(accountCapsule.getAssetIssuedID().toByteArray(), unfreezeAsset, dbManager);
      }

      accountCapsule.setInstance(accountCapsule.getInstance().toBuilder().clearFrozenSupply().addAllFrozenSupply(frozenList).build());

      dbManager.getAccountStore().put(ownerAddress, accountCapsule);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException
        | ArithmeticException
        | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(UnfreezeAssetContract.class)) {
      throw new ContractValidateException("contract type error,expected type [UnfreezeAssetContract],real type[" + contract.getClass() + "]");
    }
    final UnfreezeAssetContract unfreezeAssetContract;
    try {
      unfreezeAssetContract = this.contract.unpack(UnfreezeAssetContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = unfreezeAssetContract.getOwnerAddress().toByteArray();
    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
    if (accountCapsule == null) {
      String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      throw new ContractValidateException("Account[" + readableOwnerAddress + "] not exists");
    }

    if (accountCapsule.getFrozenSupplyCount() <= 0) {
      throw new ContractValidateException("no frozen supply balance");
    }

    if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      if (accountCapsule.getAssetIssuedName().isEmpty()) {
        throw new ContractValidateException("this account did not issue any asset");
      }
    } else {
      if (accountCapsule.getAssetIssuedID().isEmpty()) {
        throw new ContractValidateException("this account did not issue any asset");
      }
    }

    long now = dbManager.getHeadBlockTimeStamp();
    long allowedUnfreezeCount = accountCapsule.getFrozenSupplyList().stream()
        .filter(frozen -> frozen.getExpireTime() <= now).count();
    if (allowedUnfreezeCount <= 0) {
      throw new ContractValidateException("It's not time to unfreeze asset supply");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UnfreezeAssetContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
