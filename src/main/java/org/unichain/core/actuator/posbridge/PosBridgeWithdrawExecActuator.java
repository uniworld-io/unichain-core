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
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.actuator.posbridge.ext.Predicate;
import org.unichain.protos.Contract.PosBridgeWithdrawExecContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.utils.Numeric;

import static org.unichain.common.utils.PosBridgeUtil.lookupPredicate;

@Slf4j(topic = "actuator")
public class PosBridgeWithdrawExecActuator extends AbstractActuator {

    public PosBridgeWithdrawExecActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeWithdrawExecContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            //decode msg
            var decodedMsg = PosBridgeUtil.decodePosBridgeWithdrawExecMsg(ctx.getMessage());

            var tokenMapStore = dbManager.getRootTokenMapStore();
            var childKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.childChainId , decodedMsg.childTokenAddr);
            var tokenMap = tokenMapStore.get(childKey.getBytes());
            var config = dbManager.getPosBridgeConfigStore().get();

            Predicate predicate = lookupPredicate(tokenMap.getTokenType(), dbManager, ret, config);

            ByteString rootToken = ByteString.copyFrom(Numeric.hexStringToByteArray(tokenMap.getRootToken()));
            ByteString receiver = ByteString.copyFrom(Numeric.hexStringToByteArray(decodedMsg.receiveAddr));
            predicate.unlockTokens(receiver, rootToken, Hex.encodeHexString(decodedMsg.withdrawData.getValue()));

            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);
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
            Assert.isTrue(contract.is(PosBridgeWithdrawExecContract.class), "contract type error,expected type [PosBridgeWithdrawExecContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeWithdrawExecContract.class);
            var accountStore = dbManager.getAccountStore();
            var posConfig = dbManager.getPosBridgeConfigStore().get();

            Assert.isTrue(posConfig.isInitialized(), "POSBridge not initialized yet");

            //check permission
            checkExecPermission(ctx, posConfig);

            //check signatures
            PosBridgeUtil.validateSignatures(ctx.getMessage(), ctx.getSignaturesList(), posConfig);

            //check mapped token
            var decodedMsg = PosBridgeUtil.decodePosBridgeWithdrawExecMsg(ctx.getMessage());

            //command is for unichain ?
            Assert.isTrue(PosBridgeUtil.isUnichain(decodedMsg.rootChainId) , "ROOT_CHAIN_INVALID");

            //token mapped ?
            var tokenMapStore = dbManager.getRootTokenMapStore();
            var childKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.childChainId , decodedMsg.childTokenAddr);
            Assert.isTrue(tokenMapStore.has(childKey.getBytes()), "TOKEN_NOT_MAPPED: " + childKey);

            //check receive addr
            byte[] receiverAddress = Numeric.hexStringToByteArray(decodedMsg.receiveAddr);
            Assert.isTrue(Wallet.addressValid(receiverAddress), "INVALID_RECEIVER");

            Assert.isTrue(accountStore.get(getOwnerAddress().toByteArray()).getBalance() >= fee, "Not enough balance to cover fee, require " + fee + "ginza");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    //@todo limit this transaction to limited address
    private void checkExecPermission(PosBridgeWithdrawExecContract ctx, final PosBridgeConfigCapsule posConfig) throws Exception{
        //now all address with balance enough can submit validation
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeWithdrawExecContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }
}
