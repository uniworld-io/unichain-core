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

        var loopDealBs = summary.getLowerTick();
        var futureStore = dbManager.getFutureTransferStore();
        FutureTransferCapsule loopDeal;
        var lowerTime = -1L;
        var atLest = false;

        while (true){
            //check first
            if(Objects.isNull(loopDealBs) || !futureStore.has(loopDealBs.toByteArray()))
                break;
            //load lower time
            loopDeal = futureStore.get(loopDealBs.toByteArray());
            lowerTime = (lowerTime <= 0) ? loopDeal.getExpireTime() : Math.min(lowerTime, loopDeal.getExpireTime());
            loopDealBs = loopDeal.getNextTick();
            atLest = true;
        }

        return atLest && (headBlockTickDay >= lowerTime);
    }


    /**
     * Looping list & delete
     */
    private void withdraw(byte[] ownerAddr, long headBlockTime){
        var headBlockTickDay = Util.makeDayTick(headBlockTime);
        var futureStore = dbManager.getFutureTransferStore();
        var accountStore = dbManager.getAccountStore();
        var ownerAcc = dbManager.getAccountStore().get(ownerAddr);
        var summary = ownerAcc.getFutureSummary();

        /**
         * loop to withdraw
         */
        var loopDealKeyBs = summary.getLowerTick();
        var withdrawDealAmount = 0L;
        var withdrawDealCounter = 0L;
        var headDealKeyBs = summary.getLowerTick();
        var tailDealKeyBs = summary.getUpperTick();
        var lowerTime = -1L;
        var upperTime = -1L;

        while (true){
            if(Objects.isNull(loopDealKeyBs))
                break;

            var loopDealKey = loopDealKeyBs.toByteArray();
            if(!futureStore.has(loopDealKey))
                break;

            var loopDeal = futureStore.get(loopDealKey);
            if(loopDeal.getExpireTime() > headBlockTickDay)
            {
                //keep this deal, update time barrier
                lowerTime = (lowerTime <= 0) ? loopDeal.getExpireTime() : Math.min(lowerTime, loopDeal.getExpireTime());
                upperTime = (upperTime <= 0) ? loopDeal.getExpireTime() :  Math.max(upperTime, loopDeal.getExpireTime());

                //check  next deal
                loopDealKeyBs = loopDeal.getNextTick();
            }
            else {
                /*
                 * withdraw deal
                 */
                withdrawDealAmount = Math.addExact(withdrawDealAmount, loopDeal.getBalance());
                withdrawDealCounter = Math.incrementExact(withdrawDealCounter);

                futureStore.delete(loopDealKey);
                loopDealKeyBs = loopDeal.getNextTick();

                //update summary header/tail pointer
                if (Arrays.equals(headDealKeyBs.toByteArray(), loopDealKey)) {
                    headDealKeyBs = loopDeal.getNextTick();
                }

                if (Arrays.equals(tailDealKeyBs.toByteArray(), loopDealKey)) {
                    tailDealKeyBs = loopDeal.getPrevTick();
                }

                //update link prev
                var prevTickBs = loopDeal.getPrevTick();
                var nextTickBs = loopDeal.getNextTick();
                if (!Objects.isNull(prevTickBs)) {
                    var prevTickKey = prevTickBs.toByteArray();
                    if (futureStore.has(prevTickKey)) {
                        var prevTick = futureStore.get(prevTickKey);
                        prevTick.setNextTick(nextTickBs);
                        futureStore.put(prevTickKey, prevTick);
                    }
                }
                //update link next
                if (!Objects.isNull(nextTickBs)) {
                    var nextTickKey = nextTickBs.toByteArray();
                    if (futureStore.has(nextTickKey)) {
                        var nextTick = futureStore.get(nextTickKey);
                        nextTick.setPrevTick(prevTickBs);
                        futureStore.put(nextTickKey, nextTick);
                    }
                }
            }
        }

        /**
         * update summary
         */
        var withdrawAll = (summary.getTotalDeal() <= withdrawDealCounter);
        if(withdrawAll){
            ownerAcc.clearFuture();
        }
        else {
            //maintain head/tail pointer
            var headDeal = futureStore.get(headDealKeyBs.toByteArray());
            headDeal.clearPrevTick();
            futureStore.put(headDealKeyBs.toByteArray(), headDeal);
            var tailDeal = futureStore.get(tailDealKeyBs.toByteArray());
            tailDeal.clearNextTick();
            futureStore.put(tailDealKeyBs.toByteArray(), tailDeal);

            //maintain summary
            summary = summary.toBuilder()
                    .setTotalDeal(Math.subtractExact(summary.getTotalDeal(), withdrawDealCounter))
                    .setTotalBalance(Math.subtractExact(summary.getTotalBalance(), withdrawDealAmount))
                    .setLowerTick(headDealKeyBs)
                    .setUpperTick(tailDealKeyBs)
                    .setLowerTime(lowerTime)
                    .setUpperTime(upperTime)
                    .build();
            ownerAcc.setFutureSummary(summary);
        }

        //update account balance anyway
        ownerAcc.addBalance(withdrawDealAmount);
        accountStore.put(ownerAddr, ownerAcc);
    }
}
