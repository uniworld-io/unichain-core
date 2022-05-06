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
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.PosBridgeTokenMappingCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.PosBridgeMapTokenContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.capsule.PosBridgeTokenMappingCapsule.*;

@Slf4j(topic = "actuator")
public class PosBridgeMapTokenActuator extends AbstractActuator {

    PosBridgeMapTokenActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(Contract.PosBridgeMapTokenContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            var root2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var child2RootStore = dbManager.getPosBridgeTokenMapChild2RootStore();

            var rootKeyStr = (Long.toHexString(ctx.getRootChainid()) + "_" + ctx.getRootToken());
            var rootKey = rootKeyStr.getBytes();
            var childKeyStr = (Long.toHexString(ctx.getChildChainid()) + "_" + ctx.getChildToken());
            var childKey = childKeyStr.getBytes();

            var rootCap = root2ChildStore.has(rootKey)  ? root2ChildStore.get(rootKey): new PosBridgeTokenMappingCapsule(Protocol.PosBridgeTokenMapping.newBuilder().build());
            rootCap.putToken(childKeyStr, ctx.getType());
            root2ChildStore.put(rootKey, rootCap);

            var childCap = child2RootStore.has(childKey)  ? child2RootStore.get(childKey): new PosBridgeTokenMappingCapsule(Protocol.PosBridgeTokenMapping.newBuilder().build());
            childCap.putToken(rootKeyStr, ctx.getType());
            child2RootStore.put(childKey, childCap);

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
            Assert.isTrue(contract.is(PosBridgeMapTokenContract.class), "contract type error,expected type [PosBridgeMapTokenContract],real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeMapTokenContract.class);
            var accountStore = dbManager.getAccountStore();
            var ownerAddr = ctx.getOwnerAddress().toByteArray();

            //check permission
            var configStore = dbManager.getPosBridgeConfigStore();
            var config = configStore.get();
            Assert.isTrue(Arrays.equals(ctx.getOwnerAddress().toByteArray(), config.getOwner()), "unmatched owner");

            Assert.isTrue(ctx.getChildChainid() != ctx.getRootChainid(), "root chain id must be different from child chain id");

            if(isUniChain(ctx.getRootChainid()))
                checkUniChainToken(ctx.getRootChainid(), ctx.getRootToken(), ctx.getType(), true);
            else
                checkOtherChainToken(ctx.getRootChainid(), ctx.getRootToken(), ctx.getType(), true);

            if(isUniChain(ctx.getChildChainid()))
                checkUniChainToken(ctx.getChildChainid(), ctx.getChildToken(), ctx.getType(), false);
            else
                checkOtherChainToken(ctx.getChildChainid(), ctx.getChildToken(), ctx.getType(), false);

            var root2ChildStore = dbManager.getPosBridgeTokenMapRoot2ChildStore();
            var child2RootStore = dbManager.getPosBridgeTokenMapChild2RootStore();

            var rootKeyStr = (Long.toHexString(ctx.getRootChainid()) + "_" + ctx.getRootToken());
            var rootKey = rootKeyStr.getBytes();
            var childKeyStr = (Long.toHexString(ctx.getChildChainid()) + "_" + ctx.getChildToken());
            var childKey = childKeyStr.getBytes();

            if(root2ChildStore.has(rootKey)){
                var mapped = root2ChildStore.get(rootKey);
                Assert.isTrue(!mapped.hasToken(childKeyStr), "token map already exist");
                Assert.isTrue(mapped.getType() == ctx.getType(), "miss-matched asset type");
            }

            Assert.isTrue(child2RootStore.has(childKey), "token map already exist");

            Assert.isTrue(accountStore.get(ownerAddr).getBalance() >= fee, "Not enough fee");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    private void checkUniChainToken(long chainId, String token, int assetType, boolean isRoot) throws Exception{
        Assert.isTrue(isUniChain(chainId), "not unichain chainId");
        switch (assetType){
            case ASSET_TYPE_NATIVE:
                if(isRoot)
                    Assert.isTrue("UNW".equalsIgnoreCase(token));
            case ASSET_TYPE_TOKEN:
                //check token exist
                var tokenStore = dbManager.getTokenAddrSymbolIndexStore();
                Assert.isTrue(tokenStore.has(Hex.decodeHex(token)), "token asset not found: " + token);
                break;
            case ASSET_TYPE_NFT:
                //check token exist
                var nftStore = dbManager.getNftAddrSymbolIndexStore();
                Assert.isTrue(nftStore.has(Hex.decodeHex(token)), "nft asset not found: " + token);
                break;
            default:
                throw new Exception("invalid asset type");
        }
    }

    /**
     * check EVM-compatible chain info
     */
    private void checkOtherChainToken(long chainId, String token, int assetType, boolean isRoot) throws Exception{
        Assert.isTrue(!isUniChain(chainId), "not expect unichain network");
        switch (assetType){
            case ASSET_TYPE_NATIVE:
                if(isRoot)
                    break;
            case ASSET_TYPE_TOKEN:
            case ASSET_TYPE_NFT:
                Assert.isTrue(Wallet.addressValid(ByteArray.fromHexString(token)), "invalid EVM-compatible token address: " + token);
                break;
            default:
                throw new Exception("invalid asset type");
        }
    }

    private boolean isUniChain(long chainId){
        return (chainId == 0x44) || (chainId == 0x82);
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeMapTokenContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 UNW default
    }
}
