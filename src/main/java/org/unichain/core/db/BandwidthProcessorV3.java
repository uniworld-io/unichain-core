package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.exception.AccountResourceInsufficientException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Protocol.Transaction.Contract;

/**
 * With block v3: create new account while transferring token to non-exist account
 * charged on owner account.
 */
@Slf4j(topic = "DB")
public class BandwidthProcessorV3 extends BandwidthProcessorV2 {

  public BandwidthProcessorV3(Manager manager) {
    super(manager);
  }

  @Override
  protected void consumeCreateNewAccountIfUrc30Transfer(AccountCapsule ownerAccountCapsule, Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    consumeForCreateNewAccount(ownerAccountCapsule, trace);
  }

  @Override
  protected void consumeCreateNewAccountIfUrc20Transfer(AccountCapsule ownerAccountCapsule, Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    consumeForCreateNewAccount(ownerAccountCapsule, trace);
  }
}
