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
import org.unichain.protos.Contract.ExchangeTransactionContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ExchangeTransactionActuator extends AbstractActuator {

  ExchangeTransactionActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      val exchangeTransactionContract = this.contract.unpack(ExchangeTransactionContract.class);
      var ownerAddress = exchangeTransactionContract.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      var exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(exchangeTransactionContract.getExchangeId()));

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();

      var tokenID = exchangeTransactionContract.getTokenId().toByteArray();
      long tokenQuant = exchangeTransactionContract.getQuant();

      byte[] anotherTokenID;
      long anotherTokenQuant = exchangeCapsule.transaction(tokenID, tokenQuant);

      if (Arrays.equals(tokenID, firstTokenID)) {
        anotherTokenID = secondTokenID;
      } else {
        anotherTokenID = firstTokenID;
      }

      long newBalance = accountCapsule.getBalance() - calcFee();
      accountCapsule.setBalance(newBalance);

      if (Arrays.equals(tokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance - tokenQuant);
      } else {
        accountCapsule.reduceAssetAmountV2(tokenID, tokenQuant, dbManager);
      }

      if (Arrays.equals(anotherTokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance + anotherTokenQuant);
      } else {
        accountCapsule.addAssetAmountV2(anotherTokenID, anotherTokenQuant, dbManager);
      }
      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      dbManager.putExchangeCapsule(exchangeCapsule);
      chargeFee(ownerAddress, fee);
      ret.setExchangeReceivedAmount(anotherTokenQuant);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (ItemNotFoundException | InvalidProtocolBufferException | BalanceInsufficientException e) {
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
      Assert.isTrue(this.contract.is(ExchangeTransactionContract.class), "contract type error,expected type [ExchangeTransactionContract],real type[" + contract.getClass() + "]");

      val contract = this.contract.unpack(ExchangeTransactionContract.class);
      var ownerAddress = contract.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "account[" + readableOwnerAddress + "] not exists");

      var accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "No enough balance for exchange transaction fee!");

      ExchangeCapsule exchangeCapsule;
      try {
        exchangeCapsule = dbManager.getExchangeStoreFinal().get(ByteArray.fromLong(contract.getExchangeId()));
      } catch (ItemNotFoundException ex) {
        throw new ContractValidateException("Exchange[" + contract.getExchangeId() + "] not exists");
      }

      var firstTokenID = exchangeCapsule.getFirstTokenId();
      var secondTokenID = exchangeCapsule.getSecondTokenId();
      long firstTokenBalance = exchangeCapsule.getFirstTokenBalance();
      long secondTokenBalance = exchangeCapsule.getSecondTokenBalance();
      var tokenID = contract.getTokenId().toByteArray();
      long tokenQuant = contract.getQuant();
      long tokenExpected = contract.getExpected();

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
        boolean tokenVaild = !Arrays.equals(tokenID, "_".getBytes()) && !TransactionUtil.isNumber(tokenID);
        Assert.isTrue(!tokenVaild, "token id is not a valid number");
      }

      boolean tokenExchange = !Arrays.equals(tokenID, firstTokenID) && !Arrays.equals(tokenID, secondTokenID);
      Assert.isTrue(!tokenExchange, "token is not in exchange");
      Assert.isTrue(tokenQuant > 0, "token quant must greater than zero");
      Assert.isTrue(tokenExpected > 0, "token expected must greater than zero");

      boolean token = firstTokenBalance == 0 || secondTokenBalance == 0;
      Assert.isTrue(!token, "Token balance in exchange is equal with 0, the exchange has been closed");

      long balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
      long tokenBalance = (Arrays.equals(tokenID, firstTokenID) ? firstTokenBalance : secondTokenBalance);
      tokenBalance += tokenQuant;
      Assert.isTrue(tokenBalance <= balanceLimit, "token balance must less than " + balanceLimit);

      if (Arrays.equals(tokenID, "_".getBytes())) {
        Assert.isTrue(accountCapsule.getBalance() >= (tokenQuant + calcFee()), "balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(tokenID, tokenQuant, dbManager), "token balance is not enough");
      }

      long anotherTokenQuant = exchangeCapsule.transaction(tokenID, tokenQuant);
      Assert.isTrue(anotherTokenQuant >= tokenExpected, "token required must greater than expected");

      return true;
    } catch (InvalidProtocolBufferException e) {
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
