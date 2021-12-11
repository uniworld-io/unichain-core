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
import org.unichain.common.utils.ByteArray;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.AssetIssueCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.ParticipateAssetIssueContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

@Slf4j(topic = "actuator")
public class ParticipateAssetIssueActuator extends AbstractActuator {

  ParticipateAssetIssueActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      final ParticipateAssetIssueContract participateAssetIssueContract = contract.unpack(Contract.ParticipateAssetIssueContract.class);
      long cost = participateAssetIssueContract.getAmount();

      //subtract from owner address
      byte[] ownerAddress = participateAssetIssueContract.getOwnerAddress().toByteArray();
      AccountCapsule ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
      long balance = Math.subtractExact(ownerAccount.getBalance(), cost);
      balance = Math.subtractExact(balance, fee);
      ownerAccount.setBalance(balance);
      byte[] key = participateAssetIssueContract.getAssetName().toByteArray();

      //calculate the exchange amount
      AssetIssueCapsule assetIssueCapsule;
      assetIssueCapsule = this.dbManager.getAssetIssueStoreFinal().get(key);

      long exchangeAmount = Math.multiplyExact(cost, assetIssueCapsule.getNum());
      exchangeAmount = Math.floorDiv(exchangeAmount, assetIssueCapsule.getUnxNum());
      ownerAccount.addAssetAmountV2(key, exchangeAmount, dbManager);

      //add to to_address
      byte[] toAddress = participateAssetIssueContract.getToAddress().toByteArray();
      AccountCapsule toAccount = this.dbManager.getAccountStore().get(toAddress);
      toAccount.setBalance(Math.addExact(toAccount.getBalance(), cost));
      if (!toAccount.reduceAssetAmountV2(key, exchangeAmount, dbManager)) {
        throw new ContractExeException("reduceAssetAmount failed !");
      }

      //write to db
      dbManager.getAccountStore().put(ownerAddress, ownerAccount);
      dbManager.getAccountStore().put(toAddress, toAccount);
      dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().getAddress().toByteArray(), fee);
      ret.setStatus(fee, Protocol.Transaction.Result.code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | ArithmeticException | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (!this.contract.is(ParticipateAssetIssueContract.class)) {
      throw new ContractValidateException("contract type error,expected type [ParticipateAssetIssueContract],real type[" + contract.getClass() + "]");
    }

    final ParticipateAssetIssueContract participateAssetIssueContract;
    try {
      participateAssetIssueContract = this.contract.unpack(ParticipateAssetIssueContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    //Parameters check
    byte[] ownerAddress = participateAssetIssueContract.getOwnerAddress().toByteArray();
    byte[] toAddress = participateAssetIssueContract.getToAddress().toByteArray();
    byte[] assetName = participateAssetIssueContract.getAssetName().toByteArray();
    long amount = participateAssetIssueContract.getAmount();

    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }
    if (!Wallet.addressValid(toAddress)) {
      throw new ContractValidateException("Invalid toAddress");
    }
//    if (!TransactionUtil.validAssetName(assetName)) {
//      throw new ContractValidateException("Invalid assetName");
//    }
    if (amount <= 0) {
      throw new ContractValidateException("Amount must greater than 0!");
    }

    if (Arrays.equals(ownerAddress, toAddress)) {
      throw new ContractValidateException("Cannot participate asset Issue yourself !");
    }

    //Whether the account exist
    AccountCapsule ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
    if (ownerAccount == null) {
      throw new ContractValidateException("Account does not exist!");
    }
    try {
      //Whether the balance is enough
      long fee = calcFee();
      if (ownerAccount.getBalance() < Math.addExact(amount, fee)) {
        throw new ContractValidateException("No enough balance !");
      }

      //Whether have the mapping
      AssetIssueCapsule assetIssueCapsule;
      assetIssueCapsule = this.dbManager.getAssetIssueStoreFinal().get(assetName);
      if (assetIssueCapsule == null) {
        throw new ContractValidateException("No asset named " + ByteArray.toStr(assetName));
      }

      if (!Arrays.equals(toAddress, assetIssueCapsule.getOwnerAddress().toByteArray())) {
        throw new ContractValidateException("The asset is not issued by " + ByteArray.toHexString(toAddress));
      }
      //Whether the exchange can be processed: to see if the exchange can be the exact int
      long now = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
      if (now >= assetIssueCapsule.getEndTime() || now < assetIssueCapsule.getStartTime()) {
        throw new ContractValidateException("No longer valid period!");
      }

      int unxNum = assetIssueCapsule.getUnxNum();
      int num = assetIssueCapsule.getNum();
      long exchangeAmount = Math.multiplyExact(amount, num);
      exchangeAmount = Math.floorDiv(exchangeAmount, unxNum);
      if (exchangeAmount <= 0) {
        throw new ContractValidateException("Can not process the exchange!");
      }

      AccountCapsule toAccount = this.dbManager.getAccountStore().get(toAddress);
      if (toAccount == null) {
        throw new ContractValidateException("To account does not exist!");
      }

      if (!toAccount.assetBalanceEnoughV2(assetName, exchangeAmount, dbManager)) {
        throw new ContractValidateException("Asset balance is not enough !");
      }
    } catch (ArithmeticException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return this.contract.unpack(Contract.ParticipateAssetIssueContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
