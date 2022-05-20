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
import org.unichain.common.storage.Deposit;
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.ByteUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
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

  public TransferAssetActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = this.contract.unpack(TransferAssetContract.class);
      var accountStore = this.dbManager.getAccountStore();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var toAddress = ctx.getToAddress().toByteArray();
      var capsule = accountStore.get(toAddress);

      if (capsule == null) {
        var withDefaultPermission = dbManager.getDynamicPropertiesStore().getAllowMultiSign() == 1;
        capsule = new AccountCapsule(ByteString.copyFrom(toAddress), AccountType.Normal, dbManager.getHeadBlockTimeStamp(), withDefaultPermission, dbManager);
        dbManager.getAccountStore().put(toAddress, capsule);
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
      }
      var assetName = ctx.getAssetName();
      var amount = ctx.getAmount();

      chargeFee(ownerAddress, fee);

      var ownerAccountCapsule = accountStore.get(ownerAddress);
      Assert.isTrue(ownerAccountCapsule.reduceAssetAmountV2(assetName.toByteArray(), amount, dbManager), "reduceAssetAmount failed !");
      accountStore.put(ownerAddress, ownerAccountCapsule);

      capsule.addAssetAmountV2(assetName.toByteArray(), amount, dbManager);
      accountStore.put(toAddress, capsule);

      ret.setStatus(fee, code.SUCESS);
      return true;
    } catch (BalanceInsufficientException | InvalidProtocolBufferException | ArithmeticException e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(TransferAssetContract.class), "Contract type error,expected type [TransferAssetContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(TransferAssetContract.class);
      var fee = calcFee();
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var toAddress = ctx.getToAddress().toByteArray();
      var assetName = ctx.getAssetName().toByteArray();
      var amount = ctx.getAmount();

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");
      Assert.isTrue(amount > 0, "Amount must greater than 0.");
      Assert.isTrue(!Arrays.equals(ownerAddress, toAddress), "Cannot transfer asset to yourself.");

      var ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "No owner account!");
      Assert.isTrue(this.dbManager.getAssetIssueStoreFinal().has(assetName), "No asset!");

      Map<String, Long> asset;
      if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        asset = ownerAccount.getAssetMap();
      } else {
        asset = ownerAccount.getAssetMapV2();
      }
      Assert.isTrue(!asset.isEmpty(), "Owner no asset!");

      var assetBalance = asset.get(ByteArray.toStr(assetName));
      Assert.isTrue(!(null == assetBalance || assetBalance <= 0), "AssetBalance must greater than 0.");
      Assert.isTrue(amount <= assetBalance, "AssetBalance is not sufficient.");

      var toAccount = this.dbManager.getAccountStore().get(toAddress);
      if (toAccount != null) {
        //after UvmSolidity059 proposal, send unx to smartContract by actuator is not allowed.
        var transferAssetToSmartContract = (dbManager.getDynamicPropertiesStore().getAllowUvmSolidity059() == 1) && (toAccount.getType() == AccountType.Contract);
        Assert.isTrue(!transferAssetToSmartContract, "Cannot transfer asset to smartContract.");

        if (dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
          assetBalance = toAccount.getAssetMap().get(ByteArray.toStr(assetName));
        } else {
          assetBalance = toAccount.getAssetMapV2().get(ByteArray.toStr(assetName));
        }
        if (assetBalance != null) {
            Math.addExact(assetBalance, amount); //check if overflow
        }
      } else {
        fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        Assert.isTrue(ownerAccount.getBalance() >= fee, "Validate TransferAssetActuator error, insufficient fee.");
      }

      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
  }

  public static boolean validateForSmartContract(Deposit deposit, byte[] ownerAddress, byte[] toAddress, byte[] tokenId, long amount) throws ContractValidateException {
    try {
      Assert.notNull(deposit, "No deposit!");
      var tokenIdWithoutLeadingZero = ByteUtil.stripLeadingZeroes(tokenId);

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");
      Assert.isTrue(amount > 0, "Amount must greater than 0.");
      Assert.isTrue(!Arrays.equals(ownerAddress, toAddress), "Cannot transfer asset to yourself.");

      var ownerAccount = deposit.getAccount(ownerAddress);
      Assert.notNull(ownerAccount, "No owner account!");

      Assert.notNull(deposit.getAssetIssue(tokenIdWithoutLeadingZero), "No asset !");

      Assert.isTrue(deposit.getDbManager().getAssetIssueStoreFinal().has(tokenIdWithoutLeadingZero), "No asset !");

      Map<String, Long> asset;
      if (deposit.getDbManager().getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        asset = ownerAccount.getAssetMap();
      } else {
        asset = ownerAccount.getAssetMapV2();
      }

      Assert.isTrue(!asset.isEmpty(), "Owner no asset!");
      var assetBalance = asset.get(ByteArray.toStr(tokenIdWithoutLeadingZero));
      Assert.isTrue(!(null == assetBalance || assetBalance <= 0), "AssetBalance must greater than 0.");
      Assert.isTrue (amount <= assetBalance, "AssetBalance is not sufficient.");

      var toAccount = deposit.getAccount(toAddress);
      Assert.notNull(toAccount, "Validate InternalTransfer error, no ToAccount. And not allowed to create account in smart contract.");

      if (deposit.getDbManager().getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
        assetBalance = toAccount.getAssetMap().get(ByteArray.toStr(tokenIdWithoutLeadingZero));
      } else {
        assetBalance = toAccount.getAssetMapV2().get(ByteArray.toStr(tokenIdWithoutLeadingZero));
      }

      if (assetBalance != null) {
          Math.addExact(assetBalance, amount); //check if overflow
      }

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
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
