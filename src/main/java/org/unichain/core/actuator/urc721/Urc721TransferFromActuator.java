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

package org.unichain.core.actuator.urc721;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc721.Urc721TokenCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc721TransferFromContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc721TransferFromActuator extends AbstractActuator {

    private static Descriptors.FieldDescriptor URC721_TRANSFER_FROM_FIELD_TOKEN_ID = Urc721TransferFromContract.getDescriptor().findFieldByNumber(Urc721TransferFromContract.TOKEN_ID_FIELD_NUMBER);

    public Urc721TransferFromActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
        var fee = calcFee();
        try {
            val ctx = this.contract.unpack(Urc721TransferFromContract.class);
            var accountStore = dbManager.getAccountStore();
            var tokenStore = dbManager.getUrc721TokenStore();

            var fromAddr = ctx.getOwnerAddress().toByteArray();
            var toAddr = ctx.getTo().toByteArray();
            var tokenKey = Urc721TokenCapsule.genTokenKey(ctx.getAddress().toByteArray(), ctx.getTokenId());

            //create new account
            if (!accountStore.has(toAddr)) {
                dbManager.createDefaultAccount(toAddr, Protocol.AccountType.Normal);
                fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
            }

            //purge old token
            var token = tokenStore.get(tokenKey);
            dbManager.removeUrc721Token(tokenKey);

            //save new token
            token.setOwner(ByteString.copyFrom(toAddr))
                    .setLastOperation(dbManager.getHeadBlockTimeStamp())
                    .clearApproval()
                    .clearNext()
                    .clearPrev();
            dbManager.saveUrc721Token(token);

            chargeFee(fromAddr, fee);
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
            Assert.isTrue(contract.is(Urc721TransferFromContract.class), "contract type error,expected type [Urc721TransferFromContract], real type[" + contract.getClass() + "]");
            var fee = calcFee();

            val ctx = this.contract.unpack(Urc721TransferFromContract.class);
            var accountStore = dbManager.getAccountStore();
            var tokenStore = dbManager.getUrc721TokenStore();
            var contractStore = dbManager.getUrc721ContractStore();
            var summaryStore = dbManager.getUrc721AccountTokenRelationStore();

            var operatorAddr = ctx.getOwnerAddress().toByteArray();
            var toAddr = ctx.getTo().toByteArray();
            var contractAddr = ctx.getAddress().toByteArray();

            Assert.isTrue(Wallet.addressValid(operatorAddr)
                            && accountStore.has(operatorAddr)
                            && Wallet.addressValid(contractAddr)
                            && contractStore.has(contractAddr)
                            && Wallet.addressValid(toAddr)
                            && !Arrays.equals(dbManager.getBurnAddress(), toAddr)
                            && (!accountStore.has(toAddr) || (accountStore.get(toAddr).getType() != Protocol.AccountType.Contract)),
                    "Unrecognized  operator|to|contract address");

            Assert.isTrue(ctx.hasField(URC721_TRANSFER_FROM_FIELD_TOKEN_ID) && ctx.getTokenId() >= 0, "Missing or bad token id");

            Assert.isTrue(!Arrays.equals(operatorAddr, toAddr), "Can not transfer to it self");

            var tokenKey = Urc721TokenCapsule.genTokenKey(contractAddr, ctx.getTokenId());
            Assert.isTrue(tokenStore.has(tokenKey), "Token not exist");

            var token = tokenStore.get(tokenKey);
            var tokenOwner = token.getOwner();
            var relation = summaryStore.get(tokenOwner);

            Assert.isTrue(!Arrays.equals(toAddr, tokenOwner),"Can not transfer to it self");

            //check permission
            Assert.isTrue(Arrays.equals(operatorAddr, tokenOwner)
                    || relation.isApprovedForAll(contractAddr, operatorAddr)
                    || token.isApproval(operatorAddr), "Transfer token not allowed: must be owner or approved");

            if (!accountStore.has(toAddr)) {
                fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
            }

            Assert.isTrue(accountStore.get(operatorAddr).getBalance() >= fee, "Not enough balance to cover fee, required gas: " + fee + "ginza");
            return true;
        } catch (Exception e) {
            logger.error("Actuator error: {} --> ", e.getMessage(), e);
            throw new ContractValidateException(e.getMessage());
        }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
        return contract.unpack(Urc721TransferFromContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
        return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
    }
}
