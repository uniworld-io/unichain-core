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
import org.unichain.common.event.PosBridgeTokenWithdrawEvent;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.PosBridgeWithdrawContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;

import static org.unichain.core.capsule.PosBridgeTokenMappingCapsule.*;

//@todo later
@Slf4j(topic = "actuator")
public class PosBridgeWithdrawActuator extends AbstractActuator {

    PosBridgeWithdrawActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeWithdrawContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            //load token map
            var child2RootKey = PosBridgeUtil.makeTokenMapKey(Wallet.getAddressPreFixString(), ctx.getChildToken());
            var childChainId = Wallet.getAddressPreFixByte();

            var rootTokenMap = dbManager.getPosBridgeTokenMapChild2RootStore().get(child2RootKey.getBytes());
            var assetType = (int)rootTokenMap.getAssetType();
            var rootChainIdHex = rootTokenMap.getInstance().getTokensMap().keySet().stream().findFirst().get();
            var rootChainId = (new BigInteger(rootChainIdHex, 16)).longValue();
            var rootToken = rootTokenMap.getTokenByChainId(rootChainId);

            //transfer back token
            switch (assetType){
                case ASSET_TYPE_NATIVE:
                case ASSET_TYPE_TOKEN: {
                    var symbol = dbManager.getTokenAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getChildToken())).getSymbol();
                    var tokenInfo = dbManager.getTokenPoolStore().get(symbol.toUpperCase().getBytes());
                    var wrapCtx = Contract.TransferTokenContract.newBuilder()
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(tokenInfo.getOwnerAddress())
                            .setTokenName(symbol)
                            .setAvailableTime(0L)
                            .build();
                    var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var wrapActuator = new TokenTransferActuatorV4(wrapCap, dbManager);
                    var wrapRet = new TransactionResultCapsule();
                    wrapActuator.execute(wrapRet);
                    ret.setFee(wrapRet.getFee());
                    break;
                }
                case ASSET_TYPE_NFT: {
                    var symbol = dbManager.getNftAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getChildToken())).getSymbol();
                    var wrapCtx = Contract.BurnNftTokenContract.newBuilder()
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setContract(symbol)
                            .setTokenId(ctx.getData())
                            .build();
                    var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.BurnNftTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var wrapActuator = new NftBurnTokenActuator(wrapCap, dbManager);
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

            //emit event
            var event = NativeContractEvent.builder()
                    .topic("PosBridgeWithdrawToken")
                    .rawData(
                            PosBridgeTokenWithdrawEvent.builder()
                                    .owner_address(Hex.encodeHexString(ctx.getOwnerAddress().toByteArray()))
                                    .root_token(rootToken)
                                    .root_chainid(rootChainId)
                                    .child_token(ctx.getChildToken())
                                    .child_chainid(childChainId)
                                    .type(assetType)
                                    .data(ctx.getData())
                                    .receive_address(ctx.getReceiveAddress())
                                    .build())
                    .build();
            emitEvent(event, ret);
            logger.info("withdraw asset: " + event);
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

            //make sure receive address is valid
            Assert.isTrue(Wallet.addressValid(Hex.decodeHex(ctx.getReceiveAddress())), "invalid receive address");

            //make sure token mapped
            var child2RootStr = PosBridgeUtil.makeTokenMapKey(Wallet.getAddressPreFixString(), ctx.getChildToken());
            var child2RootKey = child2RootStr.getBytes();
            Assert.isTrue(dbManager.getPosBridgeTokenMapChild2RootStore().has(child2RootKey), "unmapped token: " + child2RootStr);

            var assetType = (int)dbManager.getPosBridgeTokenMapChild2RootStore().get(child2RootKey).getAssetType();

            //make sure asset exist
            switch (assetType){
                    case ASSET_TYPE_NATIVE:
                    case ASSET_TYPE_TOKEN: {
                        var symbol = dbManager.getTokenAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getChildToken())).getSymbol();
                        var tokenInfo = dbManager.getTokenPoolStore().get(symbol.toUpperCase().getBytes());
                        var wrapCtx = Contract.TransferTokenContract.newBuilder()
                                .setAmount(ctx.getData())
                                .setOwnerAddress(ctx.getOwnerAddress())
                                .setToAddress(tokenInfo.getOwnerAddress())
                                .setTokenName(symbol)
                                .setAvailableTime(0L)
                                .build();
                        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                                .getInstance()
                                .getRawData()
                                .getContract(0)
                                .getParameter();
                        var wrapActuator = new TokenTransferActuatorV4(wrapCap, dbManager);
                        wrapActuator.validate();
                        break;
                    }
                    case ASSET_TYPE_NFT: {
                        var symbol = dbManager.getNftAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getChildToken())).getSymbol();
                        var wrapCtx = Contract.BurnNftTokenContract.newBuilder()
                                .setOwnerAddress(ctx.getOwnerAddress())
                                .setContract(symbol)
                                .setTokenId(ctx.getData())
                                .build();
                        var wrapCap = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.BurnNftTokenContract)
                                .getInstance()
                                .getRawData()
                                .getContract(0)
                                .getParameter();
                        var wrapActuator = new NftBurnTokenActuator(wrapCap, dbManager);
                        wrapActuator.validate();
                        break;
                    }
                    default:
                        throw new Exception("invalid asset type");
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
        return contract.unpack(PosBridgeWithdrawContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }
}
