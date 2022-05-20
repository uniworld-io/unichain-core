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
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.FutureWithdrawContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

import static org.unichain.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;

@Slf4j(topic = "actuator")
public class WithdrawFutureActuatorV3 extends AbstractActuator {

    public WithdrawFutureActuatorV3(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        try {
            val ctx = contract.unpack(FutureWithdrawContract.class);
            var ownerAddress = ctx.getOwnerAddress().toByteArray();
            withdraw(ownerAddress, dbManager.getHeadBlockTimeStamp());
            ret.setStatus(calcFee(), code.SUCESS);
            return true;
        }
        catch (Exception e){
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            ret.setStatus(calcFee(), code.FAILED);
            throw new ContractExeException(e.getMessage());
        }
    }

    @Override
    public boolean validate() throws ContractValidateException {
        try {
            Assert.notNull(contract, "No contract!");
            Assert.notNull(dbManager, "No dbManager!");
            Assert.isTrue(contract.is(FutureWithdrawContract.class), "Contract type error,expected type [FutureWithdrawContract], real type[" + contract.getClass() + "]");

            val ctx = this.contract.unpack(FutureWithdrawContract.class);
            var ownerAddress = ctx.getOwnerAddress().toByteArray();
            Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");

            var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
            var readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
            Assert.notNull(accountCapsule, ACCOUNT_EXCEPTION_STR + readableOwnerAddress + "] not exists");
            Assert.isTrue(availableToWithdraw(ownerAddress, dbManager.getHeadBlockTimeStamp()), "Account does not have any future balance");
            return true;
        }
        catch (Exception e){
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(FutureWithdrawContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }

    private boolean availableToWithdraw(byte[] ownerAddress, long headBlockTime) {
        var headBlockTickDay = Util.makeDayTick(headBlockTime);
        var ownerAcc = dbManager.getAccountStore().get(ownerAddress);
        var summary = ownerAcc.getFutureSummary();
        return !((Objects.isNull(summary)
                || (summary.getTotalDeal() <= 0)
                || (headBlockTickDay < summary.getLowerTime())
                || (summary.getTotalBalance() <= 0)));
    }

    private void withdraw(byte[] ownerAddress, long headBlockTime){
        var headBlockTickDay = Util.makeDayTick(headBlockTime);
        var futureStore = dbManager.getFutureTransferStore();
        var accountStore = dbManager.getAccountStore();
        var ownerAcc = dbManager.getAccountStore().get(ownerAddress);
        var summary = ownerAcc.getFutureSummary();

        /**
         * loop to withdraw, the most fastest way!!!
         */
        var tmpTickKeyBs = summary.getLowerTick();
        var withdrawAmount = 0;
        var withdrawDeal = 0;
        var withdrawAll = false;
        while (true){
            if(Objects.isNull(tmpTickKeyBs))
            {
                withdrawAll = true;
                break;
            }

            var tmpTickKey = tmpTickKeyBs.toByteArray();
            if(!futureStore.has(tmpTickKey)){
                withdrawAll = true;
                break;
            }

            var tmpTick = futureStore.get(tmpTickKey);
            if(tmpTick.getExpireTime() > headBlockTickDay)
            {
                break;
            }

            /**
             * withdraw deals
             */
            withdrawAmount += tmpTick.getBalance();
            withdrawDeal ++;
            futureStore.delete(tmpTickKeyBs.toByteArray());
            tmpTickKeyBs = tmpTick.getNextTick();
            continue;
        }

        /**
         * all deals withdraw: remove summary
         */
        if(withdrawAll){
            ownerAcc.clearFuture();
            ownerAcc.addBalance(withdrawAmount);
            accountStore.put(ownerAddress, ownerAcc);
            return;
        }

        /**
         * some deals remain: update head & summary
         */
        var newHead = futureStore.get(tmpTickKeyBs.toByteArray());
        newHead.clearPrevTick();
        futureStore.put(tmpTickKeyBs.toByteArray(), newHead);
        summary = summary.toBuilder()
                .setTotalDeal(summary.getTotalDeal() - withdrawDeal)
                .setTotalBalance(summary.getTotalBalance() - withdrawAmount)
                .setLowerTick(tmpTickKeyBs)
                .setLowerTime(newHead.getExpireTime())
                .build();
        ownerAcc.setFutureSummary(summary);
        ownerAcc.addBalance(withdrawAmount);
        accountStore.put(ownerAddress, ownerAcc);
    }
}
