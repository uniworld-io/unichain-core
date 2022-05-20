package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.ContractCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.UpdateSettingContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class UpdateSettingContractActuator extends AbstractActuator {

  public UpdateSettingContractActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(UpdateSettingContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var newPercent = ctx.getConsumeUserResourcePercent();
      var contractAddress = ctx.getContractAddress().toByteArray();
      var deployedContract = dbManager.getContractStore().get(contractAddress);

      dbManager.getContractStore().put(contractAddress, new ContractCapsule(deployedContract.getInstance().toBuilder().setConsumeUserResourcePercent(newPercent).build()));
      chargeFee(ownerAddress, fee);
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
      Assert.isTrue(this.contract.is(UpdateSettingContract.class), "Contract type error,expected type [UpdateSettingContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(UpdateSettingContract.class);
      Assert.isTrue(Wallet.addressValid(ctx.getOwnerAddress().toByteArray()), "Invalid address");

      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      var accountStore = dbManager.getAccountStore();
      var accountCapsule = accountStore.get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + readableOwnerAddress + "] not exists");

      var newPercent = ctx.getConsumeUserResourcePercent();
      Assert.isTrue(!(newPercent > 100 || newPercent < 0), "Percent not in [0, 100]");

      var contractAddress = ctx.getContractAddress().toByteArray();
      var deployedContract = dbManager.getContractStore().get(contractAddress);
      Assert.notNull(deployedContract, "Contract not exists");

      var deployedContractOwnerAddress = deployedContract.getInstance().getOriginAddress().toByteArray();
      Assert.isTrue(Arrays.equals(ownerAddress, deployedContractOwnerAddress), "Account[" + readableOwnerAddress + "] is not the owner of the contract");
      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateSettingContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }

}
