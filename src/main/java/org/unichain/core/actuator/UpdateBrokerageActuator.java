package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.spongycastle.util.encoders.Hex;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.UpdateBrokerageContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class UpdateBrokerageActuator extends AbstractActuator {

  UpdateBrokerageActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    final UpdateBrokerageContract updateBrokerageContract;
    final long fee = calcFee();
    try {
      updateBrokerageContract = contract.unpack(UpdateBrokerageContract.class);
      byte[] ownerAddress = updateBrokerageContract.getOwnerAddress().toByteArray();
      int brokerage = updateBrokerageContract.getBrokerage();
      //@note review role of brokerage when charging fee
      dbManager.getDelegationStore().setBrokerage(ownerAddress, brokerage);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.isTrue(dbManager.getDynamicPropertiesStore().allowChangeDelegation(), "contract type error,unexpected type [UpdateBrokerageContract]");
      Assert.notNull(this.contract, "No contract!");
      Assert.notNull(this.dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(UpdateBrokerageContract.class), "Contract type error,expected type [UpdateBrokerageContract],real type[" + contract.getClass() + "]");

      val updateBrokerageContract = contract.unpack(UpdateBrokerageContract.class);
      var ownerAddress = updateBrokerageContract.getOwnerAddress().toByteArray();
      var brokerage = updateBrokerageContract.getBrokerage();

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      Assert.isTrue(!(brokerage < 0 || brokerage > 100), "Invalid brokerage");

      var witnessCapsule = dbManager.getWitnessStore().get(ownerAddress);
      Assert.notNull(witnessCapsule, "Not exist witness:" + Hex.toHexString(ownerAddress));

      var account = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(account, "Account has not existed");

      return true;
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(UpdateBrokerageContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}