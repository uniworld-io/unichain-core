package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.config.Parameter.ChainConstant;

@Slf4j(topic = "DB")
public class StorageMarket {

  private Manager dbManager;
  private long supply = 1_000_000_000_000_000L;

  public StorageMarket(Manager manager) {
    this.dbManager = manager;
  }

  private long exchange_to_supply(boolean isUNW, long quant) {
    long balance = isUNW ? dbManager.getDynamicPropertiesStore().getTotalStoragePool() :
        dbManager.getDynamicPropertiesStore().getTotalStorageReserved();
    long newBalance = Math.addExact(balance, quant);
    double issuedSupply = -supply * (1.0 - Math.pow(1.0 + (double) quant / newBalance, 0.0005));
    long out = (long) issuedSupply;
    supply = Math.addExact(supply, out);
    return out;
  }

  private long exchange_to_supply2(boolean isUNW, long quant) {
    long balance = isUNW ? dbManager.getDynamicPropertiesStore().getTotalStoragePool() :
        dbManager.getDynamicPropertiesStore().getTotalStorageReserved();
    long newBalance = Math.subtractExact(balance, quant);
    double issuedSupply = -supply * (1.0 - Math.pow(1.0 + (double) quant / newBalance, 0.0005));
    long out = (long) issuedSupply;
    supply = Math.addExact(supply, out);
    return out;
  }

  private long exchange_from_supply(boolean isUNW, long supplyQuant) {
    long balance = isUNW ? dbManager.getDynamicPropertiesStore().getTotalStoragePool() :
        dbManager.getDynamicPropertiesStore().getTotalStorageReserved();
    supply =Math.subtractExact(supply, supplyQuant);

    double exchangeBalance = balance * (Math.pow(1.0 + (double) supplyQuant / supply, 2000.0) - 1.0);
    long out = (long) exchangeBalance;
    if (isUNW) {
      out = Math.round(exchangeBalance / 100000) * 100000;
    }
    return out;
  }

  public long exchange(long from, boolean isUNW) {
    long relay = exchange_to_supply(isUNW, from);
    return exchange_from_supply(!isUNW, relay);
  }

  public long calculateTax(long duration, long limit) {
    // todo: Support for change by the committee
    double ratePerYear = dbManager.getDynamicPropertiesStore().getStorageExchangeTaxRate() / 100.0;
    double millisecondPerYear = (double) ChainConstant.MS_PER_YEAR;
    double feeRate = duration / millisecondPerYear * ratePerYear;
    long storageTax = (long) (limit * feeRate);
    logger.info("storageTax: " + storageTax);
    return storageTax;
  }


  public long tryPayTax(long duration, long limit) {
    long storageTax = calculateTax(duration, limit);
    long tax = exchange(storageTax, false);
    logger.info("tax: " + tax);

    long newTotalTax = Math.addExact(dbManager.getDynamicPropertiesStore().getTotalStorageTax(), tax);
    long newTotalPool = Math.subtractExact(dbManager.getDynamicPropertiesStore().getTotalStoragePool(), tax);
    long newTotalReserved = Math.addExact(dbManager.getDynamicPropertiesStore().getTotalStorageReserved(), storageTax);
    logger.info("reserved: " + dbManager.getDynamicPropertiesStore().getTotalStorageReserved());
    boolean eq = dbManager.getDynamicPropertiesStore().getTotalStorageReserved() == 128L * 1024 * 1024 * 1024;
    logger.info("reserved == 128GB: " + eq);
    logger.info("newTotalTax: " + newTotalTax + "  newTotalPool: " + newTotalPool + "  newTotalReserved: " + newTotalReserved);
    return storageTax;
  }

  public long payTax(long duration, long limit) {
    long storageTax = calculateTax(duration, limit);
    long tax = exchange(storageTax, false);
    logger.info("tax: " + tax);

    long newTotalTax = Math.addExact(dbManager.getDynamicPropertiesStore().getTotalStorageTax(), tax);
    long newTotalPool = Math.subtractExact(dbManager.getDynamicPropertiesStore().getTotalStoragePool(), tax);
    long newTotalReserved = Math.addExact(dbManager.getDynamicPropertiesStore().getTotalStorageReserved(), storageTax);
    logger.info("reserved: " + dbManager.getDynamicPropertiesStore().getTotalStorageReserved());
    boolean eq = dbManager.getDynamicPropertiesStore().getTotalStorageReserved() == 128L * 1024 * 1024 * 1024;
    logger.info("reserved == 128GB: " + eq);
    logger.info("newTotalTax: " + newTotalTax + "  newTotalPool: " + newTotalPool + "  newTotalReserved: " + newTotalReserved);
    dbManager.getDynamicPropertiesStore().saveTotalStorageTax(newTotalTax);
    dbManager.getDynamicPropertiesStore().saveTotalStoragePool(newTotalPool);
    dbManager.getDynamicPropertiesStore().saveTotalStorageReserved(newTotalReserved);

    return storageTax;
  }

