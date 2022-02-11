package org.unichain.core.db;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TokenPoolCapsule;
import org.unichain.core.exception.AccountResourceInsufficientException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol.Transaction.Contract;

/**
 * With block v4:
 * - charge fee on token pool
 * - fee based on new token params: createAccountFee that's default to getCreateAccountFee
 */
@Slf4j(topic = "DB")
public class BandwidthProcessorV4 extends BandwidthProcessorV2 {

  public BandwidthProcessorV4(Manager manager) {
    super(manager);
  }

  @Override
  protected void consumeForCreateNewAccountIfTokenTransfer(AccountCapsule ownerAccountCapsule, Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    try {
      var ctx = contract.getParameter().unpack(org.unichain.protos.Contract.TransferTokenContract.class);
      var tokenPool = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(ctx.getTokenName()));
      long fee = tokenPool.getCreateAccountFee(dbManager.getDynamicPropertiesStore().getCreateAccountFee());

      if (consumeFeeTokenPool(Util.stringAsBytesUppercase(ctx.getTokenName()), fee)) {
        trace.setNetBill(0, fee);
        dbManager.getDynamicPropertiesStore().addTotalCreateAccountCost(fee);
        return;
      }
      else
        throw new AccountResourceInsufficientException();
    }
    catch (InvalidProtocolBufferException e){
      logger.error("bad contract format {}", e.getMessage(), e);
      throw new ContractValidateException("bad contract format:" + e.getMessage());
    }
  }

  @Override
  protected boolean consumeFeeTokenPool(byte[] tokenKey, long fee) {
    long latestOperationTime = dbManager.getHeadBlockTimeStamp();
    TokenPoolCapsule tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
    tokenPool.setLatestOperationTime(latestOperationTime);

    if(tokenPool.getFeePool() < fee)
    {
      logger.error("not enough token pool fee for token {} available {} require {}", tokenPool.getName(), tokenPool.getFeePool(), fee);
      return false;
    }

    try {
      dbManager.burnFee(fee);
      tokenPool.setFeePool(Math.subtractExact(tokenPool.getFeePool(), fee));
      dbManager.getTokenPoolStore().put(tokenKey, tokenPool);
      return true;
    }
    catch (Exception e){
      logger.warn("Exception while charge token pool fee: ", e);
      return false;
    }
  }
}
