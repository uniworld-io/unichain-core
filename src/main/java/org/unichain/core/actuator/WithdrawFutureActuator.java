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
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.FutureWithdrawContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;

@Slf4j(topic = "actuator")
public class WithdrawFutureActuator extends AbstractActuator {

  WithdrawFutureActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
      var fee = calcFee();
      try {
          val ctx = contract.unpack(FutureWithdrawContract.class);
          var ownerAddress = ctx.getOwnerAddress().toByteArray();
//          var ownerAccount = dbManager.getAccountStore().get(ownerAddress);
          withdraw(ownerAddress, dbManager.getHeadBlockTimeStamp());
          ret.setStatus(fee, code.SUCESS);
          return true;
      }
      catch (Exception e){
          logger.error("exec withdraw future error -->", e);
          ret.setStatus(fee, code.FAILED);
          throw new ContractExeException(e.getMessage());
      }
  }

  @Override
  public boolean validate() throws ContractValidateException {
      try {
          Assert.isTrue(contract != null, "No contract!");
          Assert.isTrue(dbManager != null, "No dbManager!");
          Assert.isTrue(contract.is(Contract.FutureWithdrawContract.class), "contract type error,expected type [FutureWithdrawContract],real type[" + contract.getClass() + "]");

          val ctx = this.contract.unpack(FutureWithdrawContract.class);
          var ownerAddress = ctx.getOwnerAddress().toByteArray();
          Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");

          var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
          var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
          Assert.isTrue(accountCapsule != null, ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] not exists");

          var isGP = Args.getInstance()
                  .getGenesisBlock()
                  .getWitnesses()
                  .stream()
                  .anyMatch(witness -> Arrays.equals(ownerAddress, witness.getAddress()));
          Assert.isTrue(!isGP, ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] is a guard representative and is not allowed to withdraw Balance");
          Assert.isTrue(isFutureWithdrawable(ownerAddress, dbManager.getHeadBlockTimeStamp()), "Account does not have any future balance");
          return true;
      }
      catch (Exception e){
          logger.error("validate withdraw future got error", e);
          throw new ContractValidateException(e.getMessage());
      }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(FutureWithdrawContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TRANSFER_FEE;
  }

  private boolean isFutureWithdrawable(byte[] ownerAddress, long headBlockTime) {
    var headBlockTickDay = Util.makeDayTick(headBlockTime);
    var ownerAcc = dbManager.getAccountStore().get(ownerAddress);
    var summary = ownerAcc.getFutureSummary();
    if(summary == null || headBlockTickDay < summary.getLowerTime() || summary.getTotalDeal() <= 0 || summary.getTotalBalance() <= 0)
        return false;
    else
        return true;
  }

    private void withdraw(byte[] ownerAddress, long headBlockTime){
        var headBlockTickDay = Util.makeDayTick(headBlockTime);
        var futureStore = dbManager.getFutureTransferStore();
        var accountStore = dbManager.getAccountStore();
        var summary = accountStore.get(ownerAddress).getFutureSummary();
        var ownerAcc = dbManager.getAccountStore().get(ownerAddress);

        Assert.isTrue(summary != null && summary.getLowerTime() <= headBlockTickDay, "No future deal to withdraw");

        //then loop to withdraw, the most fastest way!!!
        var tmpTickKeyBs = summary.getLowerTick();
        var withdrawAmount = 0;
        var withdrawDeal = 0;
        while (true){
            if(tmpTickKeyBs == null)
                break;
            var tmpTick = futureStore.get(tmpTickKeyBs.toByteArray());
            if(tmpTick.getExpireTime() <= headBlockTickDay)
            {
                //withdraw
                withdrawAmount += tmpTick.getBalance();
                withdrawDeal ++;
                //delete
                futureStore.delete(tmpTickKeyBs.toByteArray());
                tmpTickKeyBs = tmpTick.getNextTick();
            }
            else
                break;
        }

        /**
         * all deals withdrawed: remove summary
         */
        if(tmpTickKeyBs == null){
            ownerAcc.clearFuture();
            ownerAcc.addBalance(withdrawAmount);
            dbManager.getAccountStore().put(ownerAddress, ownerAcc);
            return;
        }

        /**
         * some deals remain: update head & summary
         */
        var newHead = futureStore.get(tmpTickKeyBs.toByteArray());
        newHead.setPrevTick(null);
        futureStore.put(tmpTickKeyBs.toByteArray(), newHead);

        //save summary
        summary = summary.toBuilder()
                .setTotalDeal(summary.getTotalDeal() - withdrawDeal)
                .setTotalBalance(summary.getTotalBalance() - withdrawAmount)
                .setLowerTick(tmpTickKeyBs)
                .setLowerTime(newHead.getExpireTime())
                .build();
        ownerAcc.setFutureSummary(summary);
        ownerAcc.addBalance(withdrawAmount);
        dbManager.getAccountStore().put(ownerAddress, ownerAcc);
    }
}
