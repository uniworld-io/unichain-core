package org.unichain.core.db;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.util.encoders.Hex;
import org.springframework.util.StringUtils;
import org.unichain.common.runtime.Runtime;
import org.unichain.common.runtime.RuntimeImpl;
import org.unichain.common.runtime.config.VMConfig;
import org.unichain.common.runtime.vm.program.InternalTransaction;
import org.unichain.common.runtime.vm.program.InternalTransaction.UnxType;
import org.unichain.common.runtime.vm.program.Program.*;
import org.unichain.common.runtime.vm.program.ProgramResult;
import org.unichain.common.runtime.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.unichain.common.storage.DepositImpl;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.Constant;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.*;
import org.unichain.core.config.args.Args;
import org.unichain.core.exception.*;
import org.unichain.protos.Contract.TriggerSmartContract;
import org.unichain.protos.Protocol.SmartContract.ABI;
import org.unichain.protos.Protocol.Transaction;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;
import org.unichain.protos.Protocol.Transaction.Result.contractResult;

import java.util.Objects;

import static org.unichain.common.runtime.vm.program.InternalTransaction.UnxType.*;
import static org.unichain.core.config.Parameter.ChainConstant.BLOCK_VERSION_1;

@Slf4j(topic = "TransactionTrace")
public class TransactionTrace {

  private TransactionCapsule unx;

  private ReceiptCapsule receipt;

  private Manager dbManager;

  private Runtime runtime;

  private EnergyProcessor energyProcessor;

  private InternalTransaction.UnxType unxType;

  private long txStartTimeInMs;

  private BlockCapsule blockCapsule;

  public TransactionCapsule getUnx() {
    return unx;
  }

  public enum TimeResultType {
    NORMAL,
    LONG_RUNNING,
    OUT_OF_TIME
  }

  @Getter
  @Setter
  private TimeResultType timeResultType = TimeResultType.NORMAL;

  public TransactionTrace(TransactionCapsule unx, Manager dbManager) {
    this.unx = unx;
    Transaction.Contract.ContractType contractType = this.unx.getInstance().getRawData().getContract(0).getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        unxType = UNW_CONTRACT_CALL_TYPE;
        break;
      case ContractType.CreateSmartContract_VALUE:
        unxType = UNW_CONTRACT_CREATION_TYPE;
        break;
      default:
        unxType = UNW_PRECOMPILED_TYPE;
    }

