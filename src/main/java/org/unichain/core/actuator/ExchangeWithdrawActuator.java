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
import org.unichain.core.capsule.AccountCapsule;
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

  ExchangeWithdrawActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      val exchangeWithdrawContract = this.contract.unpack(ExchangeWithdrawContract.class);
      var ownerAddress = exchangeWithdrawContract.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      var exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(exchangeWithdrawContract.getExchangeId()));

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();
      long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

      var tokenID = exchangeWithdrawContract.getTokenId().toByteArray();
      long tokenQuant = exchangeWithdrawContract.getQuant();

      byte[] anotherTokenID;
      long anotherTokenQuant;

      var bigFirstTokenBalance = new BigInteger(String.valueOf(firstTokenBalance));
      var bigSecondTokenBalance = new BigInteger(String.valueOf(secondTokenBalance));
      var bigTokenQuant = new BigInteger(String.valueOf(tokenQuant));
      if (Arrays.equals(tokenID, firstTokenID)) {
        anotherTokenID = secondTokenID;
//      anotherTokenQuant = Math
//            .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
        anotherTokenQuant = bigSecondTokenBalance.multiply(bigTokenQuant)
                .divide(bigFirstTokenBalance)
                .longValueExact();
        exchangeCapsule.setBalance(firstTokenBalance - tokenQuant, secondTokenBalance - anotherTokenQuant);
      } else {
        anotherTokenID = firstTokenID;
//        anotherTokenQuant = Math
//            .floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
        anotherTokenQuant = bigFirstTokenBalance.multiply(bigTokenQuant).divide(bigSecondTokenBalance).longValueExact();
        exchangeCapsule.setBalance(firstTokenBalance - anotherTokenQuant, secondTokenBalance - tokenQuant);
      }

      long newBalance = accountCapsule.getBalance() - calcFee();

      if (Arrays.equals(tokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance + tokenQuant);
      } else {
        accountCapsule.addAssetAmountV2(tokenID, tokenQuant, dbManager);
      }

      if (Arrays.equals(anotherTokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance + anotherTokenQuant);
      } else {
        accountCapsule.addAssetAmountV2(anotherTokenID, anotherTokenQuant, dbManager);
      }


      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      dbManager.putExchangeCapsule(exchangeCapsule);

      chargeFee(ownerAddress, fee);

      ret.setExchangeWithdrawAnotherAmount(anotherTokenQuant);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (ItemNotFoundException
        | InvalidProtocolBufferException
        | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }


  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(ExchangeWithdrawContract.class), "contract type error,expected type [ExchangeWithdrawContract],real type[" + contract.getClass() + "]");

      val contract = this.contract.unpack(ExchangeWithdrawContract.class);
      var ownerAddress = contract.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "account[" + readableOwnerAddress + "] not exists");

      var accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "No enough balance for exchange withdraw fee!");

      ExchangeCapsule exchangeCapsule;
      try {
        exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(contract.getExchangeId()));
      } catch (ItemNotFoundException ex) {
        throw new ContractValidateException("Exchange[" + contract.getExchangeId() + "] not exists");
      }

      Assert.isTrue(accountCapsule.getAddress().equals(exchangeCapsule.getCreatorAddress()), "account[" + readableOwnerAddress + "] is not creator");

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();
      long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();
      var tokenID = contract.getTokenId().toByteArray();
      long tokenQuant = contract.getQuant();
      long anotherTokenQuant;

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
        boolean tokenVaild = !Arrays.equals(tokenID, "_".getBytes()) && !TransactionUtil.isNumber(tokenID);
        Assert.isTrue(!tokenVaild, "token id is not a valid number");
      }

      boolean tokenExchange = !Arrays.equals(tokenID, firstTokenID) && !Arrays.equals(tokenID, secondTokenID);
      Assert.isTrue(!tokenExchange, "token is not in exchange");
      Assert.isTrue(tokenQuant > 0, "withdraw token quant must greater than zero");

      boolean token = firstTokenBalance == 0 || secondTokenBalance == 0;
      Assert.isTrue(!token, "Token balance in exchange is equal with 0, the exchange has been closed");

      var bigFirstTokenBalance = new BigDecimal(String.valueOf(firstTokenBalance));
      var bigSecondTokenBalance = new BigDecimal(String.valueOf(secondTokenBalance));
      var bigTokenQuant = new BigDecimal(String.valueOf(tokenQuant));
      if (Arrays.equals(tokenID, firstTokenID)) {
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
        anotherTokenQuant = bigSecondTokenBalance.multiply(bigTokenQuant)
                                                  .divideToIntegralValue(bigFirstTokenBalance)
                                                  .longValueExact();
        boolean exchangeBalance = firstTokenBalance < tokenQuant || secondTokenBalance < anotherTokenQuant;
        Assert.isTrue(!exchangeBalance, "exchange balance is not enough");
        Assert.isTrue(anotherTokenQuant > 0, "withdraw another token quant must greater than zero");

        double remainder = bigSecondTokenBalance.multiply(bigTokenQuant)
                                                .divide(bigFirstTokenBalance, 4, BigDecimal.ROUND_HALF_UP)
                                                .doubleValue()
                           - anotherTokenQuant;
        Assert.isTrue(!(remainder / anotherTokenQuant > 0.0001), "Not precise enough");
      } else {
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
        anotherTokenQuant = bigFirstTokenBalance.multiply(bigTokenQuant)
                                                  .divideToIntegralValue(bigSecondTokenBalance)
                                                  .longValueExact();
        Assert.isTrue(!(secondTokenBalance < tokenQuant || firstTokenBalance < anotherTokenQuant), "exchange balance is not enough");
        Assert.isTrue(anotherTokenQuant > 0, "withdraw another token quant must greater than zero");

        double remainder = bigFirstTokenBalance.multiply(bigTokenQuant)
                                                .divide(bigSecondTokenBalance, 4, BigDecimal.ROUND_HALF_UP)
                                                .doubleValue()
                - anotherTokenQuant;
        Assert.isTrue(!(remainder / anotherTokenQuant > 0.0001), "Not precise enough");
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
