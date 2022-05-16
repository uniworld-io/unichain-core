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
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.PosBridgeSetupContract;
import org.unichain.protos.Protocol.Transaction.Result.code;
import org.web3j.utils.Numeric;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.unichain.core.services.http.utils.Util.*;

/**
 * @todo clean all address hex prefix for compatible
 * @todo refine setup policy
 * @todo limit predicate address as SYSTEM ADDRESS to prevent withdrawing by user if wallet leaked!!!
 * @todo move to multi-sign wallet to :
 *  + drop Relayer to prevent bottle neck attack
 *  + all validator submit directly to all network
 */

@Slf4j(topic = "actuator")
public class PosBridgeSetupActuator extends AbstractActuator {

    PosBridgeSetupActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(PosBridgeSetupContract.class);
            var ownerAddr = ctx.getOwnerAddress().toByteArray();
            var configStore = dbManager.getPosBridgeConfigStore();
            var config = configStore.get();
            if(ctx.hasField(POSBRIDGE_NEW_OWNER))
                config.setNewOwner(ctx.getNewOwner().toByteArray());

            if(ctx.hasField(POSBRIDGE_MIN_VALIDATOR))
                config.setMinValidator(ctx.getMinValidator());

            if(ctx.hasField(POSBRIDGE_CONSENSUS_RATE))
                config.setConsensusRate(ctx.getConsensusRate());

            if(ctx.hasField(POSBRIDGE_PREDICATE_NATIVE))
                config.setPredicateNative(ctx.getPredicateNative());

            if(ctx.hasField(POSBRIDGE_PREDICATE_TOKEN))
                config.setPredicateErc20(ctx.getPredicateToken());

            if(ctx.hasField(POSBRIDGE_PREDICATE_NFT))
                config.setPredicateErc721(ctx.getPredicateNft());

            if(!ctx.getValidatorsList().isEmpty()) {
                config.clearThenPutValidators(ctx.getValidatorsList());
            }
            config.setInitialized(true);
            configStore.put(config);

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
            Assert.isTrue(contract.is(PosBridgeSetupContract.class), "contract type error, expected type [PosBridgeSetupContract], real type[" + contract.getClass() + "]");
            var fee = calcFee();
            val ctx = this.contract.unpack(PosBridgeSetupContract.class);
            var ownerAcc = dbManager.getAccountStore().get(ctx.getOwnerAddress().toByteArray());
            var configStore = dbManager.getPosBridgeConfigStore();
            var config = configStore.get();

            //check permission
            Assert.isTrue(Arrays.equals(ctx.getOwnerAddress().toByteArray(), config.getOwner()), "unmatched owner");

            if(ctx.hasField(POSBRIDGE_NEW_OWNER)) {
                Assert.isTrue(Wallet.addressValid(ctx.getOwnerAddress().toByteArray()), "Invalid new owner address");
            }

            if(ctx.hasField(POSBRIDGE_MIN_VALIDATOR)) {
                Assert.isTrue(ctx.getMinValidator() >= 1 && ctx.getMinValidator() <= 100, "Invalid new min validator");
            }

            if(ctx.hasField(POSBRIDGE_CONSENSUS_RATE)) {
                Assert.isTrue(ctx.getConsensusRate() >= 50 &&  ctx.getConsensusRate() <= 100, "Invalid consensus rate");
            }

            if(ctx.hasField(POSBRIDGE_PREDICATE_NATIVE)) {
                var predicateAddr = Numeric.hexStringToByteArray(ctx.getPredicateNative());
                Assert.isTrue(Wallet.addressValid(predicateAddr) && dbManager.getAccountStore().has(predicateAddr), "Invalid or not exist native predicate addr");
            }

            if(ctx.hasField(POSBRIDGE_PREDICATE_TOKEN)) {
                var predicateAddr = Numeric.hexStringToByteArray(ctx.getPredicateToken());
                Assert.isTrue(Wallet.addressValid(predicateAddr) && dbManager.getAccountStore().has(predicateAddr), "Invalid or not exist token predicate addr");
            }

            if(ctx.hasField(POSBRIDGE_PREDICATE_NFT)) {
                var predicateAddr = Numeric.hexStringToByteArray(ctx.getPredicateNft());
                Assert.isTrue(Wallet.addressValid(predicateAddr) && dbManager.getAccountStore().has(predicateAddr), "Invalid or not exist NFT predicate addr");
            }

            if(ctx.hasField(POSBRIDGE_MIN_VALIDATOR)) {
                Assert.isTrue(ctx.getMinValidator() >= 1 && ctx.getMinValidator() <= 100, "Invalid new min validator");
            }
            if(!ctx.getValidatorsList().isEmpty()){
                ctx.getValidatorsList().forEach(v -> Assert.isTrue(Wallet.addressValid(Numeric.hexStringToByteArray(v)) , "Invalid validator address -->" + v));
            }
            Assert.isTrue( ownerAcc.getBalance() >= fee, "Balance is not sufficient.");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(PosBridgeSetupContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return 0L;
    }
}
