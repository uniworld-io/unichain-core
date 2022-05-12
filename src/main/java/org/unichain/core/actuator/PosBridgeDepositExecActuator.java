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
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.PosBridgeDepositExecContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import static org.unichain.core.capsule.PosBridgeTokenMappingCapsule.*;

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

            var tokenMapRoot2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var rootKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.rootChainId, decodedMsg.rootTokenAddr).getBytes();
            var childMap = tokenMapRoot2ChildStore.get(rootKey);
            var assetType = (int)childMap.getAssetType();
            var childTokenAddr = childMap.getTokenByChainId(decodedMsg.childChainId);

            switch (assetType){
                case ASSET_TYPE_NATIVE:
                case ASSET_TYPE_TOKEN: {
                    //load token and transfer from token owner to ...
                    var symbol = dbManager.getTokenAddrSymbolIndexStore().get(Hex.decodeHex(childTokenAddr)).getSymbol();
                    var tokenOwner = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(symbol));

                    var wrapCtx = Contract.TransferTokenContract.newBuilder()
                            .setOwnerAddress(tokenOwner.getOwnerAddress())
                            .setToAddress(ByteString.copyFrom(Hex.decodeHex(decodedMsg.receiveAddr)))
                            .setTokenName(symbol)
                            .setAmount(decodedMsg.value)
                            .build();
                    var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var wrapActuator = new TokenTransferActuatorV4(contract, dbManager);
                    var wrapRet = new TransactionResultCapsule();
                    wrapActuator.execute(wrapRet);
                    ret.setFee(wrapRet.getFee());
                    break;
                }
                case ASSET_TYPE_NFT: {
                    var symbol = dbManager.getNftAddrSymbolIndexStore().get(Hex.decodeHex(childTokenAddr)).getSymbol();
                    var nftContractStore = dbManager.getNftTemplateStore();
                    var contractKey =  Util.stringAsBytesUppercase(symbol);
                    var nft = nftContractStore.get(contractKey);

                    //@todo how to mint nft with token id & uri ?
                    var wrapCtx = Contract.MintNftTokenContract.newBuilder()
                            .setOwnerAddress(ByteString.copyFrom(nft.getOwner()))
                            .setContract(symbol)
                            .setToAddress(ByteString.copyFrom(Hex.decodeHex(decodedMsg.receiveAddr)))
                            .setUri("missing!!!") //@todo how to set token id, uri from source ?
                            .setMetadata(Long.toHexString(decodedMsg.value))
                            .build();
                    var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.MintNftTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var wrapActuator = new NftMintTokenActuator(contract, dbManager);
                    var wrapRet = new TransactionResultCapsule();
                    wrapActuator.execute(wrapRet);
                    ret.setFee(wrapRet.getFee());
                    break;
                }
                default:
                    break;
            }

            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);

            var event = NativeContractEvent.builder()
                    .topic("PosBridgeDepositTokenExec")
                    .rawData(
                            PosBridgeTokenDepositExecEvent.builder()
                                    .owner_address(Hex.encodeHexString(ctx.getOwnerAddress().toByteArray()))
                                    .root_chainid(decodedMsg.rootChainId)
                                    .root_token(decodedMsg.rootTokenAddr)
                                    .child_chainid(decodedMsg.childChainId)
                                    .child_token(childTokenAddr)
                                    .receive_address(decodedMsg.receiveAddr)
                                    .data(decodedMsg.value)
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
            var posConfig = dbManager.getPosBridgeConfigStore().get();


            //valid signatures ?
            PosBridgeUtil.validateSignatures(ctx.getMessage(), ctx.getSignaturesList(), posConfig);

            var decodedMsg = PosBridgeUtil.decodePosBridgeDepositExecMsg(ctx.getMessage());
            var tokenMapRoot2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();

            //token mapped ?
            var rootKey = PosBridgeUtil.makeTokenMapKey(decodedMsg.rootChainId, decodedMsg.rootTokenAddr).getBytes();
            Assert.isTrue(tokenMapRoot2ChildStore.has(rootKey) && tokenMapRoot2ChildStore.get(rootKey).hasChainId(decodedMsg.childChainId),
                    "token unmapped or unmatched asset type");

            //make sure this command belong to our chain ?
            Assert.isTrue(PosBridgeUtil.isUnichain(decodedMsg.childChainId), "unrecognized child chain id: " + decodedMsg);

            //make sure valid receiver
            Assert.isTrue(Wallet.addressValid(Hex.decodeHex(decodedMsg.receiveAddr)), "invalid receive address");

            var childMap = tokenMapRoot2ChildStore.get(rootKey);
            var assetType = (int)childMap.getAssetType();
            var childTokenAddr = childMap.getTokenByChainId(decodedMsg.childChainId);

            //make sure asset exist
            switch (assetType){
                case ASSET_TYPE_NATIVE:
                case ASSET_TYPE_TOKEN:
                    Assert.isTrue(dbManager.getTokenAddrSymbolIndexStore().has(Hex.decodeHex(childTokenAddr)), "token with address not found: " + decodedMsg);
                    break;
                case ASSET_TYPE_NFT:
                    Assert.isTrue(dbManager.getNftAddrSymbolIndexStore().has(Hex.decodeHex(childTokenAddr)), "nft with address not found: " + decodedMsg);
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
