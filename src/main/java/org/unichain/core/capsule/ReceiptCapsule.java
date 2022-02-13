package org.unichain.core.capsule;

import lombok.Getter;
import lombok.Setter;
import org.unichain.common.runtime.config.VMConfig;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.db.EnergyProcessor;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.protos.Protocol.ResourceReceipt;
import org.unichain.protos.Protocol.Transaction.Result.contractResult;

import java.util.Objects;

public class ReceiptCapsule {
  private ResourceReceipt receipt;
  @Getter
  @Setter
  private long multiSignFee;

  private Sha256Hash receiptAddress;

  public ReceiptCapsule(ResourceReceipt data, Sha256Hash receiptAddress) {
    this.receipt = data;
    this.receiptAddress = receiptAddress;
  }

  public ReceiptCapsule(Sha256Hash receiptAddress) {
    this.receipt = ResourceReceipt.newBuilder().build();
    this.receiptAddress = receiptAddress;
  }

  public void setReceipt(ResourceReceipt receipt) {
    this.receipt = receipt;
  }

  public ResourceReceipt getReceipt() {
    return this.receipt;
  }

  public Sha256Hash getReceiptAddress() {
    return this.receiptAddress;
  }

  public void setNetUsage(long netUsage) {
    this.receipt = this.receipt.toBuilder().setNetUsage(netUsage).build();
  }

