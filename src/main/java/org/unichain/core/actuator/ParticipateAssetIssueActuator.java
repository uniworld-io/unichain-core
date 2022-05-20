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

  public ParticipateAssetIssueActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val ctx = contract.unpack(Contract.ParticipateAssetIssueContract.class);
      var cost = ctx.getAmount();
      //subtract from owner address
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
      var balance = Math.subtractExact(ownerAccount.getBalance(), cost);
      balance = Math.subtractExact(balance, fee);
      ownerAccount.setBalance(balance);
      var key = ctx.getAssetName().toByteArray();

      //calculate the exchange amount
      var assetIssueCapsule = this.dbManager.getAssetIssueStoreFinal().get(key);

      var exchangeAmount = Math.multiplyExact(cost, assetIssueCapsule.getNum());
      exchangeAmount = Math.floorDiv(exchangeAmount, assetIssueCapsule.getUnxNum());
      ownerAccount.addAssetAmountV2(key, exchangeAmount, dbManager);

      //add to to_address
      var toAddress = ctx.getToAddress().toByteArray();
      var toAccount = this.dbManager.getAccountStore().get(toAddress);
      toAccount.setBalance(Math.addExact(toAccount.getBalance(), cost));
      Assert.isTrue(toAccount.reduceAssetAmountV2(key, exchangeAmount, dbManager), "reduceAssetAmount failed !");

      //write to db
      dbManager.getAccountStore().put(ownerAddress, ownerAccount);
      dbManager.getAccountStore().put(toAddress, toAccount);
      dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().getAddress().toByteArray(), fee);
      ret.setStatus(fee, Protocol.Transaction.Result.code.SUCESS);
      return true;
    } catch (InvalidProtocolBufferException | ArithmeticException | BalanceInsufficientException e) {
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
      Assert.isTrue(this.contract.is(ParticipateAssetIssueContract.class), "Contract type error,expected type [ParticipateAssetIssueContract],real type[" + contract.getClass() + "]");
      val ctx = this.contract.unpack(ParticipateAssetIssueContract.class);

      //Parameters check
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var toAddress = ctx.getToAddress().toByteArray();
      var assetName = ctx.getAssetName().toByteArray();
      var amount = ctx.getAmount();

      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid ownerAddress");
      Assert.isTrue(Wallet.addressValid(toAddress), "Invalid toAddress");
//    if (!TransactionUtil.validAssetName(assetName)) {
//      throw new ContractValidateException("Invalid assetName");
//    }
      Assert.isTrue(amount > 0, "Amount must greater than 0!");
      Assert.isTrue(!Arrays.equals(ownerAddress, toAddress), "Cannot participate asset Issue yourself !");

      //Whether the account exist
      var ownerAccount = this.dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(ownerAccount, "Account does not exist!");

      try {
        //Whether the balance is enough
        var fee = calcFee();
        Assert.isTrue(ownerAccount.getBalance() >= Math.addExact(amount, fee), "No enough balance !");

        //Whether have the mapping
        var assetIssueCapsule = this.dbManager.getAssetIssueStoreFinal().get(assetName);
        Assert.notNull(assetIssueCapsule, "No asset named " + ByteArray.toStr(assetName));
        Assert.isTrue(Arrays.equals(toAddress, assetIssueCapsule.getOwnerAddress().toByteArray()), "The asset is not issued by " + ByteArray.toHexString(toAddress));

        //Whether the exchange can be processed: to see if the exchange can be the exact int
        var now = dbManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
        var validPeriod = now >= assetIssueCapsule.getEndTime() || now < assetIssueCapsule.getStartTime();
        Assert.isTrue(!validPeriod, "No longer valid period!");

        var unxNum = assetIssueCapsule.getUnxNum();
        var num = assetIssueCapsule.getNum();
        var exchangeAmount = Math.multiplyExact(amount, num);
        exchangeAmount = Math.floorDiv(exchangeAmount, unxNum);
        Assert.isTrue(exchangeAmount > 0, "Can not process the exchange!");

        var toAccount = this.dbManager.getAccountStore().get(toAddress);
        Assert.notNull(toAccount, "To account does not exist!");
        Assert.isTrue(toAccount.assetBalanceEnoughV2(assetName, exchangeAmount, dbManager), "Asset balance is not enough !");
      } catch (ArithmeticException e) {
        logger.debug(e.getMessage(), e);
        throw new ContractValidateException(e.getMessage());
      }
      return true;
    } catch (Exception e) {
      logger.error("Actuator error: {} --> ", e.getMessage(), e);;
      throw new ContractValidateException(e.getMessage());
    }
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
