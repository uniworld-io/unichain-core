package org.unichain.common.runtime;

import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.spongycastle.util.encoders.Hex;
import org.unichain.common.logsfilter.EventPluginLoader;
import org.unichain.common.logsfilter.trigger.ContractTrigger;
import org.unichain.common.runtime.config.VMConfig;
import org.unichain.common.runtime.vm.*;
import org.unichain.common.runtime.vm.program.InternalTransaction;
import org.unichain.common.runtime.vm.program.InternalTransaction.ExecutorType;
import org.unichain.common.runtime.vm.program.InternalTransaction.UnxType;
import org.unichain.common.runtime.vm.program.Program;
import org.unichain.common.runtime.vm.program.Program.JVMStackOverFlowException;
import org.unichain.common.runtime.vm.program.Program.OutOfTimeException;
import org.unichain.common.runtime.vm.program.Program.TransferException;
import org.unichain.common.runtime.vm.program.ProgramPrecompile;
import org.unichain.common.runtime.vm.program.ProgramResult;
import org.unichain.common.runtime.vm.program.invoke.ProgramInvoke;
import org.unichain.common.runtime.vm.program.invoke.ProgramInvokeFactory;
import org.unichain.common.storage.Deposit;
import org.unichain.common.storage.DepositImpl;
import org.unichain.core.Constant;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.Actuator;
import org.unichain.core.actuator.ActuatorFactory;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.ContractCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.EnergyProcessor;
import org.unichain.core.db.TransactionTrace;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.VMIllegalException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.CreateSmartContract;
import org.unichain.protos.Contract.TriggerSmartContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Block;
import org.unichain.protos.Protocol.SmartContract;
import org.unichain.protos.Protocol.Transaction;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;
import org.unichain.protos.Protocol.Transaction.Result.contractResult;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.unichain.common.runtime.utils.MUtil.*;
import static org.unichain.core.config.Parameter.ChainConstant.BLOCK_VERSION_1;

@Slf4j(topic = "VM")
public class RuntimeImpl implements Runtime {
  private VMConfig config = VMConfig.getInstance();
  private Transaction unx;
  private BlockCapsule blockCap;
  private Deposit deposit;
  private ProgramInvokeFactory programInvokeFactory;
  private String runtimeError;

  private EnergyProcessor energyProcessor;
  private ProgramResult result = new ProgramResult();

  private VM vm;
  private Program program;
  private InternalTransaction rootInternalTransaction;

  @Getter
  @Setter
  private InternalTransaction.UnxType unxType;
  private ExecutorType executorType;

  private TransactionTrace trace;

  @Getter
  @Setter
  private boolean isConstantCall = false;

  @Setter
  private boolean enableEventListener;

  private LogInfoTriggerParser logInfoTriggerParser;

