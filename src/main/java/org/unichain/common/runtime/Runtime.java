package org.unichain.common.runtime;

import org.unichain.common.runtime.vm.program.InternalTransaction.UnxType;
import org.unichain.common.runtime.vm.program.ProgramResult;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.VMIllegalException;


public interface Runtime {

  void setup() throws ContractValidateException, ContractExeException, VMIllegalException;

  void go();

  UnxType getUnxType();

  void finalization();

  ProgramResult getResult();

  String getRuntimeError();

  void setEnableEventListener(boolean enableEventListener);
}
