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
import org.unichain.core.capsule.FutureTransferCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.FutureWithdrawContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

import static org.unichain.core.actuator.ActuatorConstant.ACCOUNT_EXCEPTION_STR;

@Slf4j(topic = "actuator")
public class WithdrawFutureActuatorV6 extends AbstractActuator {

    public WithdrawFutureActuatorV6(Any contract, Manager dbManager) {
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
        if (Objects.isNull(summary)
                || (summary.getTotalDeal() <= 0L)
                || (summary.getTotalBalance() <= 0L)){
            return false;
        }

        var head = summary.getLowerTick();
        var futureStore = dbManager.getFutureTransferStore();
        FutureTransferCapsule loopTick;
        var lowerTime = -1L;
        while (true){
            //load lower time
            loopTick = futureStore.get(head.toByteArray());
            if(lowerTime <= 0)
                lowerTime = loopTick.getExpireTime();
            else
                lowerTime = Math.min(lowerTime, loopTick.getExpireTime());

            head = loopTick.getNextTick();
            if(Objects.isNull(head) || !futureStore.has(head.toByteArray()))
                break;
        }

        return (headBlockTickDay >= lowerTime);
    }

    /**
     * Looping list & delete
     */
    private void withdraw(byte[] ownerAddress, long headBlockTime){
        var headBlockTickDay = Util.makeDayTick(headBlockTime);
        var futureStore = dbManager.getFutureTransferStore();
        var accountStore = dbManager.getAccountStore();
        var ownerAcc = dbManager.getAccountStore().get(ownerAddress);
        var summary = ownerAcc.getFutureSummary();

        /**
         * loop to withdraw
         */
        var loopTickKeyBs = summary.getLowerTick();
        var withdrawAmount = 0L;
        var withdrawDeal = 0L;
        var headBs = summary.getLowerTick();
        var tailBs = summary.getUpperTick();
        var lowerTime = -1L;
        var upperTime = -1L;

        while (true){
            if(Objects.isNull(loopTickKeyBs))
                break;

            var loopTickKey = loopTickKeyBs.toByteArray();
            if(!futureStore.has(loopTickKey))
                break;

            var loopTick = futureStore.get(loopTickKey);
            if(loopTick.getExpireTime() > headBlockTickDay)
            {
                //update time bounder
                if(lowerTime <= 0)
                    lowerTime = loopTick.getExpireTime();
                else
                    lowerTime = Math.min(lowerTime, loopTick.getExpireTime());

                if(upperTime <= 0)
                    upperTime = loopTick.getExpireTime();
                else
                    upperTime = Math.max(upperTime, loopTick.getExpireTime());

                //next tick
                continue;
            }

            /**
             * withdraw deals
             */
            withdrawAmount = Math.addExact(withdrawAmount, loopTick.getBalance());
            withdrawDeal = Math.incrementExact(withdrawDeal);
            futureStore.delete(loopTickKey);
            loopTickKeyBs = loopTick.getNextTick();

            //update head
            if(Arrays.equals(headBs.toByteArray(), loopTickKey)){
                headBs = loopTick.getNextTick();
            }

            //update tail
            if(Arrays.equals(tailBs.toByteArray(), loopTickKey)){
                tailBs = loopTick.getPrevTick();
            }

            //new link prev
            var prevTickBs = loopTick.getPrevTick();
            var nextTickBs = loopTick.getNextTick();
            if(!Objects.isNull(prevTickBs)){
                var prevTickKey = prevTickBs.toByteArray();
                if(futureStore.has(prevTickKey)){
                    var prevTick = futureStore.get(prevTickKey);
                    prevTick.setNextTick(nextTickBs);
                    futureStore.put(prevTickKey, prevTick);
                }
            }
            //update link next
            if(!Objects.isNull(nextTickBs)){
                var nextTickKey = nextTickBs.toByteArray();
                if(futureStore.has(nextTickKey)){
                    var nextTick = futureStore.get(nextTickKey);
                    nextTick.setPrevTick(prevTickBs);
                    futureStore.put(nextTickKey, nextTick);
                }
            }
        }

        /**
         * update summary
         */
        var withdrawAll = (summary.getTotalDeal() <= withdrawDeal);
        if(withdrawAll){
            ownerAcc.clearFuture();
            ownerAcc.addBalance(withdrawAmount);
            accountStore.put(ownerAddress, ownerAcc);
        }
        else {
            //save head tick & tail tick
            var headTick = futureStore.get(headBs.toByteArray());
            headTick.clearPrevTick();
            futureStore.put(headBs.toByteArray(), headTick);
            var tailTick = futureStore.get(tailBs.toByteArray());
            tailTick.clearNextTick();
            futureStore.put(tailBs.toByteArray(), tailTick);

            //save summary
            summary = summary.toBuilder()
                    .setTotalDeal(Math.subtractExact(summary.getTotalDeal(), withdrawDeal))
                    .setTotalBalance(Math.subtractExact(summary.getTotalBalance(), withdrawAmount))
                    .setLowerTick(headBs)
                    .setUpperTick(tailBs)
                    .setLowerTime(lowerTime)
                    .setUpperTime(upperTime)
                    .build();
            ownerAcc.setFutureSummary(summary);
            ownerAcc.addBalance(withdrawAmount);
            accountStore.put(ownerAddress, ownerAcc);
        }
    }
}
