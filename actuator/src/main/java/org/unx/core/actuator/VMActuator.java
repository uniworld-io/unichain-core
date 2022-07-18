package org.unx.core.actuator;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.unx.common.logsfilter.trigger.ContractTrigger;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.runtime.InternalTransaction;
import org.unx.common.runtime.InternalTransaction.ExecutorType;
import org.unx.common.runtime.InternalTransaction.UnxType;
import org.unx.common.runtime.ProgramResult;
import org.unx.common.runtime.vm.DataWord;
import org.unx.common.utils.StorageUtils;
import org.unx.common.utils.StringUtil;
import org.unx.common.utils.WalletUtil;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.ContractCapsule;
import org.unx.core.capsule.ReceiptCapsule;
import org.unx.core.db.TransactionContext;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.utils.TransactionUtil;
import org.unx.core.vm.EnergyCost;
import org.unx.core.vm.LogInfoTriggerParser;
import org.unx.core.vm.OperationRegistry;
import org.unx.core.vm.VM;
import org.unx.core.vm.VMConstant;
import org.unx.core.vm.VMUtils;
import org.unx.core.vm.config.ConfigLoader;
import org.unx.core.vm.config.VMConfig;
import org.unx.core.vm.program.Program;
import org.unx.core.vm.program.Program.JVMStackOverFlowException;
import org.unx.core.vm.program.Program.OutOfTimeException;
import org.unx.core.vm.program.Program.TransferException;
import org.unx.core.vm.program.ProgramPrecompile;
import org.unx.core.vm.program.invoke.ProgramInvoke;
import org.unx.core.vm.program.invoke.ProgramInvokeFactory;
import org.unx.core.vm.repository.Repository;
import org.unx.core.vm.repository.RepositoryImpl;
import org.unx.core.vm.utils.MUtil;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.Block;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.Protocol.Transaction.Result.contractResult;
import org.unx.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract;
import org.unx.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "VM")
public class VMActuator implements Actuator2 {

  /* tx and block info */
  private Transaction unx;
  private BlockCapsule blockCap;

  /* uvm execution context */
  private Repository rootRepository;
  private Program program;
  private InternalTransaction rootInternalTx;

  /* tx receipt */
  private ReceiptCapsule receipt;

  @Getter
  @Setter
  private UnxType unxType;
  private ExecutorType executorType;

  @Getter
  @Setter
  private boolean isConstantCall;

  @Setter
  private boolean enableEventListener;

  private LogInfoTriggerParser logInfoTriggerParser;

  public VMActuator(boolean isConstantCall) {
    this.isConstantCall = isConstantCall;
  }

  private static long getEnergyFee(long callerEnergyUsage, long callerEnergyFrozen,
      long callerEnergyTotal) {
    if (callerEnergyTotal <= 0) {
      return 0;
    }
    return BigInteger.valueOf(callerEnergyFrozen).multiply(BigInteger.valueOf(callerEnergyUsage))
        .divide(BigInteger.valueOf(callerEnergyTotal)).longValueExact();
  }

