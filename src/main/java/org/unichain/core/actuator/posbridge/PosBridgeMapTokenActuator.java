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
import org.springframework.util.Assert;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.PosBridgeTokenMappedEvent;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.PosBridgeMapTokenContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.utils.Numeric;

import java.util.Arrays;

import static org.unichain.common.utils.PosBridgeUtil.AssetType;
import static org.unichain.common.utils.PosBridgeUtil.NativeToken;

@Slf4j(topic = "actuator")
public class PosBridgeMapTokenActuator extends AbstractActuator {

    public PosBridgeMapTokenActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeMapTokenContract.class);

            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            if(isUniChain(ctx.getRootChainid())){
                var tokenMapStore = dbManager.getRootTokenMapStore();
                tokenMapStore.mapToken(ctx.getType(), ctx.getRootToken(), ctx.getChildChainid(), ctx.getChildToken());
            }
            if(isUniChain(ctx.getChildChainid())){
                var tokenMapStore = dbManager.getChildTokenMapStore();
                tokenMapStore.mapToken(ctx.getType(), ctx.getChildToken(), ctx.getRootChainid(), ctx.getRootToken());
            }

            chargeFee(ownerAddr, fee);
            dbManager.burnFee(fee);
            ret.setStatus(fee, code.SUCESS);

            //emit event
           emitTokenMapped(ret, ctx.getRootChainid(), ctx.getRootToken(), ctx.getChildChainid(), ctx.getChildToken(), ctx.getType());
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
            Assert.isTrue(contract.is(PosBridgeMapTokenContract.class), "contract type error,expected type [PosBridgeMapTokenContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeMapTokenContract.class);
            var accountStore = dbManager.getAccountStore();
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            var config = dbManager.getPosBridgeConfigStore().get();
            Assert.isTrue(config.isInitialized(), "POSBridge not initialized yet");

            //check permission
            Assert.isTrue(Arrays.equals(ctx.getOwnerAddress().toByteArray(), config.getOwner()), "unmatched owner");

            //check valid chain id
            Assert.isTrue(ctx.getChildChainid() != ctx.getRootChainid(), "root chain id must be different from child chain id");
            Assert.isTrue(
                    Wallet.getSupportedPosChainIds().contains(ctx.getChildChainid())
                            && Wallet.getSupportedPosChainIds().contains(ctx.getRootChainid()),
                    "not supported chainId, found rootChainId: " + ctx.getRootChainid() + ", childChainId: " + ctx.getChildChainid()
            );

            //check valid token address
            if (isUniChain(ctx.getRootChainid()))
                checkUniChainToken(ctx.getRootChainid(), ctx.getRootToken(), ctx.getType(), true);
            else
                checkOtherChainToken(ctx.getRootChainid(), ctx.getRootToken(), ctx.getType(), true);

            if (isUniChain(ctx.getChildChainid()))
                checkUniChainToken(ctx.getChildChainid(), ctx.getChildToken(), ctx.getType(), false);
            else
                checkOtherChainToken(ctx.getChildChainid(), ctx.getChildToken(), ctx.getType(), false);

            //make sure un-mapped token
            var tokenMapStore = dbManager.getRootTokenMapStore();
            Assert.isTrue(
                    tokenMapStore.ensureNotMapped(ctx.getChildChainid(), ctx.getRootToken())
                            && tokenMapStore.ensureNotMapped(ctx.getChildChainid(), ctx.getChildToken()),
                    "ALREADY_MAPPED"
            );

            Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough fee");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    private void checkUniChainToken(long _chainId, String token, int numberType, boolean isRoot) throws Exception {
        AssetType assetType = AssetType.valueOfNumber(numberType);
        switch (assetType) {
            case NATIVE:
                if (isRoot) {
                    Assert.isTrue(NativeToken.UNI.equalsIgnoreCase(token), "TOKEN_NATIVE_INVALID");
                } else {
                    var urc20ContractStore = dbManager.getUrc20ContractStore();
                    Assert.isTrue(urc20ContractStore.has(Numeric.hexStringToByteArray(token)), "TOKEN_NOT_FOUND: " + token);
                }
                break;
            case ERC20:
                var urc20ContractStore = dbManager.getUrc20ContractStore();
                Assert.isTrue(urc20ContractStore.has(Numeric.hexStringToByteArray(token)), "TOKEN_NOT_FOUND: " + token);
                break;
            case ERC721:
                var urc721ContractStore = dbManager.getUrc721ContractStore();
                Assert.isTrue(urc721ContractStore.has(Numeric.hexStringToByteArray(token)), "TOKEN_NOT_FOUND: " + token);
                break;
            default:
                throw new Exception("invalid asset type");
        }
    }

    /**
     * check EVM-compatible chain info like bsc, eth ...
     */
    private void checkOtherChainToken(long _chainId, String token, int numberType, boolean isRoot) throws Exception {
        AssetType assetType = AssetType.valueOfNumber(numberType);
        switch (assetType) {
            case NATIVE:
                if (isRoot) {
                    Assert.isTrue(
                            NativeToken.BNB.equalsIgnoreCase(token)
                                    || NativeToken.ETH.equalsIgnoreCase(token),
                            "TOKEN_NATIVE_INVALID");
                }
                break;
            case ERC20:
            case ERC721:
                Assert.isTrue(org.web3j.crypto.WalletUtils.isValidAddress(token), "invalid EVM-compatible token address, found: " + token);
                break;
            default:
                throw new Exception("invalid asset type");
        }
    }

    private boolean isUniChain(long chainId) {
        return Wallet.getAddressPreFixByte() == chainId;
    }


    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeMapTokenContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return 0L;
    }


    private void emitTokenMapped(TransactionResultCapsule ret, long rootChainId,
                                 String rootToken, long childChainId,
                                 String childToken, int type){
        //emit event
        var event = NativeContractEvent.builder()
                .topic("PosBridgeMapToken")
                .rawData(
                        PosBridgeTokenMappedEvent.builder()
                                .root_token(rootToken)
                                .root_chainid(rootChainId)
                                .child_token(childToken)
                                .child_chainid(childChainId)
                                .type(type)
                                .build())
                .build();
        logger.info("=============> Token Mapping: {}", event);
        emitEvent(event, ret);
    }
}
