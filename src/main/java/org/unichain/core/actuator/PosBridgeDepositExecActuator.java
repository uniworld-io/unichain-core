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
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.PosBridgeTokenDepositExecEvent;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.internal.ChildTokenService;
import org.unichain.core.services.internal.impl.ChildTokenErc20Service;
import org.unichain.core.services.internal.impl.ChildTokenErc721Service;
import org.unichain.protos.Contract.PosBridgeDepositExecContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.utils.Numeric;

import static org.unichain.common.utils.PosBridgeUtil.*;

@Slf4j(topic = "actuator")
public class PosBridgeDepositExecActuator extends AbstractActuator {

    PosBridgeDepositExecActuator(Any contract, Manager dbManager) {
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

            var tokenMapStore = dbManager.getPosBridgeTokenMapStore();
            var rootKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.rootChainId, decodedMsg.rootTokenAddr).getBytes();
            var tokenMap = tokenMapStore.get(rootKey);
            var assetType = tokenMap.getAssetType();
            var childTokenAddr = tokenMap.getChildToken();


            ChildTokenService childTokenService;
            switch (assetType){
                case ASSET_TYPE_NATIVE:
                case ASSET_TYPE_TOKEN: {
                    childTokenService = new ChildTokenErc20Service(dbManager, ret);
                    break;
                }
                case ASSET_TYPE_NFT: {
                    childTokenService = new ChildTokenErc721Service(dbManager, ret);
                    break;
                }
                default:
                    throw new Exception("invalid asset type");
            }
            var childToken = ByteString.copyFrom(Numeric.hexStringToByteArray(childTokenAddr));
            childTokenService.deposit(ctx.getOwnerAddress(), childToken, Hex.encodeHexString(decodedMsg.value.getValue()));

            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);

            var event = NativeContractEvent.builder()
                    .topic("PosBridgeDepositTokenExec")
                    .rawData(
                            PosBridgeTokenDepositExecEvent.builder()
                                    .owner_address(Numeric.toHexString(ctx.getOwnerAddress().toByteArray()))
                                    .root_chainid(decodedMsg.rootChainId)
                                    .root_token(decodedMsg.rootTokenAddr)
                                    .child_chainid(decodedMsg.childChainId)
                                    .child_token(childTokenAddr)
                                    .receive_address(decodedMsg.receiveAddr)
                                    .data(PosBridgeUtil.abiDecodeToUint256(decodedMsg.value).getValue().longValue())
                                    .type(assetType)
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
            Assert.isTrue(contract.is(PosBridgeDepositExecContract.class), "contract type error,expected type [PosBridgeDepositExecContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeDepositExecContract.class);
            var accountStore = dbManager.getAccountStore();
            var config = dbManager.getPosBridgeConfigStore().get();

            Assert.isTrue(config.isInitialized(), "POSBridge not initialized yet");

            //valid signatures ?
            PosBridgeUtil.validateSignatures(ctx.getMessage(), ctx.getSignaturesList(), config);

            var decodedMsg = PosBridgeUtil.decodePosBridgeDepositExecMsg(ctx.getMessage());

            var tokenMapStore = dbManager.getPosBridgeTokenMapStore();

            //token mapped ?
            var rootKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.rootChainId, decodedMsg.rootTokenAddr).getBytes();
            Assert.isTrue(tokenMapStore.has(rootKey)
                            && tokenMapStore.get(rootKey).getChildChainId() == decodedMsg.childChainId,
                    "token unmapped or unmatched asset type");

            //make sure this command belong to our chain ?
            Assert.isTrue(PosBridgeUtil.isUnichain(decodedMsg.childChainId), "unrecognized child chain id: " + decodedMsg);

            //make sure valid receiver
            Assert.isTrue(Wallet.addressValid(Numeric.hexStringToByteArray(decodedMsg.receiveAddr)), "invalid receive address");

            var tokenMap = tokenMapStore.get(rootKey);
            var assetType = tokenMap.getAssetType();
            var childTokenAddr = tokenMap.getChildToken();

            //make sure asset exist
            switch (assetType){
                case ASSET_TYPE_NATIVE:
                case ASSET_TYPE_TOKEN:
                    Assert.isTrue(dbManager.getTokenAddrSymbolIndexStore().has(Numeric.hexStringToByteArray(childTokenAddr)), "token with address not found: " + decodedMsg);
                    break;
                case ASSET_TYPE_NFT:
                    Assert.isTrue(dbManager.getNftAddrSymbolIndexStore().has(Numeric.hexStringToByteArray(childTokenAddr)), "nft with address not found: " + decodedMsg);
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