  public void setNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(netFee).build();
  }

  public void addNetFee(long netFee) {
    this.receipt = this.receipt.toBuilder().setNetFee(getNetFee() + netFee).build();
  }

  public long getEnergyUsage() {
    return this.receipt.getEnergyUsage();
  }

  public long getEnergyFee() {
    return this.receipt.getEnergyFee();
  }

  public void setEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEnergyUsage(energyUsage).build();
  }

  public void setEnergyFee(long energyFee) {
    this.receipt = this.receipt.toBuilder().setEnergyFee(energyFee).build();
  }

  public long getOriginEnergyUsage() {
    return this.receipt.getOriginEnergyUsage();
  }

  public long getEnergyUsageTotal() {
    return this.receipt.getEnergyUsageTotal();
  }

  public void setOriginEnergyUsage(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setOriginEnergyUsage(energyUsage).build();
  }

  public void setEnergyUsageTotal(long energyUsage) {
    this.receipt = this.receipt.toBuilder().setEnergyUsageTotal(energyUsage).build();
  }

  public long getNetUsage() {
    return this.receipt.getNetUsage();
  }

  public long getNetFee() {
    return this.receipt.getNetFee();
  }

  /**
   * Pay energy bill block version 2: directly charge from account's balance
   */
  public void payEnergyBillV2(Manager manager, AccountCapsule origin, AccountCapsule caller, long sharedPercent) throws BalanceInsufficientException {
    if (receipt.getEnergyUsageTotal() <= 0) {
      return;
    }

    if (Objects.isNull(origin) && VMConfig.allowTvmConstantinople()) {
      payEnergyBillV2(manager, caller, receipt.getEnergyUsageTotal());
      return;
    }

    if (caller.getAddress().equals(origin.getAddress())) {
      payEnergyBillV2(manager, caller, receipt.getEnergyUsageTotal());
    } else {
      long originUsage = Math.multiplyExact(receipt.getEnergyUsageTotal(), sharedPercent) / 100;
      originUsage = chargeOriginUsage(manager, origin, originUsage);
      this.setOriginEnergyUsage(originUsage);
      long callerUsage = Math.subtractExact(receipt.getEnergyUsageTotal(), originUsage);
      payEnergyBillV2(manager, caller, callerUsage);
    }
  }

  private void payEnergyBillV2(Manager manager, AccountCapsule account, long usage) throws BalanceInsufficientException {
    long blockEnergyUsage = Math.addExact(manager.getDynamicPropertiesStore().getBlockEnergyUsage(), usage);
    manager.getDynamicPropertiesStore().saveBlockEnergyUsage(blockEnergyUsage);

    long ginzaPerEnergy = manager.loadEnergyGinzaFactor();
    long energyFee = Math.multiplyExact(usage, ginzaPerEnergy);
    this.setEnergyUsage(0);
    this.setEnergyFee(energyFee);
    long balance = account.getBalance();
    if (balance < energyFee) {
      throw new BalanceInsufficientException(StringUtil.createReadableString(account.createDbKey()) + " insufficient balance");
    }
    account.setBalance(Math.subtractExact(balance, energyFee));
    account.setLatestOperationTime(manager.getHeadBlockTimeStamp());
    manager.getAccountStore().put(account.getAddress().toByteArray(), account);
    manager.adjustBalance(manager.getAccountStore().getBurnaccount().getAddress().toByteArray(), energyFee);
  }

  /**
   * pay energy bill block version 1
   */
  public void payEnergyBill(Manager manager, AccountCapsule origin, AccountCapsule caller, long percent, long originEnergyLimit, EnergyProcessor energyProcessor, long now) throws BalanceInsufficientException {
    if (receipt.getEnergyUsageTotal() <= 0) {
      return;
    }

    if (Objects.isNull(origin) && VMConfig.allowTvmConstantinople()) {
      payEnergyBill(manager, caller, receipt.getEnergyUsageTotal(), energyProcessor, now);
      return;
    }

    if (caller.getAddress().equals(origin.getAddress())) {
      payEnergyBill(manager, caller, receipt.getEnergyUsageTotal(), energyProcessor, now);
    } else {
      long originUsage = Math.multiplyExact(receipt.getEnergyUsageTotal(), percent) / 100;
      originUsage = getOriginUsage(manager, origin, originEnergyLimit, energyProcessor, originUsage);

      long callerUsage = receipt.getEnergyUsageTotal() - originUsage;
      energyProcessor.useEnergy(origin, originUsage, now);
      this.setOriginEnergyUsage(originUsage);
      payEnergyBill(manager, caller, callerUsage, energyProcessor, now);
    }
  }

  private void payEnergyBill(Manager manager, AccountCapsule account, long usage, EnergyProcessor energyProcessor, long now) throws BalanceInsufficientException {
    long accountEnergyLeft = energyProcessor.getAccountLeftEnergyFromFreeze(account);
    if (accountEnergyLeft >= usage) {
      energyProcessor.useEnergy(account, usage, now);
      this.setEnergyUsage(usage);
    } else {
      energyProcessor.useEnergy(account, accountEnergyLeft, now);

      if(manager.getDynamicPropertiesStore().getAllowAdaptiveEnergy() == 1) {
          long blockEnergyUsage = Math.addExact(manager.getDynamicPropertiesStore().getBlockEnergyUsage(), Math.subtractExact(usage, accountEnergyLeft));
          manager.getDynamicPropertiesStore().saveBlockEnergyUsage(blockEnergyUsage);
      }

      long ginzaPerEnergy = manager.loadEnergyGinzaFactor();
      long energyFee = Math.multiplyExact(Math.subtractExact(usage, accountEnergyLeft),  ginzaPerEnergy);
      this.setEnergyUsage(accountEnergyLeft);
      this.setEnergyFee(energyFee);
      long balance = account.getBalance();
      if (balance < energyFee) {
        throw new BalanceInsufficientException(StringUtil.createReadableString(account.createDbKey()) + " insufficient balance");
      }
      account.setBalance(Math.subtractExact(balance, energyFee));
      manager.burnFee(energyFee);
    }

    manager.getAccountStore().put(account.getAddress().toByteArray(), account);
  }

  public static ResourceReceipt copyReceipt(ReceiptCapsule origin) {
    return origin.getReceipt().toBuilder().build();
  }

  public void setResult(contractResult success) {
    this.receipt = receipt.toBuilder().setResult(success).build();
  }

  public contractResult getResult() {
    return this.receipt.getResult();
  }

  private long getOriginUsage(Manager manager, AccountCapsule origin, long originEnergyLimit, EnergyProcessor energyProcessor, long originUsage) {
    return Math.min(originUsage, energyProcessor.getAccountLeftEnergyFromFreeze(origin));
  }

  /**
   *  ChargeOriginUsage
   *     - charge origin fee at most
   *     - return actually usage
   */
  private long chargeOriginUsage(Manager manager, AccountCapsule origin, long usage) {
    long ginzaEnergyFactor = manager.loadEnergyGinzaFactor();
    long maxFee = Math.multiplyExact(usage, ginzaEnergyFactor);
    long balance = origin.getBalance();
    if(balance >= maxFee){
      origin.setBalance(Math.subtractExact(balance, maxFee));
      manager.getAccountStore().put(origin.getAddress().toByteArray(), origin);
      return usage;
    }
    else {
      origin.setBalance(0L);
      manager.getAccountStore().put(origin.getAddress().toByteArray(), origin);
      return Math.floorDiv(balance, ginzaEnergyFactor);
    }
  }
}
