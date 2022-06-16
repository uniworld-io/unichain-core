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
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.PosBridgeDepositExecContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.utils.Numeric;

import static org.unichain.common.utils.PosBridgeUtil.AssetType;
import static org.unichain.common.utils.PosBridgeUtil.lookupChildToken;

@Slf4j(topic = "actuator")
public class PosBridgeDepositExecActuator extends AbstractActuator {

    public PosBridgeDepositExecActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeDepositExecContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();
            var decodedMsg = PosBridgeUtil.decodePosBridgeDepositExecMsg(ctx.getMessage());

            //load token map
            var tokenMapStore = dbManager.getChildTokenMapStore();
            var rootKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.rootChainId, decodedMsg.rootTokenAddr);
            var tokenMap = tokenMapStore.get(rootKey.getBytes());


            var childToken = ByteString.copyFrom(Numeric.hexStringToByteArray(tokenMap.getChildToken()));
            var receiver = ByteString.copyFrom(Numeric.hexStringToByteArray(decodedMsg.receiveAddr));

            var childTokenManager = lookupChildToken(tokenMap.getTokenType(), dbManager, ret);
            childTokenManager.deposit(receiver, childToken, Hex.encodeHexString(decodedMsg.depositData.getValue()));

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
            Assert.isTrue(contract.is(PosBridgeDepositExecContract.class), "contract type error,expected type [PosBridgeDepositExecContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeDepositExecContract.class);
            var accountStore = dbManager.getAccountStore();
            var config = dbManager.getPosBridgeConfigStore().get();

            Assert.isTrue(config.isInitialized(), "POS_BRIDGE_CONFIG_UNSET");
            //valid signatures ?
            PosBridgeUtil.validateSignatures(ctx.getMessage(), ctx.getSignaturesList(), config);

            var decodedMsg = PosBridgeUtil.decodePosBridgeDepositExecMsg(ctx.getMessage());
            logger.info("Capture decode deposit exec: {}", decodedMsg);



            //make sure this command belong to our chain ?
            Assert.isTrue(PosBridgeUtil.isUnichain(decodedMsg.childChainId), "CHILD_CHAIN_INVALID");

            //make sure valid receiver
            Assert.isTrue(Wallet.addressValid(decodedMsg.receiveAddr), "RECEIVER_INVALID");


            //token mapped ?
            var tokenMapStore = dbManager.getChildTokenMapStore();
            var rootKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.rootChainId, decodedMsg.rootTokenAddr);
            Assert.isTrue(tokenMapStore.has(rootKey.getBytes()), "TOKEN_NOT_MAPPED: " + rootKey);

            var tokenMap = tokenMapStore.get(rootKey.getBytes());
            var childTokenAddr = tokenMap.getChildToken();

            //make sure asset exist
            AssetType assetType = AssetType.valueOfNumber(tokenMap.getTokenType());
            switch (assetType){
                case NATIVE:
                case ERC20:
                    Assert.isTrue(dbManager.getUrc20ContractStore().has(Numeric.hexStringToByteArray(childTokenAddr)), "token with address not found: " + decodedMsg);
                    break;
                case ERC721:
                    Assert.isTrue(dbManager.getUrc721ContractStore().has(Numeric.hexStringToByteArray(childTokenAddr)), "Erc721 with address not found: " + decodedMsg);
                    break;
                default:
                    throw new ContractValidateException("invalid asset type");
            }
            Assert.isTrue(accountStore.get(getOwnerAddress().toByteArray()).getBalance() >= fee, "Not enough balance to cover fee, require " + fee + "ginza");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeDepositExecContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }
}
