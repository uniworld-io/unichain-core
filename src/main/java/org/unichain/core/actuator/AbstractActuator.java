package org.unichain.core.actuator;

import com.google.protobuf.Any;
import org.unichain.common.storage.Deposit;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;

public abstract class AbstractActuator implements Actuator {

  protected Any contract;
  protected Manager dbManager;

  public Deposit getDeposit() {
    return deposit;
  }

  public void setDeposit(Deposit deposit) {
    this.deposit = deposit;
  }

  protected Deposit deposit;

  AbstractActuator(Any contract, Manager dbManager) {
    this.contract = contract;
    this.dbManager = dbManager;
  }

  protected void chargeFee(byte[] ownerAddress, long fee) throws BalanceInsufficientException {
    dbManager.adjustBalance(ownerAddress, -fee);
    dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().getAddress().toByteArray(), fee);
  }
}
