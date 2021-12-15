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
import org.unichain.common.storage.Deposit;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.ByteUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.AccountStore;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.TransferAssetContract;
import org.unichain.protos.Protocol.AccountType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;
import java.util.Map;

@Slf4j(topic = "actuator")
public class TransferAssetActuator extends AbstractActuator {

  TransferAssetActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      TransferAssetContract transferAssetContract = this.contract.unpack(TransferAssetContract.class);
      AccountStore accountStore = this.dbManager.getAccountStore();
      byte[] ownerAddress = transferAssetContract.getOwnerAddress().toByteArray();
      byte[] toAddress = transferAssetContract.getToAddress().toByteArray();
      AccountCapsule toAccountCapsule = accountStore.get(toAddress);
      if (toAccountCapsule == null) {
        boolean withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        toAccountCapsule = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, toAccountCapsule);
        fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      }
      ByteString assetName = transferAssetContract.getAssetName();
      long amount = transferAssetContract.getAmount();

      chargeFee(ownerAddress, fee);

      AccountCapsule ownerAccountCapsule = accountStore.get(ownerAddress);
      if (!ownerAccountCapsule.reduceAssetAmountV2(assetName.toByteArray(), amount, dbManager)) {
        throw new ContractExeException("reduceAssetAmount failed !");
      }
      accountStore.put(ownerAddress, ownerAccountCapsule);

      toAccountCapsule.addAssetAmountV2(assetName.toByteArray(), amount, dbManager);
      accountStore.put(toAddress, toAccountCapsule);

      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (BalanceInsufficientException
        | InvalidProtocolBufferException
        | ArithmeticException e) {
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
    if (!this.contract.is(TransferAssetContract.class)) {
      throw new ContractValidateException("contract type error,expected type [TransferAssetContract],real type[" + contract.getClass() + "]");
    }
    final TransferAssetContract transferAssetContract;
    try {
      transferAssetContract = this.contract.unpack(TransferAssetContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    long fee = calcFee();
    byte[] ownerAddress = transferAssetContract.getOwnerAddress().toByteArray();
    byte[] toAddress = transferAssetContract.getToAddress().toByteArray();
    byte[] assetName = transferAssetContract.getAssetName().toByteArray();
    long amount = transferAssetContract.getAmount();

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
      throw new ContractValidateException("Amount must greater than 0.");
    }

    if (Arrays.equals(ownerAddress, toAddress)) {
      throw new ContractValidateException("Cannot transfer asset to yourself.");
    }

    AccountCapsule ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
    if (ownerAccount == null) {
      throw new ContractValidateException("No owner account!");
    }

    if (!this.dbManager.getAssetIssueStoreFinal().has(assetName)) {
      throw new ContractValidateException("No asset !");
    }

    Map<String, Long> asset;
    if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      asset = ownerAccount.getAssetMap();
    } else {
      asset = ownerAccount.getAssetMapV2();
    }
    if (asset.isEmpty()) {
      throw new ContractValidateException("Owner no asset!");
    }

    Long assetBalance = asset.get(ByteArray.toStr(assetName));
    if (null == assetBalance || assetBalance <= 0) {
      throw new ContractValidateException("assetBalance must greater than 0.");
    }
    if (amount > assetBalance) {
      throw new ContractValidateException("assetBalance is not sufficient.");
    }

    AccountCapsule toAccount = this.dbManager.getAccountStore().get(toAddress);
    if (toAccount != null) {
      //after TvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
      if (dbManager.getDynamicPropertiesStore().getAllowTvmSolidity059() == 1 && toAccount.getType() == AccountType.Contract) {
        throw new ContractValidateException("Cannot transfer asset to smartContract.");
      }

      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        assetBalance = toAccount.getAssetMap().get(ByteArray.toStr(assetName));
      } else {
        assetBalance = toAccount.getAssetMapV2().get(ByteArray.toStr(assetName));
      }
      if (assetBalance != null) {
        try {
          assetBalance = Math.addExact(assetBalance, amount); //check if overflow
        } catch (Exception e) {
          logger.debug(e.getMessage(), e);
          throw new ContractValidateException(e.getMessage());
        }
      }
    } else {
      fee = fee + dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
      if (ownerAccount.getBalance() < fee) {
        throw new ContractValidateException(
            "Validate TransferAssetActuator error, insufficient fee.");
      }
    }

    return true;
  }

  public static boolean validateForSmartContract(Deposit deposit, byte[] ownerAddress, byte[] toAddress, byte[] tokenId, long amount) throws ContractValidateException {
    if (deposit == null) {
      throw new ContractValidateException("No deposit!");
    }

    byte[] tokenIdWithoutLeadingZero = ByteUtil.stripLeadingZeroes(tokenId);

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
      throw new ContractValidateException("Amount must greater than 0.");
    }

    if (Arrays.equals(ownerAddress, toAddress)) {
      throw new ContractValidateException("Cannot transfer asset to yourself.");
    }

    AccountCapsule ownerAccount = deposit.getAccount(ownerAddress);
    if (ownerAccount == null) {
      throw new ContractValidateException("No owner account!");
    }

    if (deposit.getAssetIssue(tokenIdWithoutLeadingZero) == null) {
      throw new ContractValidateException("No asset !");
    }
    if (!deposit.getDbManager().getAssetIssueStoreFinal().has(tokenIdWithoutLeadingZero)) {
      throw new ContractValidateException("No asset !");
    }

    Map<String, Long> asset;
    if (deposit.getDbManager().getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      asset = ownerAccount.getAssetMap();
    } else {
      asset = ownerAccount.getAssetMapV2();
    }
    if (asset.isEmpty()) {
      throw new ContractValidateException("Owner no asset!");
    }

    Long assetBalance = asset.get(ByteArray.toStr(tokenIdWithoutLeadingZero));
    if (null == assetBalance || assetBalance <= 0) {
      throw new ContractValidateException("assetBalance must greater than 0.");
    }
    if (amount > assetBalance) {
      throw new ContractValidateException("assetBalance is not sufficient.");
    }

    AccountCapsule toAccount = deposit.getAccount(toAddress);
    if (toAccount != null) {
      if (deposit.getDbManager().getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        assetBalance = toAccount.getAssetMap().get(ByteArray.toStr(tokenIdWithoutLeadingZero));
      } else {
        assetBalance = toAccount.getAssetMapV2().get(ByteArray.toStr(tokenIdWithoutLeadingZero));
      }
      if (assetBalance != null) {
        try {
          Math.addExact(assetBalance, amount); //check if overflow
        } catch (Exception e) {
          logger.debug(e.getMessage(), e);
          throw new ContractValidateException(e.getMessage());
        }
      }
    } else {
      throw new ContractValidateException("Validate InternalTransfer error, no ToAccount. And not allowed to create account in smart contract.");
    }

    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(TransferAssetContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
