package org.unichain.core.actuator;

import com.google.common.collect.Lists;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
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

  public UnfreezeAssetActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = contract.unpack(UnfreezeAssetContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();

      var capsule = dbManager.getAccountStore().get(ownerAddress);
      long unfreezeAsset = 0L;
      List<Frozen> frozenList = Lists.newArrayList();
      frozenList.addAll(capsule.getFrozenSupplyList());
      Iterator<Frozen> iterator = frozenList.iterator();
      var now = dbManager.getHeadBlockTimeStamp();
      while (iterator.hasNext()) {
        var next = iterator.next();
        if (next.getExpireTime() <= now) {
          unfreezeAsset = Math.addExact(unfreezeAsset, next.getFrozenBalance());
          iterator.remove();
        }
      }

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        capsule.addAssetAmountV2(capsule.getAssetIssuedName().toByteArray(), unfreezeAsset, dbManager);
      } else {
        capsule.addAssetAmountV2(capsule.getAssetIssuedID().toByteArray(), unfreezeAsset, dbManager);
      }

      capsule.setInstance(capsule.getInstance().toBuilder().clearFrozenSupply().addAllFrozenSupply(frozenList).build());

      dbManager.getAccountStore().put(ownerAddress, capsule);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | ArithmeticException | BalanceInsufficientException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(UnfreezeAssetContract.class), "Contract type error,expected type [UnfreezeAssetContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(UnfreezeAssetContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      var capsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(capsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");
      Assert.isTrue(capsule.getFrozenSupplyCount() > 0, "No frozen supply balance");

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        Assert.isTrue(!capsule.getAssetIssuedName().isEmpty(), "This account did not issue any asset");
      } else {
        Assert.isTrue(!capsule.getAssetIssuedID().isEmpty(), "This account did not issue any asset");
      }

      var now = dbManager.getHeadBlockTimeStamp();
      var count = capsule.getFrozenSupplyList().stream().filter(frozen -> frozen.getExpireTime() <= now).count();
      Assert.isTrue(count > 0, "It's not time to unfreeze asset supply");

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
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
