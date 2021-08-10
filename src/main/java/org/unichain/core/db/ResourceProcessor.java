package org.unichain.core.db;

import lombok.extern.slf4j.Slf4j;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.config.Parameter.AdaptiveResourceLimitConstants;
import org.unichain.core.config.Parameter.ChainConstant;
import org.unichain.core.exception.AccountResourceInsufficientException;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.TooBigTransactionResultException;

@Slf4j(topic = "DB")
abstract class ResourceProcessor {
  protected Manager dbManager;
  protected long precision;
  protected long windowSize;
  protected long averageWindowSize;

  public ResourceProcessor(Manager manager) {
    this.dbManager = manager;
    this.precision = ChainConstant.PRECISION;
    this.windowSize = ChainConstant.WINDOW_SIZE_MS / ChainConstant.BLOCK_PRODUCED_INTERVAL;
    this.averageWindowSize = AdaptiveResourceLimitConstants.PERIODS_MS / ChainConstant.BLOCK_PRODUCED_INTERVAL;
  }

  abstract void updateUsage(AccountCapsule accountCapsule);

  abstract void consume(TransactionCapsule unx, TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException, TooBigTransactionResultException;

  protected long increase(long lastUsage, long usage, long lastTime, long now) {
    return increase(lastUsage, usage, lastTime, now, windowSize);
  }

  protected long increase(long lastUsage, long usage, long lastTime, long now, long windowSize) {
    long averageLastUsage = divideCeil(lastUsage * precision, windowSize);
    long averageUsage = divideCeil(usage * precision, windowSize);

    if (lastTime != now) {
      assert now > lastTime;
      if (lastTime + windowSize > now) {
        long delta = now - lastTime;
        double decay = (windowSize - delta) / (double) windowSize;
        averageLastUsage = Math.round(averageLastUsage * decay);
      } else {
        averageLastUsage = 0;
      }
    }
    averageLastUsage += averageUsage;
    return getUsage(averageLastUsage, windowSize);
  }

  private long divideCeil(long numerator, long denominator) {
    return (numerator / denominator) + ((numerator % denominator) > 0 ? 1 : 0);
  }

  private long getUsage(long usage, long windowSize) {
    return usage * windowSize / precision;
  }

  protected boolean consumeFee(AccountCapsule accountCapsule, long fee) {
    try {
      long latestOperationTime = dbManager.getHeadBlockTimeStamp();
      accountCapsule.setLatestOperationTime(latestOperationTime);
      dbManager.chargeFee(accountCapsule, fee);
      return true;
    } catch (BalanceInsufficientException e) {
      return false;
    }
  }

  protected boolean consumeFeeTokenPool(byte[] tokenKey, long fee) {
      long latestOperationTime = dbManager.getHeadBlockTimeStamp();
      TokenPoolCapsule tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      tokenPool.setLatestOperationTime(latestOperationTime);

      if(tokenPool.getFeePool() < fee)
      {
        logger.error("not enough token pool fee for token {} available {} require", tokenPool.getName(), tokenPool.getFeePool(), fee);
        return false;
      }

      try {
        dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().getAddress().toByteArray(), fee);
        tokenPool.setFeePool(tokenPool.getFeePool() - fee);
        dbManager.getTokenPoolStore().put(tokenKey, tokenPool);
      }
      catch (Exception e){
        return false;
      }
      return true;
  }
}
