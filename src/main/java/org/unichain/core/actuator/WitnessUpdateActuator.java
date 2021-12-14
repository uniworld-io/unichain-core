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

  WitnessUpdateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val witnessUpdateContract = this.contract.unpack(WitnessUpdateContract.class);
      var ownerAddress = witnessUpdateContract.getOwnerAddress().toByteArray();
      var witnessCapsule = this.dbManager.getWitnessStore().get(ownerAddress);
      witnessCapsule.setUrl(witnessUpdateContract.getUpdateUrl().toStringUtf8());
      this.dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);
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
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(contract.is(WitnessUpdateContract.class), "Contract type error,expected type [WitnessUpdateContract], real type[" + contract.getClass() + "]");

      val contract = this.contract.unpack(WitnessUpdateContract.class);
      var ownerAddress = contract.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "account does not exist");
      Assert.isTrue(TransactionUtil.validUrl(contract.getUpdateUrl().toByteArray()), "Invalid url");
      Assert.isTrue(this.dbManager.getWitnessStore().has(ownerAddress), "Witness does not exist");

      return true;
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
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
