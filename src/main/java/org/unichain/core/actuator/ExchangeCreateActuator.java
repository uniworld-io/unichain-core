package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
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
import org.unichain.protos.Contract.ExchangeCreateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ExchangeCreateActuator extends AbstractActuator {

  ExchangeCreateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      val exchangeCreateContract = this.contract.unpack(ExchangeCreateContract.class);
      var accountCapsule = dbManager.getAccountStore().get(exchangeCreateContract.getOwnerAddress().toByteArray());

      byte[] firstTokenID = exchangeCreateContract.getFirstTokenId().toByteArray();
      byte[] secondTokenID = exchangeCreateContract.getSecondTokenId().toByteArray();
      long firstTokenBalance = exchangeCreateContract.getFirstTokenBalance();
      long secondTokenBalance = exchangeCreateContract.getSecondTokenBalance();

      long newBalance = accountCapsule.getBalance() - fee;
      accountCapsule.setBalance(newBalance);

      if (Arrays.equals(firstTokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance - firstTokenBalance);
      } else {
        accountCapsule.reduceAssetAmountV2(firstTokenID, firstTokenBalance, dbManager);
      }

      if (Arrays.equals(secondTokenID, "_".getBytes())) {
        accountCapsule.setBalance(newBalance - secondTokenBalance);
      } else {
        accountCapsule.reduceAssetAmountV2(secondTokenID, secondTokenBalance, dbManager);
      }

      long id = dbManager.getDynamicPropertiesStore().getLatestExchangeNum() + 1;
      long now = dbManager.getHeadBlockTimeStamp();
      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        //save to old asset store
        var exchangeCapsule =
            new ExchangeCapsule(
                exchangeCreateContract.getOwnerAddress(),
                id,
                now,
                firstTokenID,
                secondTokenID
            );
        exchangeCapsule.setBalance(firstTokenBalance, secondTokenBalance);
        dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);

        //save to new asset store
        if (!Arrays.equals(firstTokenID, "_".getBytes())) {
          var firstTokenRealID = dbManager.getAssetIssueStore().get(firstTokenID).getId();
          firstTokenID = firstTokenRealID.getBytes();
        }
        if (!Arrays.equals(secondTokenID, "_".getBytes())) {
          var secondTokenRealID = dbManager.getAssetIssueStore().get(secondTokenID).getId();
          secondTokenID = secondTokenRealID.getBytes();
        }
      }

      {
        // only save to new asset store
        var exchangeCapsuleV2 =
            new ExchangeCapsule(
                exchangeCreateContract.getOwnerAddress(),
                id,
                now,
                firstTokenID,
                secondTokenID
            );
        exchangeCapsuleV2.setBalance(firstTokenBalance, secondTokenBalance);
        dbManager.getExchangeV2Store().put(exchangeCapsuleV2.createDbKey(), exchangeCapsuleV2);
      }

      dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);
      dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().createDbKey(), fee);
      dbManager.getDynamicPropertiesStore().saveLatestExchangeNum(id);

      ret.setExchangeId(id);
      ret.setStatus(fee, code.SUCESS);
    } catch (BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(ExchangeCreateContract.class), "contract type error,expected type [ExchangeCreateContract],real type[" + contract.getClass() + "]");

      val contract = this.contract.unpack(ExchangeCreateContract.class);


      var ownerAddress = contract.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "account[" + readableOwnerAddress + "] not exists");

      AccountCapsule accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "No enough balance for exchange create fee!");

      var firstTokenID = contract.getFirstTokenId().toByteArray();
      var secondTokenID = contract.getSecondTokenId().toByteArray();
      long firstTokenBalance = contract.getFirstTokenBalance();
      long secondTokenBalance = contract.getSecondTokenBalance();

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
        boolean firstToken = !Arrays.equals(firstTokenID, "_".getBytes()) && !TransactionUtil.isNumber(firstTokenID);
        Assert.isTrue(!firstToken, "first token id is not a valid number");

        boolean secondToken = !Arrays.equals(secondTokenID, "_".getBytes()) && !TransactionUtil.isNumber(secondTokenID);
        Assert.isTrue(!secondToken, "second token id is not a valid number");
      }

      Assert.isTrue(!(Arrays.equals(firstTokenID, secondTokenID)), "cannot exchange same tokens");
      Assert.isTrue(!(firstTokenBalance <= 0 || secondTokenBalance <= 0), "token balance must greater than zero");

      long balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
      Assert.isTrue(!(firstTokenBalance > balanceLimit || secondTokenBalance > balanceLimit), "token balance must less than " + balanceLimit);

      if (Arrays.equals(firstTokenID, "_".getBytes())) {
        Assert.isTrue(!(accountCapsule.getBalance() < (firstTokenBalance + calcFee())), "balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(firstTokenID, firstTokenBalance, dbManager), "first token balance is not enough");
      }

      if (Arrays.equals(secondTokenID, "_".getBytes())) {
        Assert.isTrue(!(accountCapsule.getBalance() < (secondTokenBalance + calcFee())), "balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(secondTokenID, secondTokenBalance, dbManager), "second token balance is not enough");
      }

      return true;
    } catch (InvalidProtocolBufferException e) {
      throw new ContractValidateException(e.getMessage());
    }
  }


  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(ExchangeCreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getExchangeCreateFee();
  }
}
