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
import org.unichain.protos.Contract.ExchangeWithdrawContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ExchangeWithdrawActuator extends AbstractActuator {

  public ExchangeWithdrawActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(ExchangeWithdrawContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      var exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(ctx.getExchangeId()));

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();
      var firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      var secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

      var tokenID = ctx.getTokenId().toByteArray();
      var tokenQty = ctx.getQuant();

      byte[] anotherTokenID;
      long anotherTokenQty;

      var bigFirstTokenBalance = new BigInteger(String.valueOf(firstTokenBalance));
      var bigSecondTokenBalance = new BigInteger(String.valueOf(secondTokenBalance));
      var bigTokenQty = new BigInteger(String.valueOf(tokenQty));
      if (Arrays.equals(tokenID, firstTokenID)) {
        anotherTokenID = secondTokenID;
        anotherTokenQty = bigSecondTokenBalance.multiply(bigTokenQty)
                .divide(bigFirstTokenBalance)
                .longValueExact();
        exchangeCapsule.setBalance(Math.subtractExact(firstTokenBalance, tokenQty), Math.subtractExact(secondTokenBalance, anotherTokenQty));
      } else {
        anotherTokenID = firstTokenID;
        anotherTokenQty = bigFirstTokenBalance.multiply(bigTokenQty).divide(bigSecondTokenBalance).longValueExact();
        exchangeCapsule.setBalance(Math.subtractExact(firstTokenBalance, anotherTokenQty), Math.subtractExact(secondTokenBalance, tokenQty));
      }

      var newBalance = Math.subtractExact(accountCapsule.getBalance(), calcFee());

      if (Arrays.equals(tokenID, "_".getBytes())) {
        accountCapsule.setBalance(Math.addExact(newBalance, tokenQty));
      } else {
        accountCapsule.addAssetAmountV2(tokenID, tokenQty, dbManager);
      }

      if (Arrays.equals(anotherTokenID, "_".getBytes())) {
        accountCapsule.setBalance(Math.addExact(newBalance, anotherTokenQty));
      } else {
        accountCapsule.addAssetAmountV2(anotherTokenID, anotherTokenQty, dbManager);
      }

      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      dbManager.putExchangeCapsule(exchangeCapsule);

      chargeFee(ownerAddress, fee);

      ret.setExchangeWithdrawAnotherAmount(anotherTokenQty);
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
      Assert.isTrue(this.contract.is(ExchangeWithdrawContract.class), "Contract type error,expected type [ExchangeWithdrawContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(ExchangeWithdrawContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "account[" + readableOwnerAddress + "] not exists");

      var accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "No enough balance for exchange withdraw fee!");

      ExchangeCapsule exchangeCapsule;
      try {
        exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(ctx.getExchangeId()));
      } catch (ItemNotFoundException ex) {
        throw new ContractValidateException("Exchange[" + ctx.getExchangeId() + "] not exists");
      }

      Assert.isTrue(accountCapsule.getAddress().equals(exchangeCapsule.getCreatorAddress()), "account[" + readableOwnerAddress + "] is not creator");

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();
      var firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      var secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

      var tokenID = ctx.getTokenId().toByteArray();
      var tokenQty = ctx.getQuant();

      long anotherTokenQty;

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
        var tokenIdInvalid = !Arrays.equals(tokenID, "_".getBytes()) && !TransactionUtil.isNumber(tokenID);
        Assert.isTrue(!tokenIdInvalid, "Token id is not a valid number");
      }

      var tokenNotInExchange = !Arrays.equals(tokenID, firstTokenID) && !Arrays.equals(tokenID, secondTokenID);
      Assert.isTrue(!tokenNotInExchange, "Token is not in exchange");
      Assert.isTrue(tokenQty > 0, "Withdraw token qty must greater than zero");
      Assert.isTrue(!(firstTokenBalance == 0 || secondTokenBalance == 0), "Token balance in exchange is equal with 0, the exchange has been closed");

      var bigFirstTokenBalance = new BigDecimal(String.valueOf(firstTokenBalance));
      var bigSecondTokenBalance = new BigDecimal(String.valueOf(secondTokenBalance));
      var bigTokenQty = new BigDecimal(String.valueOf(tokenQty));
      if (Arrays.equals(tokenID, firstTokenID)) {
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
        anotherTokenQty = bigSecondTokenBalance.multiply(bigTokenQty)
                                                  .divideToIntegralValue(bigFirstTokenBalance)
                                                  .longValueExact();
        var exchangeBalance = firstTokenBalance < tokenQty || secondTokenBalance < anotherTokenQty;
        Assert.isTrue(!exchangeBalance, "Exchange balance is not enough");
        Assert.isTrue(anotherTokenQty > 0, "Withdraw another token quant must greater than zero");

        var remainder = bigSecondTokenBalance.multiply(bigTokenQty)
                                                .divide(bigFirstTokenBalance, 4, BigDecimal.ROUND_HALF_UP)
                                                .doubleValue()
                           - anotherTokenQty;
        Assert.isTrue(remainder / anotherTokenQty <= 0.0001, "Not precise enough");

      } else {
        anotherTokenQty = bigFirstTokenBalance.multiply(bigTokenQty)
                                                  .divideToIntegralValue(bigSecondTokenBalance)
                                                  .longValueExact();
        Assert.isTrue(!(secondTokenBalance < tokenQty || firstTokenBalance < anotherTokenQty), "Exchange balance is not enough");
        Assert.isTrue(anotherTokenQty > 0, "Withdraw another token qty must greater than zero");

        var remainder = bigFirstTokenBalance.multiply(bigTokenQty)
                                                .divide(bigSecondTokenBalance, 4, BigDecimal.ROUND_HALF_UP)
                                                .doubleValue()
                - anotherTokenQty;
        Assert.isTrue(remainder / anotherTokenQty <= 0.0001, "Not precise enough");
      }

      return true;
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }
  }


  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ExchangeWithdrawContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
