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
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.AssetIssueCapsule;
import org.unichain.core.capsule.CreateTokenCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.AssetIssueContract;
import org.unichain.protos.Contract.AssetIssueContract.FrozenSupply;
import org.unichain.protos.Contract.CreateTokenContract;
import org.unichain.protos.Protocol.Account.Frozen;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @fixme update bizz
 * - update bizz
 * - charge fee to pool fee
 */
@Slf4j(topic = "actuator")
public class CreateTokenActuator extends AbstractActuator {

  CreateTokenActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    long fee = calcFee();
    try {
      CreateTokenContract subContract = contract.unpack(CreateTokenContract.class);
      byte[] ownerAddress = subContract.getOwnerAddress().toByteArray();
      CreateTokenCapsule capsule = new CreateTokenCapsule(subContract);
      //gen token id
      long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
      tokenIdNum++;
      capsule.setId(Long.toString(tokenIdNum));
      dbManager.getDynamicPropertiesStore().saveTokenIdNum(tokenIdNum);

      //dont allow same name
      capsule.setPrecision(0);
      dbManager.getTokenStore().put(capsule.createDbKey(), capsule);

      chargeFee(ownerAddress, fee);
      //dont move pool fee to burned unx account
      dbManager.adjustBalance(ownerAddress, -subContract.getFeePool());

      AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      //add token issued list, we never allow the same token name
      accountCapsule.addTokenIssued(capsule.createDbKey(), capsule.getTotalSupply());
      dbManager.getAccountStore().put(ownerAddress, accountCapsule);

      //then add new token pool info
      dbManager.getTokenStore().put(capsule.createDbKey(), capsule);

      ret.setAssetIssueID(Long.toString(tokenIdNum));
      ret.setStatus(fee, code.SUCESS);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    } catch (BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    } catch (ArithmeticException e) {
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
    if (!this.contract.is(CreateTokenContract.class)) {
      throw new ContractValidateException("contract type error, expected type [CreateTokenContract],real type[" + contract.getClass() + "]");
    }

    final CreateTokenContract createTokenContract;
    try {
      createTokenContract = this.contract.unpack(CreateTokenContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }

    byte[] ownerAddress = createTokenContract.getOwnerAddress().toByteArray();
    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("Invalid ownerAddress");
    }

    if (!TransactionUtil.validTokenName(createTokenContract.getName().toByteArray())) {
      throw new ContractValidateException("Invalid tokenName");
    }

    //token name unx reserved
    String name = createTokenContract.getName().toStringUtf8().toLowerCase();
    if (name.equals("unx")) {
      throw new ContractValidateException("assetName can't be unx");
    }

    int precision = createTokenContract.getPrecision();
    if (precision != 0 && dbManager.getDynamicPropertiesStore().getAllowSameTokenName() != 0) {
      if (precision < 0 || precision > 6) {
        throw new ContractValidateException("precision cannot exceed 6");
      }
    }

    if ((!createTokenContract.getAbbr().isEmpty()) && !TransactionUtil.validTokenName(createTokenContract.getAbbr().toByteArray())) {
      throw new ContractValidateException("Invalid abbreviation for token");
    }

    if (!TransactionUtil.validUrl(createTokenContract.getUrl().toByteArray())) {
      throw new ContractValidateException("Invalid url");
    }

    if (!TransactionUtil.validAssetDescription(createTokenContract.getDescription().toByteArray())) {
      throw new ContractValidateException("Invalid description");
    }

    if (createTokenContract.getStartTime() == 0) {
      throw new ContractValidateException("Start time should be not empty");
    }
    if (createTokenContract.getEndTime() == 0) {
      throw new ContractValidateException("End time should be not empty");
    }
    if (createTokenContract.getEndTime() <= createTokenContract.getStartTime()) {
      throw new ContractValidateException("End time should be greater than start time");
    }
    if (createTokenContract.getStartTime() <= dbManager.getHeadBlockTimeStamp()) {
      throw new ContractValidateException("Start time should be greater than HeadBlockTime");
    }

    //don't allow conflict with
    byte[] tokenNameArr = createTokenContract.getName().toByteArray();
    if (this.dbManager.getDynamicPropertiesStore().getAllowSameTokenName() == 0
        && (this.dbManager.getAssetIssueStore().get(tokenNameArr) != null
           || this.dbManager.getTokenStore().get(tokenNameArr) != null)) {
      throw new ContractValidateException("Token exists");
    }

    if (createTokenContract.getTotalSupply() <= 0) {
      throw new ContractValidateException("TotalSupply must greater than 0!");
    }

    if (createTokenContract.getMaxSupply() <= 0) {
      throw new ContractValidateException("MaxSupply must greater than 0!");
    }

    if (createTokenContract.getMaxSupply() < createTokenContract.getTotalSupply()) {
      throw new ContractValidateException("MaxSupply must greater or equal than TotalSupply!");
    }

    if (createTokenContract.getFee() < 0) {
      throw new ContractValidateException("Token transfer fee as token must greater or equal than 0!");
    }

    if (createTokenContract.getFeePool() < 0) {
      throw new ContractValidateException("pre-transfer pool fee as UNW must greater or equal than 0!");
    }

    AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
    if (accountCapsule == null) {
      throw new ContractValidateException("Account not exists");
    }

    if (accountCapsule.getBalance() < calcFee() + createTokenContract.getFeePool()) {
      throw new ContractValidateException("No enough balance for fee & pre-transfer pool fee");
    }
    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(CreateTokenContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    //use the same asset issue fee, default to 500 UNW
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();
  }
}
