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
import org.unichain.common.event.PosBridgeTokenMappedEvent;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.PosBridgeCleanMapTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.crypto.WalletUtils;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class PosBridgeCleanMapTokenActuator extends AbstractActuator {

    public PosBridgeCleanMapTokenActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeCleanMapTokenContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            if(PosBridgeUtil.isUnichain(ctx.getRootChainid())){
                var tokenMapStore = dbManager.getRootTokenMapStore();
                tokenMapStore.unmap(ctx.getRootToken(), ctx.getChildChainid(), ctx.getChildToken());
            }

            if(PosBridgeUtil.isUnichain(ctx.getChildChainid())){
                var tokenMapStore = dbManager.getChildTokenMapStore();
                tokenMapStore.unmap(ctx.getChildToken(), ctx.getRootChainid(), ctx.getRootToken());
            }


            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);

            //emit event
            var event = NativeContractEvent.builder()
                    .topic("PosBridgeUnmapToken")
                    .rawData(
                            PosBridgeTokenMappedEvent.builder()
                                    .root_token(ctx.getRootToken())
                                    .root_chainid(ctx.getRootChainid())
                                    .child_token(ctx.getChildToken())
                                    .child_chainid(ctx.getChildChainid())
                                    .build())
                    .build();
            emitEvent(event, ret);
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
            Assert.isTrue(contract.is(PosBridgeCleanMapTokenContract.class), "contract type error,expected type [PosBridgeCleanMapTokenContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeCleanMapTokenContract.class);

            var accountStore = dbManager.getAccountStore();
            var ownerAddr = getOwnerAddress().toByteArray();

            if(PosBridgeUtil.isUnichain(ctx.getRootChainid())){
                Assert.isTrue(Wallet.addressValid(ctx.getRootToken()), "ROOT_TOKEN_INVALID");
                Assert.isTrue(WalletUtils.isValidAddress(ctx.getChildToken()), "CHILD_TOKEN_INVALID");
            }
            if(PosBridgeUtil.isUnichain(ctx.getChildChainid())){
                Assert.isTrue(Wallet.addressValid(ctx.getChildToken()), "CHILD_TOKEN_INVALID");
                Assert.isTrue(WalletUtils.isValidAddress(ctx.getRootToken()), "ROOT_TOKEN_INVALID");
            }

            //check permission
            var config = dbManager.getPosBridgeConfigStore().get();
            Assert.isTrue(config.isInitialized(), "POSBridge not initialized yet");
            Assert.isTrue(Arrays.equals(ctx.getOwnerAddress().toByteArray(), config.getOwner()), "unmatched owner");

            Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough balance to cover fee, require " + fee + "ginza");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeCleanMapTokenContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return 0L;
    }
}
