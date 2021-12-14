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
import org.unichain.protos.Contract.ExchangeInjectContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;
import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ExchangeInjectActuator extends AbstractActuator {

  ExchangeInjectActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(ExchangeInjectContract.class);
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
      long anotherTokenQuant;

      if (Arrays.equals(tokenID, firstTokenID)) {
        anotherTokenID = secondTokenID;
        anotherTokenQuant = Math.floorDiv(Math.multiplyExact(secondTokenBalance, tokenQty), firstTokenBalance);
        exchangeCapsule.setBalance(firstTokenBalance + tokenQty, secondTokenBalance + anotherTokenQuant);
      } else {
        anotherTokenID = firstTokenID;
        anotherTokenQuant = Math.floorDiv(Math.multiplyExact(firstTokenBalance, tokenQty), secondTokenBalance);
        exchangeCapsule.setBalance(firstTokenBalance + anotherTokenQuant, secondTokenBalance + tokenQty);
      }

      var newBalance = accountCapsule.getBalance() - calcFee();
      accountCapsule.setBalance(newBalance);

      if (Arrays.equals(tokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance - tokenQty);
      } else {
        accountCapsule.reduceAssetAmountV2(tokenID, tokenQty, dbManager);
      }

      if (Arrays.equals(anotherTokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance - anotherTokenQuant);
      } else {
        accountCapsule.reduceAssetAmountV2(anotherTokenID, anotherTokenQuant, dbManager);
      }
      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      dbManager.putExchangeCapsule(exchangeCapsule);

      chargeFee(ownerAddress, fee);
      ret.setExchangeInjectAnotherAmount(anotherTokenQuant);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (ItemNotFoundException | InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.error(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(ExchangeInjectContract.class), "Contract type error,expected type [ExchangeInjectContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(ExchangeInjectContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "Account[" + readableOwnerAddress + "] not exists");

      var accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "No enough balance for exchange inject fee!");

      ExchangeCapsule exchangeCapsule;
      try {
        exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(ctx.getExchangeId()));
      } catch (ItemNotFoundException ex) {
        throw new Exception("Exchange[" + ctx.getExchangeId() + "] not exists");
      }

      Assert.isTrue(accountCapsule.getAddress().equals(exchangeCapsule.getCreatorAddress()), "account[" + readableOwnerAddress + "] is not creator");

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();
      var firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      var secondTokenBalance = exchangeCapsule.getSecondTokenBalance();

      var tokenID = ctx.getTokenId().toByteArray();
      var tokenQty = ctx.getQuant();

      byte[] anotherTokenID;
      long anotherTokenQty;

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
        var tokenValid = !Arrays.equals(tokenID, "_".getBytes()) && !TransactionUtil.isNumber(tokenID);
        Assert.isTrue(!tokenValid, "Token id is not a valid number");
      }

      var tokenExchange = !Arrays.equals(tokenID, firstTokenID) && !Arrays.equals(tokenID, secondTokenID);
      Assert.isTrue(!tokenExchange, "Token id is not in exchange");

      Assert.isTrue(!(firstTokenBalance == 0 || secondTokenBalance == 0), "Token balance in exchange is equal with 0," + "the exchange has been closed");
      Assert.isTrue(tokenQty > 0, "Injected token quantity must greater than zero");

      var bigFirstTokenBalance = new BigInteger(String.valueOf(firstTokenBalance));
      var bigSecondTokenBalance = new BigInteger(String.valueOf(secondTokenBalance));
      var bigTokenQty = new BigInteger(String.valueOf(tokenQty));
      long newTokenBalance, newAnotherTokenBalance;
      if (Arrays.equals(tokenID, firstTokenID)) {
        anotherTokenID = secondTokenID;
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(secondTokenBalance, tokenQuant), firstTokenBalance);
        anotherTokenQty = bigSecondTokenBalance.multiply(bigTokenQty)
            .divide(bigFirstTokenBalance).longValueExact();
        newTokenBalance = firstTokenBalance + tokenQty;
        newAnotherTokenBalance = secondTokenBalance + anotherTokenQty;
      } else {
        anotherTokenID = firstTokenID;
//      anotherTokenQuant = Math
//          .floorDiv(Math.multiplyExact(firstTokenBalance, tokenQuant), secondTokenBalance);
        anotherTokenQty = bigFirstTokenBalance.multiply(bigTokenQty).divide(bigSecondTokenBalance).longValueExact();
        newTokenBalance = secondTokenBalance + tokenQty;
        newAnotherTokenBalance = firstTokenBalance + anotherTokenQty;
      }

      Assert.isTrue(anotherTokenQty > 0, "the calculated token quant  must be greater than 0");

      var balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
      Assert.isTrue(!(newTokenBalance > balanceLimit || newAnotherTokenBalance > balanceLimit), "token balance must less than " + balanceLimit);

      if (Arrays.equals(tokenID, "_".getBytes())) {
        Assert.isTrue(accountCapsule.getBalance() >= (tokenQty + calcFee()), "balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(tokenID, tokenQty, dbManager), "token balance is not enough");
      }

      if (Arrays.equals(anotherTokenID, "_".getBytes())) {
        Assert.isTrue(accountCapsule.getBalance() >= (anotherTokenQty + calcFee()), "balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(anotherTokenID, anotherTokenQty, dbManager), "another token balance is not enough");
      }

      return true;
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ExchangeInjectContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
