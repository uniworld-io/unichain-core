package org.unichain.core.actuator;

import com.google.protobuf.Any;
import lombok.Getter;
import lombok.Setter;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.storage.Deposit;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;

public abstract class AbstractActuator implements Actuator {
  protected Any contract;
  protected Manager dbManager;
  private long eventIndex;

  @Getter
  @Setter
  protected Deposit deposit;

  public AbstractActuator(Any contract, Manager dbManager) {
    this.contract = contract;
    this.dbManager = dbManager;
    this.eventIndex = 0;
  }

  protected void chargeFee(byte[] ownerAddress, long fee) throws BalanceInsufficientException {
    dbManager.adjustBalance(ownerAddress, -fee);
    dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().getAddress().toByteArray(), fee);
  }

  protected void chargeFee(AccountCapsule accountCapsule, long fee) throws BalanceInsufficientException {
    dbManager.adjustBalance(accountCapsule, -fee);
    dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().getAddress().toByteArray(), fee);
  }

  protected void emitEvent(NativeContractEvent event , TransactionResultCapsule ret){
    event.index = this.eventIndex++;
    ret.addEvent(event);
  }

  @Override
  public void upgrade(){
  }
}
