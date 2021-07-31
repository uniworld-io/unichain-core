/*
 * unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * unichain-core is distributed in the hope that it will be useful,
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
import lombok.var;
import org.joda.time.LocalDateTime;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.TransferTokenContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.TokenTransferType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Objects;

@Slf4j(topic = "actuator")
public class TransferTokenActuator extends AbstractActuator {

  TransferTokenActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      TransferTokenContract subContract = contract.unpack(TransferTokenContract.class);
      var ownerAddress = subContract.getOwnerAddress().toByteArray();
      var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
      var tokenName = subContract.getTokenName().toByteArray();
      var tokenPool = dbManager.getTokenStore().get(tokenName);
      var toAddress = subContract.getToAddress().toByteArray();

      // if account with to_address does not exist, create it first.
      AccountCapsule toAccount = dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        boolean withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        toAccount = new AccountCapsule(ByteString.copyFrom(toAddress), Protocol.AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccount);
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }

      //charge pool fee
      tokenPool.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      tokenPool.setFeePool(tokenPool.getFeePool() - fee);
      dbManager.getTokenStore().put(tokenName, tokenPool);

      TokenTransferType transferType = subContract.getType();
      //charge token
      if(Arrays.equals(ownerAddress, subContract.getOwnerAddress().toByteArray())){
        //just transfer from owner: free token
        ownerAccountCap.burnToken(tokenName, subContract.getAmount());
        dbManager.getAccountStore().put(ownerAddress, ownerAccountCap);
        var toAccountCap = dbManager.getAccountStore().get(toAddress);
        if(transferType == TokenTransferType.Instant){
          toAccountCap.addToken(tokenName, subContract.getAmount());
        }
        else {
          toAccountCap.addTokenFuture(tokenName, subContract.getAmount(), subContract.getAvailableTime());
        }
        dbManager.getAccountStore().put(toAddress, toAccountCap);
      }
      else {
        var tokenPoolOwnerAddr = subContract.getOwnerAddress().toByteArray();
        var tokenPoolOwnerCap = dbManager.getAccountStore().get(tokenPoolOwnerAddr);
        tokenPoolOwnerCap.mineToken(tokenName, tokenPool.getFee());
        dbManager.getAccountStore().put(tokenPoolOwnerAddr, tokenPoolOwnerCap);

        ownerAccountCap.burnToken(tokenName, subContract.getAmount());
        dbManager.getAccountStore().put(ownerAddress, ownerAccountCap);

        var toAccountCap = dbManager.getAccountStore().get(toAddress);
        if(transferType == TokenTransferType.Instant){
          toAccountCap.addToken(tokenName, subContract.getAmount() - tokenPool.getFee());
        }
        else
        {
          toAccountCap.addTokenFuture(tokenName, subContract.getAmount() - tokenPool.getFee(), subContract.getAvailableTime());
        }

        dbManager.getAccountStore().put(toAddress, toAccountCap);
      }

      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
    return true;
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(TransferTokenContract.class)) {
      throw new ContractValidateException("contract type error, expected type [TransferTokenContract],real type[" + contract.getClass() + "]");
    }

    long fee = calcFee();

    final TransferTokenContract subContract;
    try {
      subContract = this.contract.unpack(TransferTokenContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    var ownerAddress = subContract.getOwnerAddress().toByteArray();
    var ownerAccountCap = dbManager.getAccountStore().get(ownerAddress);
    if(Objects.isNull(ownerAccountCap))
      throw new ContractValidateException("Owner account not found");

    var tokenName = subContract.getTokenName().toByteArray();
    var tokenPool = dbManager.getTokenStore().get(tokenName);
    if(Objects.isNull(tokenPool))
      throw new ContractValidateException("Token pool not found: " + subContract.getTokenName());

    if(tokenPool.getEndTime() <= dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Token expired at: "+ (new LocalDateTime(tokenPool.getEndTime())));

    if(tokenPool.getStartTime() < dbManager.getHeadBlockTimeStamp())
      throw new ContractValidateException("Token pending to start at: "+ (new LocalDateTime(tokenPool.getStartTime())));

    var toAddress = subContract.getToAddress().toByteArray();
    if (!Wallet.addressValid(toAddress)) {
      throw new ContractValidateException("Invalid toAddress");
    }

    var toAccountCap = dbManager.getAccountStore().get(toAddress);
    if (toAccountCap == null) {
      fee += dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
    }

    if(tokenPool.getFeePool() < fee)
      throw new ContractValidateException("not enough token pool fee balance");

    if(subContract.getAmount() <= 0)
      throw new ContractValidateException("invalid transfer amount, expect positive number");

    if(subContract.getType() == Protocol.TokenTransferType.Future){
      if(subContract.getAvailableTime() <= dbManager.getHeadBlockTimeStamp())
        throw new ContractValidateException("block time passed available time");

      if(subContract.getAvailableTime() >= tokenPool.getEndTime())
        throw new ContractValidateException("available time exceeded token expired time");
    }

    if(ownerAccountCap.getTokenAvailable(tokenName) < subContract.getAmount())
      throw new ContractValidateException("not enough token balance");

    //after TvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
    if (dbManager.getDynamicPropertiesStore().getAllowTvmSolidity059() == 1
            && toAccountCap != null
            && toAccountCap.getType() == Protocol.AccountType.Contract) {
      throw new ContractValidateException("Cannot transfer token to smartContract.");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(TransferTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }
}
