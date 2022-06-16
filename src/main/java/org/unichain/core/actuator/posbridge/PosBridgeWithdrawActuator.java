/*
 * Unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Unichain-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.unichain.core.actuator.posbridge;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.PosBridgeTokenWithdrawEvent;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.PosBridgeWithdrawContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.crypto.WalletUtils;
import org.web3j.utils.Numeric;

import static org.unichain.common.utils.PosBridgeUtil.cleanUniPrefix;
import static org.unichain.common.utils.PosBridgeUtil.lookupChildToken;

@Slf4j(topic = "actuator")
public class PosBridgeWithdrawActuator extends AbstractActuator {

    public PosBridgeWithdrawActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeWithdrawContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            //load token map

            var tokenMapStore = dbManager.getChildTokenMapStore();
            var childChainId = Wallet.getChainId();
            var childKey = PosBridgeUtil.makeTokenMapKey(childChainId, ctx.getChildToken());
            var tokenMap = tokenMapStore.get(childKey.getBytes());

            //transfer back token
            var childToken = ByteString.copyFrom(Numeric.hexStringToByteArray(ctx.getChildToken()));

            var childTokenManager = lookupChildToken(tokenMap.getTokenType(), dbManager, ret);
            childTokenManager.withdraw(ctx.getOwnerAddress(), childToken, ctx.getData());

            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);


            this.emitWithdrawExecuted(ret, childChainId,
                    tokenMap.getRootChainId(), ctx.getChildToken(),
                    Numeric.toHexString(ctx.getOwnerAddress().toByteArray()),
                    ctx.getReceiveAddress(), ctx.getData());
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            ret.setStatus(fee, code.FAILED);
            throw new ContractExeException(e.getMessage());
        }
    }

    @Override
    public boolean validate() throws ContractValidateException {
        try {
            Assert.notNull(contract, "No contract!");
            Assert.notNull(dbManager, "No dbManager!");
            Assert.isTrue(contract.is(PosBridgeWithdrawContract.class), "contract type error,expected type [PosBridgeWithdrawContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeWithdrawContract.class);
            var accountStore = dbManager.getAccountStore();

            var config = dbManager.getPosBridgeConfigStore().get();
            Assert.isTrue(config.isInitialized(), "POSBridge not initialized yet");

            Assert.isTrue(Wallet.addressValid(ctx.getChildToken()), "INVALID_CHILD_TOKEN");
            //make sure receive address is valid
            Assert.isTrue(WalletUtils.isValidAddress(ctx.getReceiveAddress()), "INVALID_RECEIVER");

            //make sure token mapped
            var childKey = PosBridgeUtil.makeTokenMapKey(Wallet.getChainId(), ctx.getChildToken());
            var tokenMapStore = dbManager.getChildTokenMapStore();
            Assert.isTrue(tokenMapStore.has(childKey.getBytes()), "TOKEN_NOT_MAPPED: " + childKey);

            Assert.isTrue(accountStore.get(getOwnerAddress().toByteArray()).getBalance() >= fee, "Not enough balance to cover fee, require " + fee + "ginza");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeWithdrawContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }

    private void emitWithdrawExecuted(TransactionResultCapsule ret, long childChainId,
                                      long rootChainId, String childToken,
                                      String burner, String withdrawer,
                                      String withdrawData){
        //emit event
        var event = NativeContractEvent.builder()
                .topic("WithdrawExecuted")
                .rawData(
                        PosBridgeTokenWithdrawEvent.builder()
                                .childChainId(childChainId)
                                .rootChainId(rootChainId)
                                .childToken(cleanUniPrefix(childToken))
                                .burner(cleanUniPrefix(burner))
                                .withdrawer(cleanUniPrefix(withdrawer))
                                .withdrawData(withdrawData)
                                .build())
                .build();
        logger.info("===============> WithdrawExecuted: " + event);
        emitEvent(event, ret);
    }
}
