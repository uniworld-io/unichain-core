package org.unx.core.db;

import static org.unx.common.runtime.InternalTransaction.UnxType.UNX_CONTRACT_CALL_TYPE;
import static org.unx.common.runtime.InternalTransaction.UnxType.UNX_CONTRACT_CREATION_TYPE;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.util.StringUtils;
import org.unx.common.runtime.InternalTransaction.UnxType;
import org.unx.common.runtime.ProgramResult;
import org.unx.common.runtime.Runtime;
import org.unx.common.runtime.vm.DataWord;
import org.unx.common.utils.DecodeUtil;
import org.unx.common.utils.ForkController;
import org.unx.common.utils.Sha256Hash;
import org.unx.common.utils.StringUtil;
import org.unx.common.utils.WalletUtil;
import org.unx.core.Constant;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.ContractCapsule;
import org.unx.core.capsule.ReceiptCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.exception.BalanceInsufficientException;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.ReceiptCheckErrException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.store.AbiStore;
import org.unx.core.store.AccountStore;
import org.unx.core.store.CodeStore;
import org.unx.core.store.ContractStore;
import org.unx.core.store.DynamicPropertiesStore;
import org.unx.core.store.StoreFactory;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.Protocol.Transaction.Result.contractResult;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract.ABI;
import org.unx.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "TransactionTrace")
public class TransactionTrace {

  private TransactionCapsule unx;

  private ReceiptCapsule receipt;

  private StoreFactory storeFactory;

  private DynamicPropertiesStore dynamicPropertiesStore;

  private ContractStore contractStore;

  private AccountStore accountStore;

  private CodeStore codeStore;

  private AbiStore abiStore;

  private EnergyProcessor energyProcessor;

  private UnxType unxType;

  private long txStartTimeInMs;

  private Runtime runtime;

  private ForkController forkController;

  @Getter
  private TransactionContext transactionContext;
  @Getter
  @Setter
  private TimeResultType timeResultType = TimeResultType.NORMAL;
  @Getter
  @Setter
  private boolean netFeeForBandwidth = true;

  public TransactionTrace(TransactionCapsule unx, StoreFactory storeFactory,
      Runtime runtime) {
    this.unx = unx;
    Transaction.Contract.ContractType contractType = this.unx.getInstance().getRawData()
        .getContract(0).getType();
    switch (contractType.getNumber()) {
      case ContractType.TriggerSmartContract_VALUE:
        unxType = UNX_CONTRACT_CALL_TYPE;
        break;
      case ContractType.CreateSmartContract_VALUE:
        unxType = UNX_CONTRACT_CREATION_TYPE;
        break;
      default:
        unxType = UnxType.UNX_PRECOMPILED_TYPE;
    }
    this.storeFactory = storeFactory;
    this.dynamicPropertiesStore = storeFactory.getChainBaseManager().getDynamicPropertiesStore();
    this.contractStore = storeFactory.getChainBaseManager().getContractStore();
    this.codeStore = storeFactory.getChainBaseManager().getCodeStore();
    this.abiStore = storeFactory.getChainBaseManager().getAbiStore();
    this.accountStore = storeFactory.getChainBaseManager().getAccountStore();

    this.receipt = new ReceiptCapsule(Sha256Hash.ZERO_HASH);
    this.energyProcessor = new EnergyProcessor(dynamicPropertiesStore, accountStore);
    this.runtime = runtime;
    this.forkController = new ForkController();
    forkController.init(storeFactory.getChainBaseManager());
  }

  public TransactionCapsule getUnw() {
    return unx;
  }

  private boolean needVM() {
    return this.unxType == UNX_CONTRACT_CALL_TYPE
        || this.unxType == UNX_CONTRACT_CREATION_TYPE;
  }

  public void init(BlockCapsule blockCap) {
    init(blockCap, false);
  }

  //pre transaction check
  public void init(BlockCapsule blockCap, boolean eventPluginLoaded) {
    txStartTimeInMs = System.currentTimeMillis();
    transactionContext = new TransactionContext(blockCap, unx, storeFactory, false,
        eventPluginLoaded);
  }

