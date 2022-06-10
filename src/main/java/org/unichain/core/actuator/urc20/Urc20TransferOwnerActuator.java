package org.unichain.core.actuator.urc20;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc20TransferOwnerContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class Urc20TransferOwnerActuator extends AbstractActuator {

  public Urc20TransferOwnerActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc20TransferOwnerContract.class);
      var accountStore = dbManager.getAccountStore();
      var urc20Store = dbManager.getUrc20ContractStore();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      var urc20Addr = ctx.getAddress().toByteArray();
      var urc20Cap = urc20Store.get(urc20Addr);

      var toAccount = accountStore.get(toAddr);
      if (Objects.isNull(toAccount)) {
        toAccount = dbManager.createDefaultAccount(toAddr, Protocol.AccountType.Normal);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      urc20Cap.setOwnerAddress(ctx.getToAddress());
      urc20Cap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      urc20Cap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      urc20Store.put(urc20Addr, urc20Cap);

      var ownerAccount = accountStore.get(ownerAddr);
      var tokenAvail = ownerAccount.burnUrc20AllAvailableToken(urc20Addr);
      toAccount.addUrc20Token(urc20Addr, tokenAvail);
      accountStore.put(toAddr, toAccount);
      accountStore.put(ownerAddr, ownerAccount);

      chargeFee(ownerAddr, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (Exception e) {
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
      Assert.isTrue(contract.is(Urc20TransferOwnerContract.class), "Contract type error, expected type [Urc20TransferOwnerContract], real type[" + contract.getClass() + "]");

      var fee = calcFee();
      var ctx = contract.unpack(Urc20TransferOwnerContract.class);

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      var ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "Owner account not exists");

      var toAddress = ctx.getToAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");
      Assert.isTrue(!Arrays.equals(ownerAddress, toAddress), "Transfer owner to itself not allowed");

      var contractAddr = ctx.getAddress().toByteArray();
      var contractAddrBase58 = Wallet.encode58Check(contractAddr);
      var contractCap = dbManager.getUrc20ContractStore().get(contractAddr);
      Assert.notNull(contractCap, "Contract not found: " + contractAddrBase58);
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));
      Assert.isTrue(Arrays.equals(ownerAddress, contractCap.getOwnerAddress().toByteArray()), "Mismatched Contract owner not allowed");

      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (Objects.isNull(toAccount)) {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      //after UvmSolidity059 proposal, send token/reassign token owner to smartContract by actuator is not allowed.
      var transferToSmartContract  = (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1)
              && (toAccount != null)
              && (toAccount.getType() == AccountType.Contract);
      Assert.isTrue(!transferToSmartContract, "Cannot transfer token owner permission to smartContract");

      Assert.isTrue(ownerAccount.getBalance() >=  fee, "Balance is not sufficient");

      return true;
    }
    catch (IllegalArgumentException | InvalidProtocolBufferException | ArithmeticException e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc20TransferOwnerContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 UNW default
  }
}