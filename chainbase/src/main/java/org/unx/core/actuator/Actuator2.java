package org.unx.core.actuator;

import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;

public interface Actuator2 {

  void execute(Object object) throws ContractExeException;

  void validate(Object object) throws ContractValidateException;
}