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
import org.unichain.common.event.PosBridgeTokenDepositEvent;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.PosBridgeDepositContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.crypto.WalletUtils;
import org.web3j.utils.Numeric;

import static org.unichain.common.utils.PosBridgeUtil.cleanUniPrefix;
import static org.unichain.common.utils.PosBridgeUtil.lookupPredicate;

@Slf4j(topic = "actuator")
public class PosBridgeDepositActuator extends AbstractActuator {

    public PosBridgeDepositActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeDepositContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            var rootChainId = Wallet.getChainId();

            //load token map
            var tokenMapStore = dbManager.getRootTokenMapStore();
            var keyRoot = PosBridgeUtil.makeTokenMapKey(ctx.getChildChainid(), ctx.getRootToken());
            var tokenMapCap = tokenMapStore.get(keyRoot.getBytes());
            int tokenType = tokenMapCap.getTokenType();

            //lock asset
            var config = dbManager.getPosBridgeConfigStore().get();
            var predicate = lookupPredicate(tokenType, dbManager, ret, config);
            var rootToken = ByteString.copyFrom(Numeric.hexStringToByteArray(ctx.getRootToken()));
            predicate.lockTokens(ctx.getOwnerAddress(), rootToken, ctx.getData());

            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);

            this.emitDepositExecuted(ret, rootChainId,
                    ctx.getChildChainid(), ctx.getRootToken(),
                    Numeric.toHexString(ctx.getOwnerAddress().toByteArray()),
                    ctx.getReceiveAddress(), ctx.getData());
            return true;
        } catch (Exception e){
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
            Assert.isTrue(contract.is(PosBridgeDepositContract.class), "contract type error,expected type [PosBridgeDepositContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeDepositContract.class);

            var ownerAddr = getOwnerAddress().toByteArray();
            var accountStore = dbManager.getAccountStore();
            var config = dbManager.getPosBridgeConfigStore().get();
            logger.info("Found config -------> {}", config.getInstance());

            Assert.isTrue(config.isInitialized(), "POSBridge not initialized yet");
            Assert.isTrue(Wallet.addressValid(ctx.getRootToken()), "ROOT_TOKEN_INVALID");
            Assert.isTrue(WalletUtils.isValidAddress(ctx.getReceiveAddress()), "RECEIVER_INVALID");

            //check mapped token
            if(!PosBridgeUtil.NativeToken.UNI.equalsIgnoreCase(ctx.getRootToken())){
                var tokenMapStore = dbManager.getRootTokenMapStore();
                var rootKey = PosBridgeUtil.makeTokenMapKey(ctx.getChildChainid(), ctx.getRootToken());

                Assert.isTrue(tokenMapStore.has(rootKey.getBytes()), "TOKEN_NOT_MAPPED: " + rootKey);
                var tokenMap = tokenMapStore.get(rootKey.getBytes());
                Assert.isTrue(tokenMap.getChildChainId() == ctx.getChildChainid(), "CHILD_CHAIN_INVALID: " + ctx.getChildChainid());
            }
            Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough balance to cover fee, require " + fee + "ginza");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeDepositContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return 0L;
    }

    private void emitDepositExecuted(TransactionResultCapsule ret,
                                     long rootChainId, long childChainId,
                                     String rootToken, String depositor,
                                     String receiver, String depositData){
        //emit event
        var event = NativeContractEvent.builder()
                .topic("DepositExecuted")
                .rawData(
                        PosBridgeTokenDepositEvent.builder()
                                .rootChainId(rootChainId)
                                .childChainId(childChainId)
                                .rootToken(cleanUniPrefix(rootToken))
                                .depositor(cleanUniPrefix(depositor))
                                .receiver(cleanUniPrefix(receiver))
                                .depositData(depositData)
                                .build())
                .build();
        logger.info("===============> DepositExecuted: " + event);
        emitEvent(event, ret);
    }

}