  /**
   * For blockCap's unx run
   */
  public RuntimeImpl(TransactionTrace trace, BlockCapsule block, int blockVersion, Deposit deposit, ProgramInvokeFactory programInvokeFactory) {
    this.trace = trace;
    this.unx = trace.getUnx().getInstance();

    if (Objects.nonNull(block)) {
      this.blockCap = block;
      this.executorType = ExecutorType.ET_NORMAL_TYPE;
    } else {
      //@fixme set block version
      var headerRaw = Protocol.BlockHeader.raw.newBuilder().setVersion(blockVersion).build();
      var header = Protocol.BlockHeader.newBuilder().setRawData(headerRaw).build();
      this.blockCap = new BlockCapsule(Block.newBuilder().setBlockHeader(header).build());
      this.executorType = ExecutorType.ET_PRE_TYPE;
    }
    this.deposit = deposit;
    this.programInvokeFactory = programInvokeFactory;
    this.energyProcessor = new EnergyProcessor(deposit.getDbManager());

    ContractType contractType = this.unx.getRawData().getContract(0).getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        unxType = UnxType.UNW_CONTRACT_CALL_TYPE;
        break;
      case ContractType.CreateSmartContract_VALUE:
        unxType = UnxType.UNW_CONTRACT_CREATION_TYPE;
        break;
      default:
        unxType = UnxType.UNW_PRECOMPILED_TYPE;
    }
  }

  /**
   * For constant unx with latest blockCap.
   */
  public RuntimeImpl(Transaction tx, BlockCapsule block, DepositImpl deposit, ProgramInvokeFactory programInvokeFactory, boolean isConstantCall) {
    this(tx, block, deposit, programInvokeFactory);
    this.isConstantCall = isConstantCall;
  }

  private RuntimeImpl(Transaction tx, BlockCapsule block, DepositImpl deposit, ProgramInvokeFactory programInvokeFactory) {
    this.unx = tx;
    this.deposit = deposit;
    this.programInvokeFactory = programInvokeFactory;
    this.executorType = ExecutorType.ET_PRE_TYPE;
    this.blockCap = block;
    this.energyProcessor = new EnergyProcessor(deposit.getDbManager());
    ContractType contractType = tx.getRawData().getContract(0).getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        unxType = UnxType.UNW_CONTRACT_CALL_TYPE;
        break;
      case ContractType.CreateSmartContract_VALUE:
        unxType = UnxType.UNW_CONTRACT_CREATION_TYPE;
        break;
      default:
        unxType = UnxType.UNW_PRECOMPILED_TYPE;
    }
  }

  private void setupPrecompiled() throws ContractValidateException, ContractExeException {
    TransactionCapsule txCap = new TransactionCapsule(unx);
    for (Actuator act : ActuatorFactory.createActuator(blockCap, txCap, deposit.getDbManager())) {
      act.validate();
      act.execute(result.getRet());
    }
  }

  @Override
  public void setup() throws ContractValidateException, ContractExeException, VMIllegalException {
    switch (unxType) {
      case UNW_PRECOMPILED_TYPE:
        setupPrecompiled();
        break;
      case UNW_CONTRACT_CREATION_TYPE:
        setupCreateContract();
        break;
      case UNW_CONTRACT_CALL_TYPE:
        setupCallContract();
        break;
      default:
        throw new ContractValidateException("Unknown contract type");
    }
  }

   /**
   * Get account energy block v2:
   * - from balance
   * - exclude in-transaction transfer
   * - exclude frozen balance
   */
  public long getAccountEnergyLimitV2(AccountCapsule account, long feeLimit, long callValue) {
    long ginzaPerEnergy = deposit.getDbManager().loadEnergyGinzaFactor();
    callValue = max(callValue, 0);
    long energyFromBalance =  Math.floorDiv(max(account.getBalance() - callValue, 0), ginzaPerEnergy);
    long energyFromFeeLimit = feeLimit / ginzaPerEnergy;
    return min(energyFromBalance, energyFromFeeLimit);
  }

  public long getAccountEnergyLimit(AccountCapsule account) {
    long ginzaPerEnergy = deposit.getDbManager().loadEnergyGinzaFactor();
    return Math.floorDiv(account.getBalance(), ginzaPerEnergy);
  }

  public long getAccountEnergyLimitWithFixRatio(AccountCapsule account, long feeLimit, long callValue) {
    long ginzaPerEnergy = Constant.GINZA_PER_ENERGY;
    if (deposit.getDbManager().getDynamicPropertiesStore().getEnergyFee() > 0) {
      ginzaPerEnergy = deposit.getDbManager().getDynamicPropertiesStore().getEnergyFee();
    }

    long leftFrozenEnergy = energyProcessor.getAccountLeftEnergyFromFreeze(account);

    long energyFromBalance = max(account.getBalance() - callValue, 0) / ginzaPerEnergy;
    long availableEnergy = Math.addExact(leftFrozenEnergy, energyFromBalance);

    long energyFromFeeLimit = feeLimit / ginzaPerEnergy;
    return min(availableEnergy, energyFromFeeLimit);

  }

  /**
   * Get account energy with float ratio
   */
  private long getAccountEnergyLimitV1(AccountCapsule account, long feeLimit, long callValue) {

    long ginzaPerEnergy = Constant.GINZA_PER_ENERGY;
    if (deposit.getDbManager().getDynamicPropertiesStore().getEnergyFee() > 0) {
      ginzaPerEnergy = deposit.getDbManager().getDynamicPropertiesStore().getEnergyFee();
    }
    long leftEnergyFromFreeze = energyProcessor.getAccountLeftEnergyFromFreeze(account);
    callValue = max(callValue, 0);
    long energyFromBalance = Math.floorDiv(max(account.getBalance() - callValue, 0), ginzaPerEnergy);

    long energyFromFeeLimit;
    long totalBalanceForEnergyFreeze = account.getAllFrozenBalanceForEnergy();
    if (0 == totalBalanceForEnergyFreeze) {
      energyFromFeeLimit = feeLimit / ginzaPerEnergy;
    } else {
      long totalEnergyFromFreeze = energyProcessor.calculateGlobalEnergyLimit(account);
      long leftBalanceForEnergyFreeze = getEnergyFee(totalBalanceForEnergyFreeze, leftEnergyFromFreeze, totalEnergyFromFreeze);

      if (leftBalanceForEnergyFreeze >= feeLimit) {
        energyFromFeeLimit = BigInteger.valueOf(totalEnergyFromFreeze)
            .multiply(BigInteger.valueOf(feeLimit))
            .divide(BigInteger.valueOf(totalBalanceForEnergyFreeze)).longValueExact();
      } else {
        energyFromFeeLimit = Math.addExact(leftEnergyFromFreeze, (feeLimit - leftBalanceForEnergyFreeze) / ginzaPerEnergy);
      }
    }
    return min(Math.addExact(leftEnergyFromFreeze, energyFromBalance), energyFromFeeLimit);
  }

  private long getTotalEnergyLimitWithFloatRatioV2(AccountCapsule creator, AccountCapsule caller, TriggerSmartContract contract, long feeLimit, long callValue) {
    long callerEnergyLimit = getAccountEnergyLimitV2(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      return callerEnergyLimit;
    }

    long creatorEnergyLimit = getAccountEnergyLimit(creator);
    long consumeUserResourcePercent = this.deposit.getContract(contract.getContractAddress().toByteArray()).getConsumeUserResourcePercent();

    if (creatorEnergyLimit * consumeUserResourcePercent > (Constant.ONE_HUNDRED - consumeUserResourcePercent) * callerEnergyLimit) {
      return Math.floorDiv(callerEnergyLimit * Constant.ONE_HUNDRED, consumeUserResourcePercent);
    } else {
      return Math.addExact(callerEnergyLimit, creatorEnergyLimit);
    }
  }

  private long getTotalEnergyLimitWithFloatRatioV1(AccountCapsule creator, AccountCapsule caller, TriggerSmartContract contract, long feeLimit, long callValue) {
    long callerEnergyLimit = getAccountEnergyLimitV1(caller, feeLimit, callValue);
    if (Arrays.equals(creator.getAddress().toByteArray(), caller.getAddress().toByteArray())) {
      return callerEnergyLimit;
    }

    long creatorEnergyLimit = energyProcessor.getAccountLeftEnergyFromFreeze(creator);

    ContractCapsule contractCapsule = this.deposit.getContract(contract.getContractAddress().toByteArray());
    long consumeUserResourcePercent = contractCapsule.getConsumeUserResourcePercent();

    if (creatorEnergyLimit * consumeUserResourcePercent > (Constant.ONE_HUNDRED - consumeUserResourcePercent) * callerEnergyLimit) {
      return Math.floorDiv(callerEnergyLimit * Constant.ONE_HUNDRED, consumeUserResourcePercent);
    } else {
      return Math.addExact(callerEnergyLimit, creatorEnergyLimit);
    }
  }

  public long getTotalEnergyLimit(AccountCapsule creator, AccountCapsule caller, TriggerSmartContract contract, long feeLimit, long callValue) throws ContractValidateException {
    if (Objects.isNull(creator) && VMConfig.allowTvmConstantinople()) {
      return (findBlockVersion() <= BLOCK_VERSION_1) ?
              getAccountEnergyLimitWithFixRatio(caller, feeLimit, callValue) : getAccountEnergyLimitV2(caller, feeLimit, callValue);
    }
    else {
      return (findBlockVersion() <= BLOCK_VERSION_1) ?
              getTotalEnergyLimitWithFloatRatioV1(creator, caller, contract, feeLimit, callValue) : getTotalEnergyLimitWithFloatRatioV2(creator, caller, contract, feeLimit, callValue);
    }
  }

  private boolean isCheckTransaction() {
    return this.blockCap != null && !this.blockCap.getInstance().getBlockHeader().getWitnessSignature().isEmpty();
  }

  private double getCpuLimitInUsRatio() {
    double cpuLimitRatio;
    if (ExecutorType.ET_NORMAL_TYPE == executorType) {
      // self witness generates block
      if (this.blockCap != null && blockCap.generatedByMyself &&
          this.blockCap.getInstance().getBlockHeader().getWitnessSignature().isEmpty()) {
        cpuLimitRatio = 1.0;
      } else {
        // self witness or other witness or fullnode verifies block
        if (unx.getRet(0).getContractRet() == contractResult.OUT_OF_TIME) {
          cpuLimitRatio = Args.getInstance().getMinTimeRatio();
        } else {
          cpuLimitRatio = Args.getInstance().getMaxTimeRatio();
        }
      }
    } else {
      // self witness or other witness or fullnode receives tx
      cpuLimitRatio = 1.0;
    }

    return cpuLimitRatio;
  }

  /**
   * Setup create contract:
   * - create new smart contract
   * - max fee/feeLimit that owner can afford
   * - estimate energy limit
   * - shared energy percent
   * - if one account already has frozen balance for energy, just un-freeze the balance
   */
  private void setupCreateContract() throws ContractValidateException, VMIllegalException {
    //base validation
    if (!deposit.getDbManager().getDynamicPropertiesStore().supportVM()) {
      throw new ContractValidateException("vm work is off, need to be opened by the committee");
    }

    CreateSmartContract contract = ContractCapsule.getSmartContractFromTransaction(unx);
    if (contract == null) {
      throw new ContractValidateException("Cannot get CreateSmartContract from transaction");
    }
    SmartContract newSmartContract = contract.getNewContract();

    if (!contract.getOwnerAddress().equals(newSmartContract.getOriginAddress())) {
      logger.info("OwnerAddress not equals OriginAddress");
      throw new VMIllegalException("OwnerAddress is not equals OriginAddress");
    }

    byte[] contractName = newSmartContract.getName().getBytes();
    if (contractName.length > VMConstant.CONTRACT_NAME_LENGTH) {
      throw new ContractValidateException("contractName's length cannot be greater than 32");
    }

    //shared energy percent
    long percent = contract.getNewContract().getConsumeUserResourcePercent();
    if (percent < 0 || percent > Constant.ONE_HUNDRED) {
      throw new ContractValidateException("percent must be >= 0 and <= 100");
    }

    byte[] contractAddress = Wallet.generateContractAddress(unx);
    if (deposit.getAccount(contractAddress) != null) {
      throw new ContractValidateException("Trying to create a contract with existing contract address: " + Wallet.encode58Check(contractAddress));
    }

    newSmartContract = newSmartContract
            .toBuilder()
            .setContractAddress(ByteString.copyFrom(contractAddress))
            .build();
    long callValue = newSmartContract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowTvmTransferUnc()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }
    byte[] callerAddress = contract.getOwnerAddress().toByteArray();

    try {
      //feeLimit: max affordable fee
      long feeLimit = unx.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > VMConfig.MAX_FEE_LIMIT) {
        logger.info("invalid feeLimit {}", feeLimit);
        throw new ContractValidateException("feeLimit must be >= 0 and <= " + VMConfig.MAX_FEE_LIMIT);
      }

      AccountCapsule creator = this.deposit.getAccount(newSmartContract.getOriginAddress().toByteArray());

      //estimate energy limit affordable to execute TX
      logger.info("==============================Before cal energyLimit===================");
      long energyLimit = (findBlockVersion() <= BLOCK_VERSION_1) ?
              getAccountEnergyLimitV1(creator, feeLimit, callValue) : getAccountEnergyLimitV2(creator, feeLimit, callValue);
      logger.info("==============================After cal energyLimit {} ===================", energyLimit);


      checkTokenValueAndId(tokenValue, tokenId);

      byte[] ops = newSmartContract.getBytecode().toByteArray();
      rootInternalTransaction = new InternalTransaction(unx, unxType);

      //estimate time limit affordable to execute TX
      long maxCpuTimeOfOneTx = deposit.getDbManager().getDynamicPropertiesStore().getMaxCpuTimeOfOneTx() * Constant.ONE_THOUSAND;
      long thisTxCPULimitInUs = (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / Constant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;

      ProgramInvoke programHandler = programInvokeFactory
              .createProgramInvoke(UnxType.UNW_CONTRACT_CREATION_TYPE, executorType, unx,
              tokenValue, tokenId, blockCap.getInstance(), deposit, vmStartInUs,
              vmShouldEndInUs, energyLimit);
      this.vm = new VM(config);
      this.program = new Program(ops, programHandler, rootInternalTransaction, config, this.blockCap);
      byte[] txId = new TransactionCapsule(unx).getTransactionId().getBytes();
      this.program.setRootTransactionId(txId);
      if (enableEventListener
              && (EventPluginLoader.getInstance().isContractEventTriggerEnable() || EventPluginLoader.getInstance().isContractLogTriggerEnable())
              && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(), txId, callerAddress);
      }
    } catch (Exception e) {
      logger.info(e.getMessage());
      throw new ContractValidateException(e.getMessage());
    }

    program.getResult().setContractAddress(contractAddress);
    deposit.createAccount(contractAddress, newSmartContract.getName(), Protocol.AccountType.Contract);
    deposit.createContract(contractAddress, new ContractCapsule(newSmartContract));
    byte[] code = newSmartContract.getBytecode().toByteArray();
    if (!VMConfig.allowTvmConstantinople()) {
      deposit.saveCode(contractAddress, ProgramPrecompile.getCode(code));
    }

    //transfer from callerAddress to  contractAddress amount [callValue]
    if (callValue > 0) {
      logger.info("==============================Before  transfer contract===================");
      transfer(this.deposit, callerAddress, contractAddress, callValue);
      logger.info("==============================After  transfer contract===================");
    }

    //also transfer token
    if (VMConfig.allowTvmTransferUnc()) {
      if (tokenValue > 0) {
        transferToken(this.deposit, callerAddress, contractAddress, String.valueOf(tokenId), tokenValue);
      }
    }
  }

  private void setupCallContract()throws ContractValidateException {
    if (!deposit.getDbManager().getDynamicPropertiesStore().supportVM()) {
      logger.info("VM work is off, need to be opened by the committee");
      throw new ContractValidateException("VM work is off, need to be opened by the committee");
    }

    Contract.TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(unx);
    if (contract == null) {
      return;
    }

    if (contract.getContractAddress() == null) {
      throw new ContractValidateException("Cannot get contract address from TriggerContract");
    }

    byte[] contractAddress = contract.getContractAddress().toByteArray();

    ContractCapsule deployedContract = this.deposit.getContract(contractAddress);
    if (deployedContract == null) {
      logger.info("No contract or not a smart contract");
      throw new ContractValidateException("No contract or not a smart contract");
    }

    long callValue = contract.getCallValue();
    long tokenValue = 0;
    long tokenId = 0;
    if (VMConfig.allowTvmTransferUnc()) {
      tokenValue = contract.getCallTokenValue();
      tokenId = contract.getTokenId();
    }

    byte[] callerAddress = contract.getOwnerAddress().toByteArray();
    checkTokenValueAndId(tokenValue, tokenId);

    byte[] code = this.deposit.getCode(contractAddress);
    if (isNotEmpty(code)) {
      long feeLimit = unx.getRawData().getFeeLimit();
      if (feeLimit < 0 || feeLimit > VMConfig.MAX_FEE_LIMIT) {
        logger.info("Invalid feeLimit {}", feeLimit);
        throw new ContractValidateException("FeeLimit must be >= 0 and <= " + VMConfig.MAX_FEE_LIMIT);
      }
      AccountCapsule caller = this.deposit.getAccount(callerAddress);
      long energyLimit;

      if (isConstantCall) {
        energyLimit = Constant.ENERGY_LIMIT_IN_CONSTANT_TX;
      } else {
        //estimate affordable energy limit
        AccountCapsule creator = this.deposit.getAccount(deployedContract.getInstance().getOriginAddress().toByteArray());
        energyLimit = getTotalEnergyLimit(creator, caller, contract, feeLimit, callValue);
        logger.info("================================After cal energyLimit===========================");
      }

      //estimate affordable exec time limit
      long maxCpuTimeOfOneTx = deposit.getDbManager().getDynamicPropertiesStore().getMaxCpuTimeOfOneTx() * Constant.ONE_THOUSAND;
      long thisTxCPULimitInUs = (long) (maxCpuTimeOfOneTx * getCpuLimitInUsRatio());
      long vmStartInUs = System.nanoTime() / Constant.ONE_THOUSAND;
      long vmShouldEndInUs = vmStartInUs + thisTxCPULimitInUs;

      //setup program & vm
      ProgramInvoke programInvoke = programInvokeFactory.createProgramInvoke(UnxType.UNW_CONTRACT_CALL_TYPE, executorType, unx, tokenValue, tokenId, blockCap.getInstance(), deposit, vmStartInUs, vmShouldEndInUs, energyLimit);
      if (isConstantCall) {
        programInvoke.setConstantCall();
      }
      this.vm = new VM(config);
      rootInternalTransaction = new InternalTransaction(unx, unxType);
      this.program = new Program(code, programInvoke, rootInternalTransaction, config, this.blockCap);
      byte[] txId = new TransactionCapsule(unx).getTransactionId().getBytes();
      this.program.setRootTransactionId(txId);

      if (enableEventListener && (EventPluginLoader.getInstance().isContractEventTriggerEnable() || EventPluginLoader.getInstance().isContractLogTriggerEnable()) && isCheckTransaction()) {
        logInfoTriggerParser = new LogInfoTriggerParser(blockCap.getNum(), blockCap.getTimeStamp(), txId, callerAddress);
      }
    }

    program.getResult().setContractAddress(contractAddress);
    if (callValue > 0) {
      logger.info("================================Before transfer contract===========================");
      transfer(this.deposit, callerAddress, contractAddress, callValue);
      logger.info("================================After transfer contract===========================");
    }
    if (VMConfig.allowTvmTransferUnc()) {
      if (tokenValue > 0) {
        transferToken(this.deposit, callerAddress, contractAddress, String.valueOf(tokenId), tokenValue);
      }
    }
  }


  /**
   * Execute/play or save contract
   *     - play smart contract
   *     - save contract if needed
   *     - if error: spend all energy
   *     - if create contract: charge energy of saving code
   */
  @Override
  public void go() {
    try {
      if (vm != null) {
        //if out of time: spend all energy left = energyLimit - energyUsed
        TransactionCapsule unxCap = new TransactionCapsule(unx);
        if (null != blockCap && blockCap.generatedByMyself && null != unxCap.getContractRet() && contractResult.OUT_OF_TIME == unxCap.getContractRet()) {
          result = program.getResult();
          program.spendAllEnergy();//spend all energy left ( = energyLimit - energyUsed)
          OutOfTimeException e = Program.Exception.alreadyTimeOut();
          runtimeError = e.getMessage();
          result.setException(e);
          throw e;
        }

        //Execute program: play OpCode & charge energy
        vm.play(program);
        //save result
        result = program.getResult();

        if (isConstantCall) {
          long callValue = TransactionCapsule.getCallValue(unx.getRawData().getContract(0));
          long callTokenValue = TransactionCapsule.getCallTokenValue(unx.getRawData().getContract(0));
          if (callValue > 0 || callTokenValue > 0) {
            runtimeError = "constant cannot set call value or call token value.";
            result.rejectInternalTransactions();
            logger.info(runtimeError);
          }
          if (result.getException() != null) {
            runtimeError = result.getException().getMessage();
            result.rejectInternalTransactions();
          }
          return;
        }

        //if create contract: save code & charge saving energy
        if (UnxType.UNW_CONTRACT_CREATION_TYPE == unxType && !result.isRevert()) {
          byte[] code = program.getResult().getHReturn();
          long saveCodeEnergy = (long) getLength(code) * EnergyCost.getInstance().getCREATE_DATA();
          long afterSpend = program.getEnergyLimitLeft().longValue() - saveCodeEnergy;
          if (afterSpend < 0) {
            //out of energy
            if (null == result.getException()) {
              result.setException(Program.Exception.notEnoughSpendEnergy("save just created contract code", saveCodeEnergy, program.getEnergyLimitLeft().longValue()));
            }
          } else {
            //good, just save code
            result.spendEnergy(saveCodeEnergy);
            if (VMConfig.allowTvmConstantinople()) {
              deposit.saveCode(program.getContractAddress().getNoLeadZeroesData(), code);
            }
          }
        }

        //error or revert
        if (result.getException() != null || result.isRevert()) {
          result.getDeleteAccounts().clear();
          result.getLogInfoList().clear();
          result.resetFutureRefund();
          result.rejectInternalTransactions();

          if (result.getException() != null) {
            logger.info("=====================Exception when create contract {}", result.getException().getMessage());
            if (!(result.getException() instanceof TransferException)) {
              //spend all energy
              program.spendAllEnergy();
            }
            runtimeError = result.getException().getMessage();
            throw result.getException();
          } else {
            runtimeError = "REVERT opcode executed";
            logger.info("========================Revert when create contract");
          }
        } else {
          deposit.commit();
          logger.info("================================Commit cache");
          if (logInfoTriggerParser != null) {
            List<ContractTrigger> triggers = logInfoTriggerParser.parseLogInfos(program.getResult().getLogInfoList(), this.deposit);
            program.getResult().setTriggerList(triggers);
          }

        }
      } else {
        deposit.commit();
        logger.info("==============================VM is null ");
      }
    } catch (JVMStackOverFlowException e) {
      program.spendAllEnergy();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      runtimeError = result.getException().getMessage();
      logger.error("JVMStackOverFlowException: {}", result.getException().getMessage(), result.getException());
    } catch (OutOfTimeException e) {
      program.spendAllEnergy();
      result = program.getResult();
      result.setException(e);
      result.rejectInternalTransactions();
      runtimeError = result.getException().getMessage();
      logger.error("Timeout: {}", result.getException().getMessage(), result.getException());
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
      if (StringUtils.isEmpty(runtimeError)) {
        runtimeError = result.getException().getMessage();
      }
      logger.error("Runtime result is :{}", result.getException().getMessage(), result.getException());
    }

    if (!isConstantCall) {
      trace.setEnergyBill(result.getEnergyUsed());
    }
  }

  private static long getEnergyFee(long callerEnergyUsage, long callerEnergyFrozen, long callerEnergyTotal) {
    if (callerEnergyTotal <= 0) {
      return 0;
    }
    return BigInteger.valueOf(callerEnergyFrozen)
            .multiply(BigInteger.valueOf(callerEnergyUsage))
            .divide(BigInteger.valueOf(callerEnergyTotal)).longValueExact();
  }

  public void finalization() {
    if (StringUtils.isEmpty(runtimeError)) {
      for (DataWord contract : result.getDeleteAccounts()) {
        deposit.deleteContract(convertToUnichainAddress((contract.getLast20Bytes())));
      }
    }

    if (config.vmTrace() && program != null) {
      String traceContent = program.getTrace()
          .result(result.getHReturn())
          .error(result.getException())
          .toString();

      if (config.vmTraceCompressed()) {
        traceContent = VMUtils.zipAndEncode(traceContent);
      }

      String txHash = Hex.toHexString(rootInternalTransaction.getHash());
      VMUtils.saveProgramTraceFile(config, txHash, traceContent);
    }
  }

  public void checkTokenValueAndId(long tokenValue, long tokenId) throws ContractValidateException {
    if (VMConfig.allowTvmTransferUnc()) {
      if (VMConfig.allowMultiSign()) { //allowMultiSigns
        // tokenid can only be 0
        // or (MIN_TOKEN_ID, Long.Max]
        if (tokenId <= VMConstant.MIN_TOKEN_ID && tokenId != 0) {
          throw new ContractValidateException("tokenId must > " + VMConstant.MIN_TOKEN_ID);
        }
        // tokenid can only be 0 when tokenvalue = 0,
        // or (MIN_TOKEN_ID, Long.Max]
        if (tokenValue > 0 && tokenId == 0) {
          throw new ContractValidateException("invalid arguments with tokenValue = " + tokenValue + ", tokenId = " + tokenId);
        }
      }
    }
  }

  public ProgramResult getResult() {
    return result;
  }

  public String getRuntimeError() {
    return runtimeError;
  }

  private int findBlockVersion(){
    return  (blockCap == null) ? deposit.getDbManager().getDynamicPropertiesStore().getBlockVersion() : blockCap.getInstance().getBlockHeader().getRawData().getVersion();
  }
}
