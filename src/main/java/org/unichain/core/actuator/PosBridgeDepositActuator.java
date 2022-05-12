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
import org.unichain.common.event.PosBridgeTokenDepositEvent;
import org.unichain.common.utils.PosBridgeUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.PosBridgeDepositContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import static org.unichain.core.capsule.PosBridgeTokenMappingCapsule.*;

@Slf4j(topic = "actuator")
public class PosBridgeDepositActuator extends AbstractActuator {

    PosBridgeDepositActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeDepositContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            //load token map
            var root2ChildMap = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var root2ChildKey =  PosBridgeUtil.makeTokenMapKey(Wallet.getAddressPreFixString(), ctx.getRootToken()).getBytes();
            var assetType = (int)root2ChildMap.get(root2ChildKey).getAssetType();
            var childToken = root2ChildMap.get(root2ChildKey).getTokenByChainId(ctx.getChildChainid());

            var rootChainId = (long)Wallet.getAddressPreFixByte();
            var config = dbManager.getPosBridgeConfigStore().get();

            //lock asset
            switch (assetType){
                case ASSET_TYPE_NATIVE: {
                    var wrapCtx = Contract.TransferContract.newBuilder()
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(config.getNativePredicate())
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
                    var symbol = dbManager.getTokenAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getRootToken())).getSymbol();
                    var wrapCtx = Contract.TransferTokenContract.newBuilder()
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(config.getTokenPredicate())
                            .setTokenName(symbol)
                            .setAvailableTime(0L)
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
                    var symbol = dbManager.getNftAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getRootToken())).getSymbol();
                    var wrapCtx = Contract.TransferNftTokenContract.newBuilder()
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(config.getNftPredicate())
                            .setContract(symbol)
                            .setTokenId(ctx.getData())
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

            //emit event
            var event = NativeContractEvent.builder()
                    .topic("PosBridgeDepositToken")
                    .rawData(
                            PosBridgeTokenDepositEvent.builder()
                                    .owner_address(Hex.encodeHexString(ctx.getOwnerAddress().toByteArray()))
                                    .root_token(ctx.getRootToken())
                                    .root_chainid(rootChainId)
                                    .child_token(childToken)
                                    .child_chainid(ctx.getChildChainid())
                                    .type(assetType)
                                    .data(ctx.getData())
                                    .receive_address(ctx.getReceiveAddress())
                                    .build())
                    .build();
            emitEvent(event, ret);
            logger.info("locked asset: " + event);
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
            Assert.isTrue(contract.is(PosBridgeDepositContract.class), "contract type error,expected type [PosBridgeDepositContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeDepositContract.class);
            var ownerAddr = getOwnerAddress().toByteArray();
            var accountStore = dbManager.getAccountStore();
            var config = dbManager.getPosBridgeConfigStore().get();

            Assert.isTrue(config.isInitialized(), "POSBridge not initialized yet");

            //check mapped token
            var root2ChildMap = dbManager.getPosBridgeTokenMapRoot2ChildStore();

            var rootKeyStr = PosBridgeUtil.makeTokenMapKey(Wallet.getAddressPreFixString(), ctx.getRootToken());
            var rootKey = rootKeyStr.getBytes();

            Assert.isTrue(root2ChildMap.has(rootKey), "unmapped token: " + rootKeyStr);
            var childTokens = root2ChildMap.get(rootKey);
            Assert.isTrue(childTokens.hasChainId(ctx.getChildChainid()), "unmapped ChainId: " + ctx.getChildChainid());

            var assetType = childTokens.getAssetType();


            //checking balance
            switch ((int)assetType){
                case ASSET_TYPE_NATIVE: {
                    var wrapCtx = Contract.TransferContract.newBuilder()
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(config.getNativePredicate())
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
                    var symbol = dbManager.getTokenAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getRootToken())).getSymbol();
                    var wrapCtx = Contract.TransferTokenContract.newBuilder()
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(config.getTokenPredicate())
                            .setTokenName(symbol)
                            .setAvailableTime(0L)
                            .build();
                    var contract = new TransactionCapsule(wrapCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var predicateActuator = new TokenTransferActuatorV4(contract, dbManager);
                    predicateActuator.validate();
                    break;
                }
                case ASSET_TYPE_NFT: {
                    var symbol = dbManager.getNftAddrSymbolIndexStore().get(Hex.decodeHex(ctx.getRootToken())).getSymbol();
                    var wrapCtx = Contract.TransferNftTokenContract.newBuilder()
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(config.getNftPredicate())
                            .setContract(symbol)
                            .setTokenId(ctx.getData())
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
}
