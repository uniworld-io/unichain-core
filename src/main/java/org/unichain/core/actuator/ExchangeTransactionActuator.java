package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.ExchangeCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.ItemNotFoundException;
import org.unichain.protos.Contract.ExchangeTransactionContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ExchangeTransactionActuator extends AbstractActuator {

  public ExchangeTransactionActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(ExchangeTransactionContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      var exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(ctx.getExchangeId()));

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();

      var tokenID = ctx.getTokenId().toByteArray();
      var tokenQty = ctx.getQuant();

      byte[] anotherTokenID;
      var anotherTokenQty = exchangeCapsule.transaction(tokenID, tokenQty);

      if (Arrays.equals(tokenID, firstTokenID)) {
        anotherTokenID = secondTokenID;
      } else {
        anotherTokenID = firstTokenID;
      }

      var newBalance = Math.subtractExact(accountCapsule.getBalance(), calcFee());
      accountCapsule.setBalance(newBalance);

      if (Arrays.equals(tokenID, "_".getBytes())) {
        accountCapsule.setBalance(Math.subtractExact(newBalance, tokenQty));
      } else {
        accountCapsule.reduceAssetAmountV2(tokenID, tokenQty, dbManager);
      }

      if (Arrays.equals(anotherTokenID, "_".getBytes())) {
        accountCapsule.setBalance(Math.addExact(newBalance, anotherTokenQty));
      } else {
        accountCapsule.addAssetAmountV2(anotherTokenID, anotherTokenQty, dbManager);
      }
      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      dbManager.putExchangeCapsule(exchangeCapsule);
      chargeFee(ownerAddress, fee);
      ret.setExchangeReceivedAmount(anotherTokenQty);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (ItemNotFoundException | InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }


  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(ExchangeTransactionContract.class), "Contract type error,expected type [ExchangeTransactionContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(ExchangeTransactionContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "Account[" + readableOwnerAddress + "] not exists");

      var accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "No enough balance for exchange transaction fee!");

      ExchangeCapsule exchangeCapsule;
      try {
        exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(ctx.getExchangeId()));
      } catch (ItemNotFoundException ex) {
        throw new ContractValidateException("Exchange[" + ctx.getExchangeId() + "] not exists");
      }

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();
      var firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      var secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

      var tokenID = ctx.getTokenId().toByteArray();
      var tokenQty = ctx.getQuant();
      var tokenExpected = ctx.getExpected();

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
        var tokenIdInvalid = !Arrays.equals(tokenID, "_".getBytes()) && !TransactionUtil.isNumber(tokenID);
        Assert.isTrue(!tokenIdInvalid, "Token id is not a valid number");
      }

      var tokenExchange = !Arrays.equals(tokenID, firstTokenID) && !Arrays.equals(tokenID, secondTokenID);
      Assert.isTrue(!tokenExchange, "Token is not in exchange");
      Assert.isTrue(tokenQty > 0, "Token qty must greater than zero");
      Assert.isTrue(tokenExpected > 0, "Token expected must greater than zero");
      Assert.isTrue(!(firstTokenBalance == 0 || secondTokenBalance == 0), "Token balance in exchange is equal with 0, the exchange has been closed");

      var balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
      var tokenBalance = (Arrays.equals(tokenID, firstTokenID) ? firstTokenBalance : secondTokenBalance);
      tokenBalance = Math.addExact(tokenBalance, tokenQty);
      Assert.isTrue(tokenBalance <= balanceLimit, "Token balance must less than " + balanceLimit);

      if (Arrays.equals(tokenID, "_".getBytes())) {
        Assert.isTrue(accountCapsule.getBalance() >= Math.addExact(tokenQty, calcFee()), "Balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(tokenID, tokenQty, dbManager), "Token balance is not enough");
      }

      var anotherTokenQty = exchangeCapsule.transaction(tokenID, tokenQty);
      Assert.isTrue(anotherTokenQty >= tokenExpected, "Token required must greater than expected");
      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }


  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ExchangeTransactionContract.class).getOwnerAddress();
  }

  @Override
    public long calcFee() {
      return 0;
    }
}
