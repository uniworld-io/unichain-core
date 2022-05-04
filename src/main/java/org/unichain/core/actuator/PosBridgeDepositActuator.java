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
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeConfigCapsule;
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

//@todo later
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
            //transfer to predicate address
            switch (ctx.getType()){
                case ASSET_TYPE_NATIVE: {
                    var predicateCtx = Contract.TransferContract.newBuilder()
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
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
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setTokenName(ctx.getRootToken())
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
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setContract(ctx.getRootToken())
                            .setTokenId(ctx.getData())
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
            logger.info("locked asset: " + ctx.toString());
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
            var ownerAcc = accountStore.get(ownerAddr);

            //check mapped token
            var root2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var child2RootStore = dbManager.getPosBridgeTokenMapChild2RootStore();

            var childKeyStr = (Long.toHexString(ctx.getChildChainid()) + "_" + ByteArray.toHexString(ctx.getChildAddress().toByteArray()));
            var childKey = childKeyStr.getBytes();

            var rootKeyStr = (Wallet.getAddressPreFixString() + "_" + ctx.getRootToken().toUpperCase());
            var rootKey = rootKeyStr.getBytes();

            Assert.isTrue(root2ChildStore.has(rootKey) && root2ChildStore.get(rootKey).hasToken(childKeyStr), "unmapped token pair");
            Assert.isTrue(child2RootStore.has(childKey), "unmapped token pair");

            //checking on predicate asset
            switch (ctx.getType()){
                case ASSET_TYPE_NATIVE: {
                    var predicateCtx = Contract.TransferContract.newBuilder()
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
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
                            .setAmount(ctx.getData())
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setTokenName(ctx.getRootToken())
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
                            .setOwnerAddress(ctx.getOwnerAddress())
                            .setToAddress(ByteString.copyFrom(Wallet.decodeFromBase58Check(PosBridgeConfigCapsule.POSBRIDGE_PREDICATE_NATIVE_WALLET)))
                            .setContract(ctx.getRootToken())
                            .setTokenId(ctx.getData())
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
