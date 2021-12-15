package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.TransferTokenOwnerContract;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class TokenTransferOwnerActuator extends AbstractActuator {

  TokenTransferOwnerActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      var ctx = contract.unpack(TransferTokenOwnerContract.class);
      var accountStore = dbManager.getAccountStore();
      var tokenStore = dbManager.getTokenPoolStore();
      byte[] ownerAddress = ctx.getOwnerAddress().toByteArray();
      byte[] toAddress = ctx.getToAddress().toByteArray();
      byte[] tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = tokenStore.get(tokenKey);

      AccountCapsule toAccount = accountStore.get(toAddress);
      if (Objects.isNull(toAccount)) {
        boolean withDefaultPermission = (dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1);
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        fee += dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }

      //update new owner
      tokenPool.setOwnerAddress(ctx.getToAddress());
      tokenStore.put(tokenKey, tokenPool);

      //transfer available token to new owner
      var ownerAccount = accountStore.get(ownerAddress);
      var tokenAvail = ownerAccount.burnAllAvailableToken(tokenKey);
      toAccount.addToken(tokenKey, tokenAvail);
      accountStore.put(toAddress, toAccount);
      accountStore.put(ownerAddress, ownerAccount);

      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (Exception e) {
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
      Assert.isTrue(contract.is(TransferTokenOwnerContract.class), "contract type error, expected type [TransferTokenOwnerContract], real type[" + contract.getClass() + "]");

      var fee = calcFee();
      var ctx = contract.unpack(TransferTokenOwnerContract.class);

      byte[] ownerAddress = ctx.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      var ownerAccount = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "Owner account not exists");

      byte[] toAddress = ctx.getToAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");
      Assert.isTrue(!Arrays.equals(ownerAddress, toAddress), "Transfer owner to itself not allowed");

      var tokenKey = Util.stringAsBytesUppercase(ctx.getTokenName());
      var tokenPool = dbManager.getTokenPoolStore().get(tokenKey);
      Assert.notNull(tokenPool, "Token pool not found: " + ctx.getTokenName());
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < tokenPool.getEndTime(), "Token expired at: " + Utils.formatDateLong(tokenPool.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= tokenPool.getStartTime(), "Token pending to start at: " + Utils.formatDateLong(tokenPool.getStartTime()));
      Assert.isTrue(Arrays.equals(ownerAddress, tokenPool.getOwnerAddress().toByteArray()), "Mismatched token owner not allowed");

      var toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        fee += dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }


      //after UvmSolidity059 proposal, send token/reassign token owner to smartContract by actuator is not allowed.
      var transferToSmartContract  = (dbManager.getDynamicPropertiesStore().getAllowTvmSolidity059() == 1)
              && toAccount != null
              && toAccount.getType() == AccountType.Contract;
      Assert.isTrue(!transferToSmartContract, "Cannot transfer token owner permission to smartContract");

      Assert.isTrue(ownerAccount.getBalance() >=  fee, "Balance is not sufficient");

      return true;
    }
    catch (IllegalArgumentException | InvalidProtocolBufferException | ArithmeticException e){
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(TransferTokenOwnerContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 unw default
  }
}