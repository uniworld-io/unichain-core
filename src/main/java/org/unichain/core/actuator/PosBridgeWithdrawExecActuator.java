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
import org.unichain.common.crypto.ECKey;
import org.unichain.common.crypto.Hash;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.RLPList;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.PosBridgeWithdrawExecContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpString;

import java.math.BigInteger;
import java.util.HashMap;

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
            var accountStore = dbManager.getAccountStore();
            var ownerAddr = ctx.getOwnerAddress().toByteArray();
            var ownerAcc = accountStore.get(ownerAddr);

            //uint childchain_id, address childToken, address receiver, uint256 value
            var rlpMsg = RlpDecoder.decode(ctx.getMessage().toByteArray()).getValues();
            var childChainId = (new BigInteger(((RlpString)rlpMsg.get(0)).getBytes())).longValue();
            var childToken = ByteArray.toHexString(((RlpString)rlpMsg.get(1)).getBytes());

            var receiverAddr = ((RlpString)rlpMsg.get(2)).getBytes();
            var tokenData = (new BigInteger(((RlpString)rlpMsg.get(3)).getBytes())).longValue();

            //make sure token mapped
            var childKeyStr = (Long.toHexString(childChainId) + "_" + childToken);
            var mapInfo = dbManager.getPosBridgeTokenMapChild2RootStore().get(childKeyStr.getBytes());
            var rootToken = mapInfo.getFirstToken().split("_");
            var assetType = (int)mapInfo.getType();

            //transfer back
            switch (assetType){
                case ASSET_TYPE_NATIVE: {
                    var predicateCtx = Contract.TransferContract.newBuilder()
                            .setAmount(tokenData)
                            .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setToAddress(ByteString.copyFrom(receiverAddr))
                            .build();
                    var contract = new TransactionCapsule(predicateCtx, Protocol.Transaction.Contract.ContractType.TransferContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var predicateActuator = new TransferActuator(contract, dbManager);
                    var predicateRet = new TransactionResultCapsule();
                    predicateActuator.execute(predicateRet);
                    ret.setFee(predicateRet.getFee());
                    break;
                }
                case ASSET_TYPE_TOKEN: {
                    var predicateCtx = Contract.TransferTokenContract.newBuilder()
                            .setAmount(tokenData)
                            .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setToAddress(ByteString.copyFrom(receiverAddr))
                            .setTokenName(rootToken[1])
                            .setAvailableTime(0L)
                            .build();
                    var contract = new TransactionCapsule(predicateCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var predicateActuator = new TokenTransferActuatorV4(contract, dbManager);
                    var predicateRet = new TransactionResultCapsule();
                    predicateActuator.execute(predicateRet);
                    ret.setFee(predicateRet.getFee());
                    break;
                }
                case ASSET_TYPE_NFT: {
                    var predicateCtx = Contract.TransferNftTokenContract.newBuilder()
                            .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setToAddress(ByteString.copyFrom(receiverAddr))
                            .setContract(rootToken[1])
                            .setTokenId(tokenData)
                            .build();
                    var contract = new TransactionCapsule(predicateCtx, Protocol.Transaction.Contract.ContractType.TransferNftTokenContract)
                            .getInstance()
                            .getRawData()
                            .getContract(0)
                            .getParameter();
                    var predicateActuator = new NftTransferTokenActuator(contract, dbManager);
                    var predicateRet = new TransactionResultCapsule();
                    predicateActuator.execute(predicateRet);
                    ret.setFee(predicateRet.getFee());
                    break;
                }
                default:
                    throw new Exception("invalid asset type");
            }

            ret.setStatus(fee, code.SUCESS);
            logger.info("unlocked asset: " + ctx.toString());
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
            checkExecPermission(ctx);
            checkPosSignatures(ctx);
            checkWithdrawAsset(ctx);
            Assert.isTrue(accountStore.get(getOwnerAddress().toByteArray()).getBalance() >= fee, "Not enough balance to cover fee, require " + fee + "ginza");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    private void checkExecPermission(PosBridgeWithdrawExecContract ctx) throws Exception{
        //now all address with balance enough can submit validation
    }

    private void checkPosSignatures(PosBridgeWithdrawExecContract ctx) throws Exception{
        var config = dbManager.getPosBridgeConfigStore().get();
        var whitelistValidators = config.getValidators();
        var validSignedValidators = new HashMap<String, String>();
        var msg = ctx.getMessage().toByteArray();
        for(var rlpItem : (RLPList)RlpDecoder.decode(ctx.getSignatures().toByteArray()).getValues().get(0)){
            var sig = ((RlpString)rlpItem).getBytes();
            var hash = Hash.sha3(msg);
            var signedAddr = Hex.encodeHexString(ECKey.signatureToAddress(hash, sig));
            //make sure whitelist map is hex address without prefix 0x
            if(whitelistValidators.containsKey(signedAddr))
                validSignedValidators.put(signedAddr, signedAddr);
        };
        var rate = ((double)validSignedValidators.size())/whitelistValidators.size();
        Assert.isTrue(rate >= config.getConsensusRate(), "not enough POS bridge's consensus rate");
    }

    private void checkWithdrawAsset(PosBridgeWithdrawExecContract ctx) throws Exception{
        //uint childChainid, address childToken, address receiver, uint256 value
        var msg = ctx.getMessage().toByteArray();
        var rlpItems = RlpDecoder.decode(msg).getValues();
        var childChainId = (new BigInteger(((RlpString)rlpItems.get(0)).getBytes())).longValue();
        var childToken = ByteArray.toHexString(((RlpString)rlpItems.get(1)).getBytes());
        //make sure token mapped
        var child2RootStore = dbManager.getPosBridgeTokenMapChild2RootStore();
        var childKeyStr = (Long.toHexString(childChainId) + "_" + childToken);
        Assert.isTrue(child2RootStore.has(childKeyStr.getBytes()), "token un-mapped!");

        //check receive addr
        var receiverAddr = ((RlpString)rlpItems.get(2)).getBytes();
        Assert.isTrue(Wallet.addressValid(receiverAddr), "invalid receiving address");

        var mapInfo = child2RootStore.get(childKeyStr.getBytes());
        var rootToken = mapInfo.getFirstToken().split("_");
        var assetType = (int)mapInfo.getType();


        var withdrawData = (new BigInteger(((RlpString)rlpItems.get(3)).getBytes())).longValue();
        switch (assetType){
            case ASSET_TYPE_NATIVE: {
                var predicateCtx = Contract.TransferContract.newBuilder()
                        .setAmount(withdrawData)
                        .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                        .setToAddress(ByteString.copyFrom(receiverAddr))
                        .build();
                var contract = new TransactionCapsule(predicateCtx, Protocol.Transaction.Contract.ContractType.TransferContract)
                        .getInstance()
                        .getRawData()
                        .getContract(0)
                        .getParameter();
                var predicateActuator = new TransferActuator(contract, dbManager);
                predicateActuator.validate();
                break;
            }
            case ASSET_TYPE_TOKEN: {
                var predicateCtx = Contract.TransferTokenContract.newBuilder()
                        .setAmount(withdrawData)
                        .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                        .setToAddress(ByteString.copyFrom(receiverAddr))
                        .setTokenName(rootToken[1])
                        .setAvailableTime(0L)
                        .build();
                var contract = new TransactionCapsule(predicateCtx, Protocol.Transaction.Contract.ContractType.TransferTokenContract)
                        .getInstance()
                        .getRawData()
                        .getContract(0)
                        .getParameter();
                var predicateActuator = new TokenTransferActuatorV4(contract, dbManager);
                predicateActuator.validate();
                break;
            }
            case ASSET_TYPE_NFT: {
                var predicateCtx = Contract.TransferNftTokenContract.newBuilder()
                        .setOwnerAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                        .setToAddress(ByteString.copyFrom(receiverAddr))
                        .setContract(rootToken[1])
                        .setTokenId(withdrawData)
                        .build();
                var contract = new TransactionCapsule(predicateCtx, Protocol.Transaction.Contract.ContractType.TransferNftTokenContract)
                        .getInstance()
                        .getRawData()
                        .getContract(0)
                        .getParameter();
                var predicateActuator = new NftTransferTokenActuator(contract, dbManager);
                predicateActuator.validate();
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
