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
      var contractStore = dbManager.getUrc20ContractStore();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();
      var urc20Addr = ctx.getAddress().toByteArray();
      var urc20Cap = contractStore.get(urc20Addr);

      var toAccount = accountStore.get(toAddr);
      if (Objects.isNull(toAccount)) {
        toAccount = dbManager.createDefaultAccount(toAddr, Protocol.AccountType.Normal);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      urc20Cap.setOwnerAddress(ctx.getToAddress());
      urc20Cap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
      urc20Cap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      contractStore.put(urc20Addr, urc20Cap);

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
      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc20ContractStore();

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var contractAddr = ctx.getAddress().toByteArray();
      var toAddr = ctx.getToAddress().toByteArray();

      Assert.isTrue(Wallet.addressValid(ownerAddr) && accountStore.has(ownerAddr), "Unrecognized ownerAddress");
      Assert.isTrue(Wallet.addressValid(contractAddr) && contractStore.has(contractAddr), "Unrecognized contractAddr");
      Assert.isTrue( Wallet.addressValid(toAddr)
              && !Arrays.equals(dbManager.getBurnAddress(), toAddr)
              && (!accountStore.has(toAddr) || accountStore.get(toAddr).getType() != AccountType.Contract),
              "Unrecognized to address or burn address or contract address");

      Assert.isTrue(!Arrays.equals(ownerAddr, toAddr), "Transfer owner to itself not allowed");

      var contractCap = contractStore.get(contractAddr);
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: " + Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));
      Assert.isTrue(Arrays.equals(ownerAddr, contractCap.getOwnerAddress().toByteArray()), "Mismatched Contract owner not allowed");

      var toAccount = accountStore.get(toAddr);
      if (Objects.isNull(toAccount)) {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }

      Assert.isTrue(accountStore.get(ownerAddr).getBalance() >=  fee, "Balance is not sufficient");

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