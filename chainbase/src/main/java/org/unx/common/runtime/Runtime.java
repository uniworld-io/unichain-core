package org.unx.common.runtime;

import org.unx.core.db.TransactionContext;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;


public interface Runtime {

  void execute(TransactionContext context)
      throws ContractValidateException, ContractExeException;

  ProgramResult getResult();

  String getRuntimeError();

}
