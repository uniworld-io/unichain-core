package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.WitnessUpdateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class WitnessUpdateActuator extends AbstractActuator {

  public WitnessUpdateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(WitnessUpdateContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var capsule = this.dbManager.getWitnessStore().get(ownerAddress);
      capsule.setUrl(ctx.getUpdateUrl().toStringUtf8());
      this.dbManager.getWitnessStore().put(capsule.createDbKey(), capsule);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (final InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(this.contract, "No contract!");
      Assert.notNull(this.dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(WitnessUpdateContract.class), "Contract type error,expected type [WitnessUpdateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(WitnessUpdateContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "Account does not exist");
      Assert.isTrue(TransactionUtil.validUrl(ctx.getUpdateUrl().toByteArray()), "Invalid url");
      Assert.isTrue(this.dbManager.getWitnessStore().has(ownerAddress), "Witness does not exist");

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(WitnessUpdateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
