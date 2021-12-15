package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.WitnessCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.WitnessCreateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class WitnessCreateActuator extends AbstractActuator {

  WitnessCreateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val witnessCreateContract = this.contract.unpack(WitnessCreateContract.class);

      final WitnessCapsule witnessCapsule = new WitnessCapsule(witnessCreateContract.getOwnerAddress(), 0, witnessCreateContract.getUrl().toStringUtf8());

      logger.debug("createWitness, address[{}]", witnessCapsule.createReadableString());
      this.dbManager.getWitnessStore().put(witnessCapsule.createDbKey(), witnessCapsule);
      AccountCapsule accountCapsule = this.dbManager.getAccountStore().get(witnessCapsule.createDbKey());
      accountCapsule.setIsWitness(true);
      if (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1) {
        accountCapsule.setDefaultWitnessPermission(dbManager);
      }
      this.dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      dbManager.getDynamicPropertiesStore().addTotalCreateWitnessCost(fee);
      chargeFee(witnessCreateContract.getOwnerAddress().toByteArray(), fee);
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
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(WitnessCreateContract.class)) {
      throw new ContractValidateException("contract type error,expected type [WitnessCreateContract],real type[" + contract.getClass() + "]");
    }
    final WitnessCreateContract contract;
    try {
      contract = this.contract.unpack(WitnessCreateContract.class);
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid address");
    }

    if (!TransactionUtil.validUrl(contract.getUrl().toByteArray())) {
      throw new ContractValidateException("Invalid url");
    }

    AccountCapsule accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);

    if (accountCapsule == null) {
      throw new ContractValidateException("account[" + readableOwnerAddress + "] not exists");
    }
    /* todo later
    if (ArrayUtils.isEmpty(accountCapsule.getAccountName().toByteArray())) {
      throw new ContractValidateException("account name not set");
    } */

    if (this.dbManager.getWitnessStore().has(ownerAddress)) {
      throw new ContractValidateException("Witness[" + readableOwnerAddress + "] has existed");
    }

    if (accountCapsule.getBalance() < calcFee()) {
      throw new ContractValidateException("balance < AccountUpgradeCost");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(WitnessCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAccountUpgradeCost();
  }
}
