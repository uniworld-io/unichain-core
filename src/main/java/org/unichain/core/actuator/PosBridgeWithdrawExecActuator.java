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
import org.unichain.common.event.PosBridgeTokenWithdrawExecEvent;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.PosBridgeWithdrawExecContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import static org.unichain.core.capsule.PosBridgeTokenMappingCapsule.*;

@Slf4j(topic = "actuator")
public class PosBridgeWithdrawExecActuator extends AbstractActuator {

    PosBridgeWithdrawExecActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeWithdrawExecContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            //decode msg
            var decodedMsg = PosBridgeUtil.decodePosBridgeWithdrawExecMsg(ctx.getMessage().toByteArray());

            var root2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var rootKeyStr = PosBridgeUtil.makeTokenMapKey(Long.toHexString(decodedMsg.rootChainId) , decodedMsg.rootTokenAddr);
            var rootKey = rootKeyStr.getBytes();
            var childMap = root2ChildStore.get(rootKey);
            var receiveAddr = Hex.decodeHex(decodedMsg.receiveAddr);

            var assetType = (int)childMap.getAssetType();

            //unlock asset
            switch (assetType){
                case ASSET_TYPE_NATIVE: {
                    var wrapCtx = Contract.TransferContract.newBuilder()
                            .setAmount(decodedMsg.data)
                            .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setToAddress(ByteString.copyFrom(receiveAddr))
                            .build();
                    var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var wrapActuator = new TransferActuator(contract, dbManager);
                    var wrapRet = new TransactionResultCapsule();
                    wrapActuator.execute(wrapRet);
                    ret.setFee(wrapRet.getFee());
                    break;
                }
                case ASSET_TYPE_TOKEN: {
                    var symbol = dbManager.getTokenAddrSymbolIndexStore().get(decodedMsg.rootTokenAddr.getBytes()).getSymbol();
                    var wrapCtx = Contract.TransferTokenContract.newBuilder()
                            .setAmount(decodedMsg.data)
                            .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_TOKEN_WALLET)))
                            .setToAddress(ByteString.copyFrom(receiveAddr))
                            .setTokenName(symbol)
                            .setAvailableTime(0L)
                            .build();
                    var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var wrapAct = new TokenTransferActuatorV4(contract, dbManager);
                    var wrapRet = new TransactionResultCapsule();
                    wrapAct.execute(wrapRet);
                    ret.setFee(wrapRet.getFee());
                    break;
                }
                case ASSET_TYPE_NFT: {
                    var symbol = dbManager.getNftAddrSymbolIndexStore().get(decodedMsg.rootTokenAddr.getBytes()).getSymbol();
                    var wrapCtx = Contract.TransferNftTokenContract.newBuilder()
                            .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NFT_WALLET)))
                            .setToAddress(ByteString.copyFrom(receiveAddr))
                            .setContract(symbol)
                            .setTokenId(decodedMsg.data)
                            .build();
                    var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferNftTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var wrapActuator = new NftTransferTokenActuator(contract, dbManager);
                    var wrapRet = new TransactionResultCapsule();
                    wrapActuator.execute(wrapRet);
                    ret.setFee(wrapRet.getFee());
                    break;
                }
                default:
                    throw new Exception("invalid asset type");
            }

            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);

            var event = NativeContractEvent.builder()
                    .topic("PosBridgeWithdrawTokenExec")
                    .rawData(
                            PosBridgeTokenWithdrawExecEvent.builder()
                                    .owner_address(Hex.encodeHexString(ctx.getOwnerAddress().toByteArray()))
                                    .root_chainid(decodedMsg.rootChainId)
                                    .root_token(decodedMsg.rootTokenAddr)
                                    .child_chainid(decodedMsg.childChainId)
                                    .child_token(decodedMsg.childTokenAddr)
                                    .receive_address(decodedMsg.receiveAddr)
                                    .withdraw_address(decodedMsg.withdrawAddr)
                                    .data(decodedMsg.data)
                                    .type((int) decodedMsg.assetType)
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
            Assert.isTrue(contract.is(PosBridgeWithdrawExecContract.class), "contract type error,expected type [PosBridgeWithdrawExecContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeWithdrawExecContract.class);
            var accountStore = dbManager.getAccountStore();
            var msg = ctx.getMessage().toByteArray();
            var signatures = ctx.getSignatures().toByteArray();
            var posConfig = dbManager.getPosBridgeConfigStore().get();

            //check permission
            checkExecPermission(ctx, posConfig);

            //check signatures
            PosBridgeUtil.validateSignatures(msg, signatures, posConfig);

            //check mapped token
            checkWithdrawAsset(msg);

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

    private void checkWithdrawAsset(byte[] msg) throws Exception{
        var decodedMsg = PosBridgeUtil.decodePosBridgeWithdrawExecMsg(msg);

        //token mapped ?
        var root2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
        var rootKeyStr = PosBridgeUtil.makeTokenMapKey(Long.toHexString(decodedMsg.rootChainId) , decodedMsg.rootTokenAddr);
        var rootKey = rootKeyStr.getBytes();
        Assert.isTrue(root2ChildStore.has(rootKey), "unmapped token: " + rootKeyStr);
        var childMap = root2ChildStore.get(rootKey);
        Assert.isTrue( childMap.hasChainId(decodedMsg.childChainId) && childMap.getTokenByChainId(decodedMsg.childChainId).equals(decodedMsg.childTokenAddr), "un-matched mapping token: " + decodedMsg.childChainId + ", token: " + decodedMsg.childTokenAddr);

        //command is for unichain ?
        Assert.isTrue( PosBridgeUtil.isUnichain(decodedMsg.rootChainId) , "unrecognized chainId: " + decodedMsg.rootChainId);

        //check receive addr
        var receiveAddr = Hex.decodeHex(decodedMsg.receiveAddr);
        Assert.isTrue(Wallet.addressValid(receiveAddr), "invalid receiving address");

        //check asset
        var assetType = (int)childMap.getAssetType();

        switch (assetType){
            case ASSET_TYPE_NATIVE: {
                var wrapCtx = Contract.TransferContract.newBuilder()
                        .setAmount(decodedMsg.data)
                        .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                        .setToAddress(ByteString.copyFrom(receiveAddr))
                        .build();
                var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferContract)
                        .getInstance()
                        .getRawData()
                        .getContract(0)
                        .getParameter();
                var wrapActuator = new TransferActuator(contract, dbManager);
                wrapActuator.validate();
                break;
            }
            case ASSET_TYPE_TOKEN: {
                var symbol = dbManager.getTokenAddrSymbolIndexStore().get(decodedMsg.rootTokenAddr.getBytes()).getSymbol();
                var wrapCtx = Contract.TransferTokenContract.newBuilder()
                        .setAmount(decodedMsg.data)
                        .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_TOKEN_WALLET)))
                        .setToAddress(ByteString.copyFrom(receiveAddr))
                        .setTokenName(symbol)
                        .setAvailableTime(0L)
                        .build();
                var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                        .getInstance()
                        .getRawData()
                        .getContract(0)
                        .getParameter();
                var wrapActuator = new TokenTransferActuatorV4(contract, dbManager);
                wrapActuator.validate();
                break;
            }
            case ASSET_TYPE_NFT: {
                var symbol = dbManager.getNftAddrSymbolIndexStore().get(decodedMsg.rootTokenAddr.getBytes()).getSymbol();
                var wrapCtx = Contract.TransferNftTokenContract.newBuilder()
                        .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NFT_WALLET)))
                        .setToAddress(ByteString.copyFrom(receiveAddr))
                        .setContract(symbol)
                        .setTokenId(decodedMsg.data) //@todo review make sure nft tokenId is consistent along deposit/withdraw flow
                        .build();
                var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferNftTokenContract)
                        .getInstance()
                        .getRawData()
                        .getContract(0)
                        .getParameter();
                var wrapActuator = new NftTransferTokenActuator(contract, dbManager);
                wrapActuator.validate();
                break;
            }
            default:
                throw new Exception("invalid asset type");
        }
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