    this.dbManager = dbManager;
    this.receipt = new ReceiptCapsule(Sha256Hash.ZERO_HASH);
    this.energyProcessor = new EnergyProcessor(this.dbManager);
  }

  private boolean needVM() {
    return this.unxType == UNW_CONTRACT_CALL_TYPE || this.unxType == UNW_CONTRACT_CREATION_TYPE;
  }

  public void init(BlockCapsule blockCap, int blockVersion, boolean eventPluginLoaded) {
    blockCapsule = blockCap;
    txStartTimeInMs = System.currentTimeMillis();
    DepositImpl deposit = DepositImpl.createRoot(dbManager);
    runtime = new RuntimeImpl(this, blockCap, blockVersion, deposit, new ProgramInvokeFactoryImpl());
    runtime.setEnableEventListener(eventPluginLoaded);
  }

  public void checkIsConstant() throws ContractValidateException, VMIllegalException {
    if (VMConfig.allowTvmConstantinople()) {
      return;
    }

    TriggerSmartContract triggerContractFromTransaction = ContractCapsule.getTriggerContractFromTransaction(this.getUnx().getInstance());
    if (UnxType.UNW_CONTRACT_CALL_TYPE == this.unxType) {
      DepositImpl deposit = DepositImpl.createRoot(dbManager);
      ContractCapsule contract = deposit.getContract(triggerContractFromTransaction.getContractAddress().toByteArray());
      if (contract == null) {
        logger.info("contract: {} is not in contract store", Wallet.encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray()));
        throw new ContractValidateException("contract: " + Wallet.encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray()) + " is not in contract store");
      }
      ABI abi = contract.getInstance().getAbi();
      if (Wallet.isConstant(abi, triggerContractFromTransaction)) {
        throw new VMIllegalException("cannot call constant method");
      }
    }
  }

  public void setEnergyBill(long energyUsage) {
    if (energyUsage < 0) {
      energyUsage = 0L;
    }
    receipt.setEnergyUsageTotal(energyUsage);
  }

  public void setNetBill(long netUsage, long netFee) {
    receipt.setNetUsage(netUsage);
    receipt.setNetFee(netFee);
  }

  public void addNetBill(long netFee) {
    receipt.addNetFee(netFee);
  }

  public void exec() throws ContractExeException, ContractValidateException, VMIllegalException {
    /*  VM execute  */
    runtime.setup();
    runtime.go();

    if (UNW_PRECOMPILED_TYPE != runtime.getUnxType()) {
      if (contractResult.OUT_OF_TIME.equals(receipt.getResult())) {
        setTimeResultType(TimeResultType.OUT_OF_TIME);
      } else if (Math.subtractExact(System.currentTimeMillis(), txStartTimeInMs) > Args.getInstance().getLongRunningTime()) {
        setTimeResultType(TimeResultType.LONG_RUNNING);
      }
    }
  }

  public void finalization(int blockVersion) throws ContractExeException {
    try {
      if(blockVersion <= BLOCK_VERSION_1)
        payEnergyV1();
      else
        payEnergyV2();
    } catch (BalanceInsufficientException e) {
      throw new ContractExeException(e.getMessage());
    }
    runtime.finalization();
  }

  /**
   * Pay energy bill using v2 which directly charge fee from balance
   * - with pre-compiled, don't charge
   * - charge contract creation & contract call
   */
  public void payEnergyV2() throws BalanceInsufficientException {
    byte[] originAccount;
    byte[] callerAccount;
    long percent = 0;
    switch (unxType) {
      case UNW_CONTRACT_CREATION_TYPE:
        callerAccount = TransactionCapsule.getOwner(unx.getInstance().getRawData().getContract(0));
        originAccount = callerAccount;
        break;
      case UNW_CONTRACT_CALL_TYPE:
        TriggerSmartContract callContract = ContractCapsule.getTriggerContractFromTransaction(unx.getInstance());
        ContractCapsule contractCapsule = dbManager.getContractStore().get(callContract.getContractAddress().toByteArray());
        callerAccount = callContract.getOwnerAddress().toByteArray();
        originAccount = contractCapsule.getOriginAddress();
        percent = Math.max(Constant.ONE_HUNDRED - contractCapsule.getConsumeUserResourcePercent(), 0);
        percent = Math.min(percent, Constant.ONE_HUNDRED);
        break;
      default:
        return;
    }

    AccountCapsule origin = dbManager.getAccountStore().get(originAccount);
    AccountCapsule caller = dbManager.getAccountStore().get(callerAccount);
    receipt.payEnergyBillV2(dbManager, origin, caller, percent);
  }

  /**
   * pay actually bill(include ENERGY and storage).
   */
  public void payEnergyV1() throws BalanceInsufficientException {
    byte[] originAccount;
    byte[] callerAccount;
    long percent = 0;
    long originEnergyLimit = 0;
    switch (unxType) {
      case UNW_CONTRACT_CREATION_TYPE:
        callerAccount = TransactionCapsule.getOwner(unx.getInstance().getRawData().getContract(0));
        originAccount = callerAccount;
        break;
      case UNW_CONTRACT_CALL_TYPE:
        TriggerSmartContract callContract = ContractCapsule.getTriggerContractFromTransaction(unx.getInstance());
        ContractCapsule contractCapsule = dbManager.getContractStore().get(callContract.getContractAddress().toByteArray());
        callerAccount = callContract.getOwnerAddress().toByteArray();
        originAccount = contractCapsule.getOriginAddress();
        percent = Math.max(Constant.ONE_HUNDRED - contractCapsule.getConsumeUserResourcePercent(), 0);
        percent = Math.min(percent, Constant.ONE_HUNDRED);
        originEnergyLimit = contractCapsule.getOriginEnergyLimit();
        break;
      default:
        return;
    }

    // originAccount Percent = 30%
    AccountCapsule origin = dbManager.getAccountStore().get(originAccount);
    AccountCapsule caller = dbManager.getAccountStore().get(callerAccount);
    receipt.payEnergyBill(dbManager, origin, caller, percent, originEnergyLimit, energyProcessor, dbManager.getWitnessController().getHeadSlot());
  }

  public boolean checkNeedRetry() {
    if (!needVM()) {
      return false;
    }
    return unx.getContractRet() != contractResult.OUT_OF_TIME && receipt.getResult() == contractResult.OUT_OF_TIME;
  }

  /**
   * - if not(create, call contract) then pass
   * - if (create, call contract), check return code
   */
  public void check() throws ReceiptCheckErrException {
    if (!needVM()) {
      return;
    }
    if (Objects.isNull(unx.getContractRet())) {
      throw new ReceiptCheckErrException("null resultCode");
    }
    if (!unx.getContractRet().equals(receipt.getResult())) {
      logger.info("this tx id: {}, the resultCode in received block: {}, the resultCode in self: {}",
          Hex.toHexString(unx.getTransactionId().getBytes()),
          unx.getContractRet(),
          receipt.getResult());
      throw new ReceiptCheckErrException("Different resultCode");
    }
  }

  public ReceiptCapsule getReceipt() {
    return receipt;
  }

  public void setResult() {
    if (!needVM()) {
      return;
    }
    RuntimeException exception = runtime.getResult().getException();
    if (Objects.isNull(exception) && StringUtils.isEmpty(runtime.getRuntimeError()) && !runtime.getResult().isRevert()) {
      receipt.setResult(contractResult.SUCCESS);
      return;
    }
    if (runtime.getResult().isRevert()) {
      receipt.setResult(contractResult.REVERT);
      return;
    }
    if (exception instanceof IllegalOperationException) {
      receipt.setResult(contractResult.ILLEGAL_OPERATION);
      return;
    }
    if (exception instanceof OutOfEnergyException) {
      receipt.setResult(contractResult.OUT_OF_ENERGY);
      return;
    }
    if (exception instanceof BadJumpDestinationException) {
      receipt.setResult(contractResult.BAD_JUMP_DESTINATION);
      return;
    }
    if (exception instanceof OutOfTimeException) {
      receipt.setResult(contractResult.OUT_OF_TIME);
      return;
    }
    if (exception instanceof OutOfMemoryException) {
      receipt.setResult(contractResult.OUT_OF_MEMORY);
      return;
    }
    if (exception instanceof PrecompiledContractException) {
      receipt.setResult(contractResult.PRECOMPILED_CONTRACT);
      return;
    }
    if (exception instanceof StackTooSmallException) {
      receipt.setResult(contractResult.STACK_TOO_SMALL);
      return;
    }
    if (exception instanceof StackTooLargeException) {
      receipt.setResult(contractResult.STACK_TOO_LARGE);
      return;
    }
    if (exception instanceof JVMStackOverFlowException) {
      receipt.setResult(contractResult.JVM_STACK_OVER_FLOW);
      return;
    }
    if (exception instanceof TransferException) {
      receipt.setResult(contractResult.TRANSFER_FAILED);
      return;
    }

    logger.info("uncaught exception", exception);
    receipt.setResult(contractResult.UNKNOWN);
  }

  public String getRuntimeError() {
    return runtime.getRuntimeError();
  }

  public ProgramResult getRuntimeResult() {
    return runtime.getResult();
  }

  public Runtime getRuntime() {
    return runtime;
  }
}
