package org.unx.common.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.unx.common.parameter.CommonParameter;
import org.unx.core.actuator.Actuator;
import org.unx.core.actuator.Actuator2;
import org.unx.core.actuator.ActuatorCreator;
import org.unx.core.actuator.VMActuator;
import org.unx.core.db.TransactionContext;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.vm.program.Program;
import org.unx.core.vm.program.Program.BadJumpDestinationException;
import org.unx.core.vm.program.Program.IllegalOperationException;
import org.unx.core.vm.program.Program.JVMStackOverFlowException;
import org.unx.core.vm.program.Program.OutOfEnergyException;
import org.unx.core.vm.program.Program.OutOfMemoryException;
import org.unx.core.vm.program.Program.OutOfTimeException;
import org.unx.core.vm.program.Program.PrecompiledContractException;
import org.unx.core.vm.program.Program.StackTooLargeException;
import org.unx.core.vm.program.Program.StackTooSmallException;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.Protocol.Transaction.Result.contractResult;

@Slf4j(topic = "VM")
public class RuntimeImpl implements Runtime {

  TransactionContext context;
  private List<Actuator> actuatorList = null;

  @Getter
  private Actuator2 actuator2 = null;

  @Override
  public void execute(TransactionContext context)
      throws ContractValidateException, ContractExeException {
    this.context = context;

    ContractType contractType = context.getUnxCap().getInstance().getRawData().getContract(0)
        .getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
      case ContractType.CreateSmartContract_VALUE:
        Set<String> actuatorSet = CommonParameter.getInstance().getActuatorSet();
        if (!actuatorSet.isEmpty() && !actuatorSet.contains(VMActuator.class.getSimpleName())) {
          throw new ContractValidateException("not exist contract " + "SmartContract");
        }
        actuator2 = new VMActuator(context.isStatic());
        break;
      default:
        actuatorList = ActuatorCreator.getINSTANCE().createActuator(context.getUnxCap());
    }
    if (actuator2 != null) {
      actuator2.validate(context);
      actuator2.execute(context);
    } else {
      for (Actuator act : actuatorList) {
        act.validate();
        act.execute(context.getProgramResult().getRet());
      }
    }

    setResultCode(context.getProgramResult());

  }

  @Override
  public ProgramResult getResult() {
    return context.getProgramResult();
  }

  @Override
  public String getRuntimeError() {
    return context.getProgramResult().getRuntimeError();
  }


  private void setResultCode(ProgramResult result) {
    RuntimeException exception = result.getException();
    if (Objects.isNull(exception) && StringUtils
        .isEmpty(result.getRuntimeError()) && !result.isRevert()) {
      result.setResultCode(contractResult.SUCCESS);
      return;
    }
    if (result.isRevert()) {
      result.setResultCode(contractResult.REVERT);
      return;
    }
    if (exception instanceof IllegalOperationException) {
      result.setResultCode(contractResult.ILLEGAL_OPERATION);
      return;
    }
    if (exception instanceof OutOfEnergyException) {
      result.setResultCode(contractResult.OUT_OF_ENERGY);
      return;
    }
    if (exception instanceof BadJumpDestinationException) {
      result.setResultCode(contractResult.BAD_JUMP_DESTINATION);
      return;
    }
    if (exception instanceof OutOfTimeException) {
      result.setResultCode(contractResult.OUT_OF_TIME);
      return;
    }
    if (exception instanceof OutOfMemoryException) {
      result.setResultCode(contractResult.OUT_OF_MEMORY);
      return;
    }
    if (exception instanceof PrecompiledContractException) {
      result.setResultCode(contractResult.PRECOMPILED_CONTRACT);
      return;
    }
    if (exception instanceof StackTooSmallException) {
      result.setResultCode(contractResult.STACK_TOO_SMALL);
      return;
    }
    if (exception instanceof StackTooLargeException) {
      result.setResultCode(contractResult.STACK_TOO_LARGE);
      return;
    }
    if (exception instanceof JVMStackOverFlowException) {
      result.setResultCode(contractResult.JVM_STACK_OVER_FLOW);
      return;
    }
    if (exception instanceof Program.TransferException) {
      result.setResultCode(contractResult.TRANSFER_FAILED);
      return;
    }
    if (exception instanceof Program.InvalidCodeException) {
      result.setResultCode(contractResult.INVALID_CODE);
      return;
    }
    result.setResultCode(contractResult.UNKNOWN);
  }

}