  public void checkIsConstant() throws ContractValidateException, VMIllegalException {
    if (dynamicPropertiesStore.getAllowUvmConstantinople() == 1) {
      return;
    }
    TriggerSmartContract triggerContractFromTransaction = ContractCapsule
        .getTriggerContractFromTransaction(this.getUnw().getInstance());
    if (UNX_CONTRACT_CALL_TYPE == this.unxType) {
      ContractCapsule contract = contractStore
          .get(triggerContractFromTransaction.getContractAddress().toByteArray());
      if (contract == null) {
        logger.info("contract: {} is not in contract store", StringUtil
            .encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray()));
        throw new ContractValidateException("contract: " + StringUtil
            .encode58Check(triggerContractFromTransaction.getContractAddress().toByteArray())
            + " is not in contract store");
      }
      ABI abi = contract.getInstance().getAbi();
      if (WalletUtil.isConstant(abi, triggerContractFromTransaction)) {
        throw new VMIllegalException("cannot call constant method");
      }
    }
  }

  //set bill
  public void setBill(long energyUsage) {
    if (energyUsage < 0) {
      energyUsage = 0L;
    }
    receipt.setEnergyUsageTotal(energyUsage);
  }

  //set net bill
  public void setNetBill(long netUsage, long netFee) {
    receipt.setNetUsage(netUsage);
    receipt.setNetFee(netFee);
  }

  public void setNetBillForCreateNewAccount(long netUsage, long netFee) {
    receipt.setNetUsage(netUsage);
    receipt.setNetFee(netFee);
    setNetFeeForBandwidth(false);
  }

  public void addNetBill(long netFee) {
    receipt.addNetFee(netFee);
  }

  public void exec()
      throws ContractExeException, ContractValidateException, VMIllegalException {
    /*  VM execute  */
    runtime.execute(transactionContext);
    setBill(transactionContext.getProgramResult().getEnergyUsed());

//    if (UnwType.UNW_PRECOMPILED_TYPE != unxType) {
//      if (contractResult.OUT_OF_TIME
//          .equals(receipt.getResult())) {
//        setTimeResultType(TimeResultType.OUT_OF_TIME);
//      } else if (System.currentTimeMillis() - txStartTimeInMs
//          > CommonParameter.getInstance()
//          .getLongRunningTime()) {
//        setTimeResultType(TimeResultType.LONG_RUNNING);
//      }
//    }
  }

  public void saveEnergyLeftOfOrigin(long energyLeft) {
    receipt.setOriginEnergyLeft(energyLeft);
  }

  public void saveEnergyLeftOfCaller(long energyLeft) {
    receipt.setCallerEnergyLeft(energyLeft);
  }

  public void finalization() throws ContractExeException {
    try {
      pay();
    } catch (BalanceInsufficientException e) {
      throw new ContractExeException(e.getMessage());
    }
    if (StringUtils.isEmpty(transactionContext.getProgramResult().getRuntimeError())) {
      for (DataWord contract : transactionContext.getProgramResult().getDeleteAccounts()) {
        deleteContract(contract.toUnxAddress());
      }
    }
  }

  /**
   * pay actually bill(include ENERGY and storage).
   */
  public void pay() throws BalanceInsufficientException {
    byte[] originAccount;
    byte[] callerAccount;
    long percent = 0;
    long originEnergyLimit = 0;
    switch (unxType) {
      case UNX_CONTRACT_CREATION_TYPE:
        callerAccount = TransactionCapsule.getOwner(unx.getInstance().getRawData().getContract(0));
        originAccount = callerAccount;
        break;
      case UNX_CONTRACT_CALL_TYPE:
        TriggerSmartContract callContract = ContractCapsule
            .getTriggerContractFromTransaction(unx.getInstance());
        ContractCapsule contractCapsule =
            contractStore.get(callContract.getContractAddress().toByteArray());

        callerAccount = callContract.getOwnerAddress().toByteArray();
        originAccount = contractCapsule.getOriginAddress();
        percent = Math
            .max(Constant.ONE_HUNDRED - contractCapsule.getConsumeUserResourcePercent(), 0);
        percent = Math.min(percent, Constant.ONE_HUNDRED);
        originEnergyLimit = contractCapsule.getOriginEnergyLimit();
        break;
      default:
        return;
    }

    // originAccount Percent = 30%
    AccountCapsule origin = accountStore.get(originAccount);
    AccountCapsule caller = accountStore.get(callerAccount);
    receipt.payEnergyBill(
        dynamicPropertiesStore, accountStore, forkController,
        origin,
        caller,
        percent, originEnergyLimit,
        energyProcessor,
        EnergyProcessor.getHeadSlot(dynamicPropertiesStore));
  }

  public boolean checkNeedRetry() {
    if (!needVM()) {
      return false;
    }
    return unx.getContractRet() != contractResult.OUT_OF_TIME && receipt.getResult()
        == contractResult.OUT_OF_TIME;
  }

  public void check() throws ReceiptCheckErrException {
    if (!needVM()) {
      return;
    }
    if (Objects.isNull(unx.getContractRet())) {
      throw new ReceiptCheckErrException("null resultCode");
    }
    if (!unx.getContractRet().equals(receipt.getResult())) {
      logger.info(
          "this tx id: {}, the resultCode in received block: {}, the resultCode in self: {}",
          Hex.toHexString(unx.getTransactionId().getBytes()), unx.getContractRet(),
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
    receipt.setResult(transactionContext.getProgramResult().getResultCode());
  }

  public String getRuntimeError() {
    return transactionContext.getProgramResult().getRuntimeError();
  }

  public ProgramResult getRuntimeResult() {
    return transactionContext.getProgramResult();
  }

  public Runtime getRuntime() {
    return runtime;
  }

  public void deleteContract(byte[] address) {
    abiStore.delete(address);
    codeStore.delete(address);
    accountStore.delete(address);
    contractStore.delete(address);
  }

  public static byte[] convertToUnxAddress(byte[] address) {
    if (address.length == 20) {
      byte[] newAddress = new byte[21];
      byte[] temp = new byte[]{DecodeUtil.addressPreFixByte};
      System.arraycopy(temp, 0, newAddress, 0, temp.length);
      System.arraycopy(address, 0, newAddress, temp.length, address.length);
      address = newAddress;
    }
    return address;
  }

  public enum TimeResultType {
    NORMAL,
    LONG_RUNNING,
    OUT_OF_TIME
  }
}
