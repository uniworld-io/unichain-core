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
import org.unichain.core.capsule.ExchangeCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.ExchangeCreateContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ExchangeCreateActuator extends AbstractActuator {

  public ExchangeCreateActuator(final Any contract, final Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = this.contract.unpack(ExchangeCreateContract.class);
      var accountCapsule = dbManager.getAccountStore().get(ctx.getOwnerAddress().toByteArray());

      var firstTokenID = ctx.getFirstTokenId().toByteArray();
      var secondTokenID = ctx.getSecondTokenId().toByteArray();
      var firstTokenBalance = ctx.getFirstTokenBalance();
      var secondTokenBalance = ctx.getSecondTokenBalance();

      var newBalance = Math.subtractExact(accountCapsule.getBalance(), fee);
      accountCapsule.setBalance(newBalance);

      if (Arrays.equals(firstTokenID, "_".getBytes())) {
        accountCapsule.setBalance(Math.subtractExact(newBalance, firstTokenBalance));
      } else {
        accountCapsule.reduceAssetAmountV2(firstTokenID, firstTokenBalance, dbManager);
      }

      if (Arrays.equals(secondTokenID, "_".getBytes())) {
        accountCapsule.setBalance(Math.subtractExact(newBalance, secondTokenBalance));
      } else {
        accountCapsule.reduceAssetAmountV2(secondTokenID, secondTokenBalance, dbManager);
      }

      var id = Math.incrementExact(dbManager.getDynamicPropertiesStore().getLatestExchangeNum());
      var now = dbManager.getHeadBlockTimeStamp();
      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        //save to old asset store
        var exchangeCapsule =
            new ExchangeCapsule(
                ctx.getOwnerAddress(),
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
                ctx.getOwnerAddress(),
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
      return true;
    } catch (Exception e) {
      logger.error("Actuator exec error: {} --> ", e.getMessage(), e);;
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(ExchangeCreateContract.class), "contract type error,expected type [ExchangeCreateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(ExchangeCreateContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");
      Assert.isTrue(this.dbManager.getAccountStore().has(ownerAddress), "Account[" + readableOwnerAddress + "] not exists");

      var accountCapsule = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.isTrue(accountCapsule.getBalance() >= calcFee(), "Not enough balance for exchange create fee!");

      var firstTokenID = ctx.getFirstTokenId().toByteArray();
      var secondTokenID = ctx.getSecondTokenId().toByteArray();
      var firstTokenBalance = ctx.getFirstTokenBalance();
      var secondTokenBalance = ctx.getSecondTokenBalance();

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 1) {
        var firstTokenInvalid = !Arrays.equals(firstTokenID, "_".getBytes()) && !TransactionUtil.isNumber(firstTokenID);
        Assert.isTrue(!firstTokenInvalid, "First token id is not a valid number");

        var secondTokenInvalid = !Arrays.equals(secondTokenID, "_".getBytes()) && !TransactionUtil.isNumber(secondTokenID);
        Assert.isTrue(!secondTokenInvalid, "Second token id is not a valid number");
      }

      Assert.isTrue(!(Arrays.equals(firstTokenID, secondTokenID)), "Cannot exchange same tokens");
      Assert.isTrue(!(firstTokenBalance <= 0 || secondTokenBalance <= 0), "Token balance must greater than zero");

      var balanceLimit = dbManager.getDynamicPropertiesStore().getExchangeBalanceLimit();
      Assert.isTrue(!(firstTokenBalance > balanceLimit || secondTokenBalance > balanceLimit), "Token balance must less than " + balanceLimit);

      if (Arrays.equals(firstTokenID, "_".getBytes())) {
        Assert.isTrue(accountCapsule.getBalance() >= Math.addExact(firstTokenBalance , calcFee()), "Balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(firstTokenID, firstTokenBalance, dbManager), "First token balance is not enough");
      }

      if (Arrays.equals(secondTokenID, "_".getBytes())) {
        Assert.isTrue(accountCapsule.getBalance() >= Math.addExact(secondTokenBalance, calcFee()), "Balance is not enough");
      } else {
        Assert.isTrue(accountCapsule.assetBalanceEnoughV2(secondTokenID, secondTokenBalance, dbManager), "Second token balance is not enough");
      }

      return true;
    } catch (Exception e) {
      logger.error("Actuator validate error: {} --> ", e.getMessage(), e);;
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