  public long tryBuyStorageBytes(long storageBought) {
    long relay = exchange_to_supply2(false, storageBought);
    return exchange_from_supply(true, relay);
  }

  public long tryBuyStorage(long quant) {
    return exchange(quant, true);
  }

  public long trySellStorage(long bytes) {
    return exchange(bytes, false);
  }

  public AccountCapsule buyStorageBytes(AccountCapsule accountCapsule, long storageBought) {
    long now = dbManager.getHeadBlockTimeStamp();
    long currentStorageLimit = accountCapsule.getStorageLimit();

    long relay = exchange_to_supply2(false, storageBought);
    long quant = exchange_from_supply(true, relay);

    long newBalance = Math.subtractExact(accountCapsule.getBalance(), quant);

    long newStorageLimit = Math.addExact(currentStorageLimit, storageBought);
    logger.info("storageBought: " + storageBought + "  newStorageLimit: " + newStorageLimit);

    accountCapsule.setLatestExchangeStorageTime(now);
    accountCapsule.setStorageLimit(newStorageLimit);
    accountCapsule.setBalance(newBalance);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    long newTotalPool = Math.addExact(dbManager.getDynamicPropertiesStore().getTotalStoragePool(), quant);
    long newTotalReserved = Math.subtractExact(dbManager.getDynamicPropertiesStore().getTotalStorageReserved(), storageBought);
    logger.info("newTotalPool: " + newTotalPool + "  newTotalReserved: " + newTotalReserved);
    dbManager.getDynamicPropertiesStore().saveTotalStoragePool(newTotalPool);
    dbManager.getDynamicPropertiesStore().saveTotalStorageReserved(newTotalReserved);
    return accountCapsule;
  }


  public void buyStorage(AccountCapsule accountCapsule, long quant) {
    long now = dbManager.getHeadBlockTimeStamp();
    long currentStorageLimit = accountCapsule.getStorageLimit();

    long newBalance = Math.subtractExact(accountCapsule.getBalance(), quant);
    logger.info("newBalance： " + newBalance);

    long storageBought = exchange(quant, true);
    long newStorageLimit = Math.addExact(currentStorageLimit , storageBought);
    logger.info("storageBought: " + storageBought + "  newStorageLimit: " + newStorageLimit);

    accountCapsule.setLatestExchangeStorageTime(now);
    accountCapsule.setStorageLimit(newStorageLimit);
    accountCapsule.setBalance(newBalance);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    long newTotalPool = Math.addExact(dbManager.getDynamicPropertiesStore().getTotalStoragePool(), quant);
    long newTotalReserved = Math.subtractExact(dbManager.getDynamicPropertiesStore().getTotalStorageReserved(), storageBought);
    logger.info("newTotalPool: " + newTotalPool + "  newTotalReserved: " + newTotalReserved);
    dbManager.getDynamicPropertiesStore().saveTotalStoragePool(newTotalPool);
    dbManager.getDynamicPropertiesStore().saveTotalStorageReserved(newTotalReserved);
  }

  public void sellStorage(AccountCapsule accountCapsule, long bytes) {
    long now = dbManager.getHeadBlockTimeStamp();
    long currentStorageLimit = accountCapsule.getStorageLimit();

    long quant = exchange(bytes, false);
    long newBalance = Math.addExact(accountCapsule.getBalance(), quant);

    long newStorageLimit = Math.subtractExact(currentStorageLimit, bytes);

    accountCapsule.setLatestExchangeStorageTime(now);
    accountCapsule.setStorageLimit(newStorageLimit);
    accountCapsule.setBalance(newBalance);
    dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

    long newTotalPool = Math.subtractExact(dbManager.getDynamicPropertiesStore().getTotalStoragePool(), quant);
    long newTotalReserved = Math.addExact(dbManager.getDynamicPropertiesStore().getTotalStorageReserved(), bytes);
    logger.info("newTotalPool: " + newTotalPool + "  newTotalReserved: " + newTotalReserved);
    dbManager.getDynamicPropertiesStore().saveTotalStoragePool(newTotalPool);
    dbManager.getDynamicPropertiesStore().saveTotalStorageReserved(newTotalReserved);
  }

  public long getAccountLeftStorageInByteFromBought(AccountCapsule accountCapsule) {
    return accountCapsule.getStorageLimit() - accountCapsule.getStorageUsage();
  }
}