  @Override
  public void validate(Object object) throws ContractValidateException {

    TransactionContext context = (TransactionContext) object;
    if (Objects.isNull(context)) {
      throw new RuntimeException("TransactionContext is null");
    }

    // Load Config
    ConfigLoader.load(context.getStoreFactory());
    // Warm up registry class
    OperationRegistry.init();
    unx = context.getUnxCap().getInstance();
    blockCap = context.getBlockCap();
    if (VMConfig.allowUvmFreeze() && context.getUnxCap().getUnxTrace() != null) {
      receipt = context.getUnxCap().getUnxTrace().getReceipt();
    }
    //Route Type
    ContractType contractType = this.unx.getRawData().getContract(0).getType();
    //Prepare Repository
    rootRepository = RepositoryImpl.createRoot(context.getStoreFactory());

    enableEventListener = context.isEventPluginLoaded();

    //set executorType type
    if (Objects.nonNull(blockCap)) {
      this.executorType = ExecutorType.ET_NORMAL_TYPE;
    } else {
      this.blockCap = new BlockCapsule(Block.newBuilder().build());
      this.executorType = ExecutorType.ET_PRE_TYPE;
    }
    if (isConstantCall) {
      this.executorType = ExecutorType.ET_PRE_TYPE;
    }

    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        unxType = UnxType.UNX_CONTRACT_CALL_TYPE;
        call();
        break;
      case ContractType.CreateSmartContract_VALUE:
        unxType = UnxType.UNX_CONTRACT_CREATION_TYPE;
        create();
        break;
      default:
        throw new ContractValidateException("Unknown contract type");
    }
  }

  @Override
  public void execute(Object object) throws ContractExeException {
    TransactionContext context = (TransactionContext) object;
    if (Objects.isNull(context)) {
      throw new RuntimeException("TransactionContext is null");
    }

    ProgramResult result = context.getProgramResult();
    try {
      if (program != null) {
        if (null != blockCap && blockCap.generatedByMyself && blockCap.hasWitnessSignature()
            && null != TransactionUtil.getContractRet(unx)
            && contractResult.OUT_OF_TIME == TransactionUtil.getContractRet(unx)) {
          result = program.getResult();
          program.spendAllEnergy();

          OutOfTimeException e = Program.Exception.alreadyTimeOut();
          result.setRuntimeError(e.getMessage());
          result.setException(e);
          throw e;
        }

        VM.play(program, OperationRegistry.getTable());
        result = program.getResult();

        if (UnxType.UNX_CONTRACT_CREATION_TYPE == unxType && !result.isRevert()) {
          byte[] code = program.getResult().getHReturn();
          if (code.length != 0 && VMConfig.allowUvmLondon() && code[0] == (byte) 0xEF) {
            if (null == result.getException()) {
              result.setException(Program.Exception.invalidCodeException());
            }
          }
          long saveCodeEnergy = (long) getLength(code) * EnergyCost.getCreateData();
          long afterSpend = program.getEnergyLimitLeft().longValue() - saveCodeEnergy;
          if (afterSpend < 0) {
            if (null == result.getException()) {
              result.setException(Program.Exception
                  .notEnoughSpendEnergy("save just created contract code",
                      saveCodeEnergy, program.getEnergyLimitLeft().longValue()));
            }
          } else {
            result.spendEnergy(saveCodeEnergy);
            if (VMConfig.allowUvmConstantinople()) {
              rootRepository.saveCode(program.getContractAddress().getNoLeadZeroesData(), code);
            }
          }
        }

        if (isConstantCall) {
          if (result.getException() != null) {
            result.setRuntimeError(result.getException().getMessage());
            result.rejectInternalTransactions();
          }
          context.setProgramResult(result);
          return;
        }

        if (result.getException() != null || result.isRevert()) {
          result.getDeleteAccounts().clear();
          result.getLogInfoList().clear();
          //result.resetFutureRefund();
          result.rejectInternalTransactions();

          if (result.getException() != null) {
            if (!(result.getException() instanceof TransferException)) {
              program.spendAllEnergy();
            }
            result.setRuntimeError(result.getException().getMessage());
            throw result.getException();
          } else {
            result.setRuntimeError("REVERT opcode executed");
          }
        } else {
          rootRepository.commit();

          if (logInfoTriggerParser != null) {
            List<ContractTrigger> triggers = logInfoTriggerParser
                .parseLogInfos(program.getResult().getLogInfoList(), rootRepository);
            program.getResult().setTriggerList(triggers);
          }

        }
      } else {
        rootRepository.commit();
      }
      for (DataWord account : result.getDeleteAccounts()) {
        RepositoryImpl.removeLruCache(account.toUnxAddress());
      }
    } catch (JVMStackOverFlowException e) {
      program.spendAllEnergy();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      result.setRuntimeError(result.getException().getMessage());
      logger.info("JVMStackOverFlowException: {}", result.getException().getMessage());
    } catch (OutOfTimeException e) {
      program.spendAllEnergy();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      result.setRuntimeError(result.getException().getMessage());
      logger.info("timeout: {}", result.getException().getMessage());
    } catch (Throwable e) {
      if (!(e instanceof TransferException)) {
        program.spendAllEnergy();
      }
      result = program.getResult();
      result.rejectInternalTransactions();
      if (Objects.isNull(result.getException())) {
        logger.error(e.getMessage(), e);
        result.setException(new RuntimeException("Unknown Throwable"));
      }
      if (StringUtils.isEmpty(result.getRuntimeError())) {
        result.setRuntimeError(result.getException().getMessage());
      }
      logger.info("runtime result is :{}", result.getException().getMessage());
    }
    //use program returned fill context
    context.setProgramResult(result);

    if (VMConfig.vmTrace() && program != null) {
      String traceContent = program.getTrace()
          .result(result.getHReturn())
          .error(result.getException())
          .toString();

      if (VMConfig.vmTraceCompressed()) {
        traceContent = VMUtils.zipAndEncode(traceContent);
      }

      String txHash = Hex.toHexString(rootInternalTx.getHash());
      VMUtils.saveProgramTraceFile(txHash, traceContent);
    }

  }

  private void create()
      throws ContractValidateException {
    if (!rootRepository.getDynamicPropertiesStore().supportVM()) {
      throw new ContractValidateException("vm work is off, need to be opened by the committee");
    }

    CreateSmartContract contract = ContractCapsule.getSmartContractFromTransaction(unx);
    if (contract == null) {
      throw new ContractValidateException("Cannot get CreateSmartContract from transaction");
    }
    SmartContract newSmartContract;
    if (VMConfig.allowUvmCompatibleEvm()) {
      newSmartContract = contract.getNewContract().toBuilder().setVersion(1).build();
    } else {
      newSmartContract = contract.getNewContract().toBuilder().clearVersion().build();
    }
    if (!contract.getOwnerAddress().equals(newSmartContract.getOriginAddress())) {
      logger.info("OwnerAddress not equals OriginAddress");
      throw new ContractValidateException("OwnerAddress is not equals OriginAddress");
    }

    byte[] contractName = newSmartContract.getName().getBytes();

    if (contractName.length > VMConstant.CONTRACT_NAME_LENGTH) {
      throw new ContractValidateException("contractName's length cannot be greater than 32");
    }

    long percent = contract.getNewContract().getConsumeUserResourcePercent();
    if (percent < 0 || percent > VMConstant.ONE_HUNDRED) {
      throw new ContractValidateException("percent must be >= 0 and <= 100");
    }

    byte[] contractAddress = WalletUtil.generateContractAddress(unx);
    // insure the new contract address haven't exist
    if (rootRepository.getAccount(contractAddress) != null) {
      throw new ContractValidateException(
          "Trying to create a contract with existing contract address: " + StringUtil
              .encode58Check(contractAddress));
    }

    newSmartContract = newSmartContract.toBuilder()
        .setContractAddress(ByteString.copyFrom(contractAddress)).build();
    long callValue = newSmartContract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowUvmTransferUrc10()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }
    byte[] callerAddress = contract.getOwnerAddress().toByteArray();
    // create vm to constructor smart contract
    try {
      long feeLimit = unx.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > rootRepository.getDynamicPropertiesStore().getMaxFeeLimit()) {
        logger.info("invalid feeLimit {}", feeLimit);
        throw new ContractValidateException("feeLimit must be >= 0 and <= "
            + rootRepository.getDynamicPropertiesStore().getMaxFeeLimit());
      }
      AccountCapsule creator = rootRepository
          .getAccount(newSmartContract.getOriginAddress().toByteArray());

      long energyLimit;
      // according to version

      if (isConstantCall) {
        energyLimit = CommonParameter.getInstance().maxEnergyLimitForConstant;
      } else {
        if (StorageUtils.getEnergyLimitHardFork()) {
          if (callValue < 0) {
            throw new ContractValidateException("callValue must be >= 0");
          }
          if (tokenValue < 0) {
            throw new ContractValidateException("tokenValue must be >= 0");
          }
          if (newSmartContract.getOriginEnergyLimit() <= 0) {
            throw new ContractValidateException("The originEnergyLimit must be > 0");
          }
          energyLimit = getAccountEnergyLimitWithFixRatio(creator, feeLimit, callValue);
        } else {
          energyLimit = getAccountEnergyLimitWithFloatRatio(creator, feeLimit, callValue);
        }
      }

      checkTokenValueAndId(tokenValue, tokenId);

      byte[] ops = newSmartContract.getBytecode().toByteArray();
      rootInternalTx = new InternalTransaction(unx, unxType);

      long maxCpuTimeOfOneTx = rootRepository.getDynamicPropertiesStore()
          .getMaxCpuTimeOfOneTx() * VMConstant.ONE_THOUSAND;
      long thisTxCPULimitInUs = (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / VMConstant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;
      ProgramInvoke programInvoke = ProgramInvokeFactory
          .createProgramInvoke(UnxType.UNX_CONTRACT_CREATION_TYPE, executorType, unx,
              tokenValue, tokenId, blockCap.getInstance(), rootRepository, vmStartInUs,
              vmShouldEndInUs, energyLimit);
      if (isConstantCall) {
        programInvoke.setConstantCall();
      }
      this.program = new Program(ops, contractAddress, programInvoke, rootInternalTx);
      if (VMConfig.allowUvmCompatibleEvm()) {
        this.program.setContractVersion(1);
      }
      byte[] txId = TransactionUtil.getTransactionId(unx).getBytes();
      this.program.setRootTransactionId(txId);
      if (enableEventListener && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(),
            txId, callerAddress);
      }
    } catch (Exception e) {
      logger.info(e.getMessage());
      throw new ContractValidateException(e.getMessage());
    }
    program.getResult().setContractAddress(contractAddress);

    rootRepository.createAccount(contractAddress, newSmartContract.getName(),
        Protocol.AccountType.Contract);

    rootRepository.createContract(contractAddress, new ContractCapsule(newSmartContract));
    byte[] code = newSmartContract.getBytecode().toByteArray();
    if (!VMConfig.allowUvmConstantinople()) {
      rootRepository.saveCode(contractAddress, ProgramPrecompile.getCode(code));
    }
    // transfer from callerAddress to contractAddress according to callValue
    if (callValue > 0) {
      MUtil.transfer(rootRepository, callerAddress, contractAddress, callValue);
    }
    if (VMConfig.allowUvmTransferUrc10() && tokenValue > 0) {
      MUtil.transferToken(rootRepository, callerAddress, contractAddress, String.valueOf(tokenId),
          tokenValue);
    }

  }

  /**
   * **
   */

  private void call()
      throws ContractValidateException {

    if (!rootRepository.getDynamicPropertiesStore().supportVM()) {
      logger.info("vm work is off, need to be opened by the committee");
      throw new ContractValidateException("VM work is off, need to be opened by the committee");
    }

    TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(unx);
    if (contract == null) {
      return;
    }

    if (contract.getContractAddress() == null) {
      throw new ContractValidateException("Cannot get contract address from TriggerContract");
    }

    byte[] contractAddress = contract.getContractAddress().toByteArray();

    ContractCapsule deployedContract = rootRepository.getContract(contractAddress);
    if (null == deployedContract) {
      logger.info("No contract or not a smart contract");
      throw new ContractValidateException("No contract or not a smart contract");
    }

    long callValue = contract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowUvmTransferUrc10()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }

    if (StorageUtils.getEnergyLimitHardFork()) {
      if (callValue < 0) {
        throw new ContractValidateException("callValue must be >= 0");
      }
      if (tokenValue < 0) {
        throw new ContractValidateException("tokenValue must be >= 0");
      }
    }

    byte[] callerAddress = contract.getOwnerAddress().toByteArray();
    checkTokenValueAndId(tokenValue, tokenId);

    byte[] code = rootRepository.getCode(contractAddress);
    if (isNotEmpty(code)) {
      long feeLimit = unx.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > rootRepository.getDynamicPropertiesStore().getMaxFeeLimit()) {
        logger.info("invalid feeLimit {}", feeLimit);
        throw new ContractValidateException("feeLimit must be >= 0 and <= "
            + rootRepository.getDynamicPropertiesStore().getMaxFeeLimit());
      }
      AccountCapsule caller = rootRepository.getAccount(callerAddress);
      long energyLimit;
      if (isConstantCall) {
        energyLimit = CommonParameter.getInstance().maxEnergyLimitForConstant;
      } else {
        AccountCapsule creator = rootRepository
            .getAccount(deployedContract.getInstance().getOriginAddress().toByteArray());
        energyLimit = getTotalEnergyLimit(creator, caller, contract, feeLimit, callValue);
      }

      long maxCpuTimeOfOneTx = rootRepository.getDynamicPropertiesStore()
          .getMaxCpuTimeOfOneTx() * VMConstant.ONE_THOUSAND;
      long thisTxCPULimitInUs =
          (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / VMConstant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;
      ProgramInvoke programInvoke = ProgramInvokeFactory
          .createProgramInvoke(UnxType.UNX_CONTRACT_CALL_TYPE, executorType, unx,
              tokenValue, tokenId, blockCap.getInstance(), rootRepository, vmStartInUs,
              vmShouldEndInUs, energyLimit);
      if (isConstantCall) {
        programInvoke.setConstantCall();
      }
      rootInternalTx = new InternalTransaction(unx, unxType);
      this.program = new Program(code, contractAddress, programInvoke, rootInternalTx);
      if (VMConfig.allowUvmCompatibleEvm()) {
        this.program.setContractVersion(deployedContract.getContractVersion());
      }
      byte[] txId = TransactionUtil.getTransactionId(unx).getBytes();
      this.program.setRootTransactionId(txId);

      if (enableEventListener && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(),
            txId, callerAddress);
      }
    }

    program.getResult().setContractAddress(contractAddress);
    //transfer from callerAddress to targetAddress according to callValue

    if (callValue > 0) {
      MUtil.transfer(rootRepository, callerAddress, contractAddress, callValue);
    }
    if (VMConfig.allowUvmTransferUrc10() && tokenValue > 0) {
      MUtil.transferToken(rootRepository, callerAddress, contractAddress, String.valueOf(tokenId),
          tokenValue);
    }

  }

  public long getAccountEnergyLimitWithFixRatio(AccountCapsule account, long feeLimit,
      long callValue) {

    long ginzaPerEnergy = VMConstant.GINZA_PER_ENERGY;
    if (rootRepository.getDynamicPropertiesStore().getEnergyFee() > 0) {
      ginzaPerEnergy = rootRepository.getDynamicPropertiesStore().getEnergyFee();
    }

    long leftFrozenEnergy = rootRepository.getAccountLeftEnergyFromFreeze(account);
    if (VMConfig.allowUvmFreeze()) {
      receipt.setCallerEnergyLeft(leftFrozenEnergy);
    }

    long energyFromBalance = max(account.getBalance() - callValue, 0) / ginzaPerEnergy;
    long availableEnergy = Math.addExact(leftFrozenEnergy, energyFromBalance);

    long energyFromFeeLimit = feeLimit / ginzaPerEnergy;
    return min(availableEnergy, energyFromFeeLimit);

  }

  private long getAccountEnergyLimitWithFloatRatio(AccountCapsule account, long feeLimit,
      long callValue) {

    long ginzaPerEnergy = VMConstant.GINZA_PER_ENERGY;
    if (rootRepository.getDynamicPropertiesStore().getEnergyFee() > 0) {
      ginzaPerEnergy = rootRepository.getDynamicPropertiesStore().getEnergyFee();
    }
    // can change the calc way
    long leftEnergyFromFreeze = rootRepository.getAccountLeftEnergyFromFreeze(account);
    callValue = max(callValue, 0);
    long energyFromBalance = Math
        .floorDiv(max(account.getBalance() - callValue, 0), ginzaPerEnergy);

    long energyFromFeeLimit;
    long totalBalanceForEnergyFreeze = account.getAllFrozenBalanceForEnergy();
    if (0 == totalBalanceForEnergyFreeze) {
      energyFromFeeLimit =
          feeLimit / ginzaPerEnergy;
    } else {
      long totalEnergyFromFreeze = rootRepository
          .calculateGlobalEnergyLimit(account);
      long leftBalanceForEnergyFreeze = getEnergyFee(totalBalanceForEnergyFreeze,
          leftEnergyFromFreeze,
          totalEnergyFromFreeze);

      if (leftBalanceForEnergyFreeze >= feeLimit) {
        energyFromFeeLimit = BigInteger.valueOf(totalEnergyFromFreeze)
            .multiply(BigInteger.valueOf(feeLimit))
            .divide(BigInteger.valueOf(totalBalanceForEnergyFreeze)).longValueExact();
      } else {
        energyFromFeeLimit = Math
            .addExact(leftEnergyFromFreeze,
                (feeLimit - leftBalanceForEnergyFreeze) / ginzaPerEnergy);
      }
    }

    return min(Math.addExact(leftEnergyFromFreeze, energyFromBalance), energyFromFeeLimit);
  }

  public long getTotalEnergyLimit(AccountCapsule creator, AccountCapsule caller,
      TriggerSmartContract contract, long feeLimit, long callValue)
      throws ContractValidateException {
    if (Objects.isNull(creator) && VMConfig.allowUvmConstantinople()) {
      return getAccountEnergyLimitWithFixRatio(caller, feeLimit, callValue);
    }
    //  according to version
    if (StorageUtils.getEnergyLimitHardFork()) {
      return getTotalEnergyLimitWithFixRatio(creator, caller, contract, feeLimit, callValue);
    } else {
      return getTotalEnergyLimitWithFloatRatio(creator, caller, contract, feeLimit, callValue);
    }
  }


  public void checkTokenValueAndId(long tokenValue, long tokenId) throws ContractValidateException {
    if (VMConfig.allowUvmTransferUrc10() && VMConfig.allowMultiSign()) {
      // tokenid can only be 0
      // or (MIN_TOKEN_ID, Long.Max]
      if (tokenId <= VMConstant.MIN_TOKEN_ID && tokenId != 0) {
        throw new ContractValidateException("tokenId must be > " + VMConstant.MIN_TOKEN_ID);
      }
      // tokenid can only be 0 when tokenvalue = 0,
      // or (MIN_TOKEN_ID, Long.Max]
      if (tokenValue > 0 && tokenId == 0) {
        throw new ContractValidateException("invalid arguments with tokenValue = "
            + tokenValue + ", tokenId = " + tokenId);
      }
    }
  }


  private double getCpuLimitInUsRatio() {

    double cpuLimitRatio;

    if (ExecutorType.ET_NORMAL_TYPE == executorType) {
      // self witness generates block
      if (blockCap != null && blockCap.generatedByMyself
          && !blockCap.hasWitnessSignature()) {
        cpuLimitRatio = 1.0;
      } else {
        // self witness or other witness or fullnode verifies block
        if (unx.getRet(0).getContractRet() == contractResult.OUT_OF_TIME) {
          cpuLimitRatio = CommonParameter.getInstance().getMinTimeRatio();
        } else {
          cpuLimitRatio = CommonParameter.getInstance().getMaxTimeRatio();
        }
      }
    } else {
      // self witness or other witness or fullnode receives tx
      cpuLimitRatio = 1.0;
    }

    return cpuLimitRatio;
  }

  public long getTotalEnergyLimitWithFixRatio(AccountCapsule creator, AccountCapsule caller,
      TriggerSmartContract contract, long feeLimit, long callValue)
      throws ContractValidateException {

    long callerEnergyLimit = getAccountEnergyLimitWithFixRatio(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      // when the creator calls his own contract, this logic will be used.
      // so, the creator must use a BIG feeLimit to call his own contract,
      // which will cost the feeLimit UNW when the creator's frozen energy is 0.
      return callerEnergyLimit;
    }

    long creatorEnergyLimit = 0;
    ContractCapsule contractCapsule = rootRepository
        .getContract(contract.getContractAddress().toByteArray());
    long consumeUserResourcePercent = contractCapsule.getConsumeUserResourcePercent();

    long originEnergyLimit = contractCapsule.getOriginEnergyLimit();
    if (originEnergyLimit < 0) {
      throw new ContractValidateException("originEnergyLimit can't be < 0");
    }

    long originEnergyLeft = 0;
    if (consumeUserResourcePercent < VMConstant.ONE_HUNDRED) {
      originEnergyLeft = rootRepository.getAccountLeftEnergyFromFreeze(creator);
      if (VMConfig.allowUvmFreeze()) {
        receipt.setOriginEnergyLeft(originEnergyLeft);
      }
    }
    if (consumeUserResourcePercent <= 0) {
      creatorEnergyLimit = min(originEnergyLeft, originEnergyLimit);
    } else {
      if (consumeUserResourcePercent < VMConstant.ONE_HUNDRED) {
        // creatorEnergyLimit =
        // min(callerEnergyLimit * (100 - percent) / percent,
        //   creatorLeftFrozenEnergy, originEnergyLimit)

        creatorEnergyLimit = min(
            BigInteger.valueOf(callerEnergyLimit)
                .multiply(BigInteger.valueOf(VMConstant.ONE_HUNDRED - consumeUserResourcePercent))
                .divide(BigInteger.valueOf(consumeUserResourcePercent)).longValueExact(),
            min(originEnergyLeft, originEnergyLimit)
        );
      }
    }
    return Math.addExact(callerEnergyLimit, creatorEnergyLimit);
  }

  private long getTotalEnergyLimitWithFloatRatio(AccountCapsule creator, AccountCapsule caller,
      TriggerSmartContract contract, long feeLimit, long callValue) {

    long callerEnergyLimit = getAccountEnergyLimitWithFloatRatio(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      return callerEnergyLimit;
    }

    // creatorEnergyFromFreeze
    long creatorEnergyLimit = rootRepository.getAccountLeftEnergyFromFreeze(creator);

    ContractCapsule contractCapsule = rootRepository
        .getContract(contract.getContractAddress().toByteArray());
    long consumeUserResourcePercent = contractCapsule.getConsumeUserResourcePercent();

    if (creatorEnergyLimit * consumeUserResourcePercent
        > (VMConstant.ONE_HUNDRED - consumeUserResourcePercent) * callerEnergyLimit) {
      return Math.floorDiv(callerEnergyLimit * VMConstant.ONE_HUNDRED, consumeUserResourcePercent);
    } else {
      return Math.addExact(callerEnergyLimit, creatorEnergyLimit);
    }
  }

  private boolean isCheckTransaction() {
    return this.blockCap != null && !this.blockCap.getInstance().getBlockHeader()
        .getWitnessSignature().isEmpty();
  }

}
