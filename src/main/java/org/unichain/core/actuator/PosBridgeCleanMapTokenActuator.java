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

package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.PosBridgeTokenMappedEvent;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.PosBridgeCleanMapTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class PosBridgeCleanMapTokenActuator extends AbstractActuator {

    PosBridgeCleanMapTokenActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx0 = this.contract.unpack(PosBridgeCleanMapTokenContract.class);
            var ctx = ctx0.toBuilder()
                    .setRootToken(Numeric.cleanHexPrefix(ctx0.getRootToken()).toLowerCase())
                    .setChildToken(Numeric.cleanHexPrefix(ctx0.getChildToken()).toLowerCase())
                    .build();
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            var root2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var child2RootStore = dbManager.getPosBridgeTokenMapChild2RootStore();

            var rootKeyStr = (Long.toHexString(ctx.getRootChainid()) + "_" + ctx.getRootToken());
            var rootKey = rootKeyStr.getBytes();
            var childKeyStr = (Long.toHexString(ctx.getChildChainid()) + "_" + ctx.getChildToken());
            var childKey = childKeyStr.getBytes();

            var rootCap = root2ChildStore.get(rootKey);
            var empty = rootCap.clearToken(ctx.getChildChainid());
            if(empty)
                root2ChildStore.delete(rootKey);
            else
                root2ChildStore.put(rootKey, rootCap);

            child2RootStore.delete(childKey);

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
                                    .type(ctx.getType())
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
            val ctx0 = this.contract.unpack(PosBridgeCleanMapTokenContract.class);
            var ctx = ctx0.toBuilder()
                    .setRootToken(Numeric.cleanHexPrefix(ctx0.getRootToken()).toLowerCase())
                    .setChildToken(Numeric.cleanHexPrefix(ctx0.getChildToken()).toLowerCase())
                    .build();
            var accountStore = dbManager.getAccountStore();
            var ownerAddr = getOwnerAddress().toByteArray();

            //check permission
            var config = dbManager.getPosBridgeConfigStore().get();
            Assert.isTrue(config.isInitialized(), "POSBridge not initialized yet");
            Assert.isTrue(Arrays.equals(ctx.getOwnerAddress().toByteArray(), config.getOwner()), "unmatched owner");

            //check mapped token pair
            var root2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var child2RootStore = dbManager.getPosBridgeTokenMapChild2RootStore();

            var rootKeyStr = (Long.toHexString(ctx.getRootChainid()) + "_" + ctx.getRootToken());
            var rootKey = rootKeyStr.getBytes();
            var childKeyStr = (Long.toHexString(ctx.getChildChainid()) + "_" + ctx.getChildToken());
            var childKey = childKeyStr.getBytes();

            Assert.isTrue(root2ChildStore.has(rootKey), "not found mapped token pair");
            var rootValue = root2ChildStore.get(rootKey);
            Assert.isTrue(rootValue.hasChainId(ctx.getChildChainid()), "not found mapped token pair");
            Assert.isTrue(rootValue.getAssetType() == ctx.getType(), "miss-matched asset type");

            Assert.isTrue(child2RootStore.has(childKey), "not found mapped token pair");
            var childValue = child2RootStore.get(childKey);
            Assert.isTrue(childValue.hasChainId(ctx.getRootChainid()), "not found mapped token pair");
            Assert.isTrue(childValue.getAssetType() == ctx.getType(), "miss-matched asset type");

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
        return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }
}
