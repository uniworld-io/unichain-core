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

package org.unichain.core.actuator.urc20;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc20.Urc20ApproveContractCapsule;
import org.unichain.core.capsule.urc20.Urc20SpenderCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc20ApproveContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;
import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc20ApproveActuator extends AbstractActuator {

  public Urc20ApproveActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var spenderStore = dbManager.getUrc20SpenderStore();
      var accountStore = dbManager.getAccountStore();
      var urc20ContractStore = dbManager.getUrc20ContractStore();
      var ctx = contract.unpack(Urc20ApproveContract.class);
      var ctxCap = new Urc20ApproveContractCapsule(ctx);

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var spenderAddr = ctx.getSpender().toByteArray();
      var urc20Addr = ctx.getAddress().toByteArray();
      var urc20Contract = urc20ContractStore.get(urc20Addr);
      var ownerCap = accountStore.get(ownerAddr);
      var urc20OwnerAddr = urc20Contract.getOwnerAddress().toByteArray();
      var urc20OwnerCap = accountStore.get(urc20OwnerAddr);

      var limit = ctxCap.getAmount();

      //create spender account
      var createSpenderAcc = !accountStore.has(spenderAddr);
      if (createSpenderAcc) {
        dbManager.createDefaultAccount(spenderAddr, Protocol.AccountType.Normal);
      }

      if(Arrays.equals(ownerAddr, urc20OwnerAddr)){
           /**
            if owner of token:
            - charge more fee on owner
            - don't charge token fee
          **/
        if(createSpenderAcc)
        {
          var moreFee = dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
          dbManager.adjustBalance(urc20OwnerCap, -moreFee);
        }
      }
      else {
        var tokenFee =  BigInteger.valueOf(urc20Contract.getFee()).add(
                Utils.divideCeiling(ctxCap.getAmount().multiply(BigInteger.valueOf(urc20Contract.getExtraFeeRate())), BigInteger.valueOf(100L)));

        if (createSpenderAcc) {
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
          tokenFee = tokenFee.add(BigInteger.valueOf(urc20Contract.getCreateAccountFee()));
        }

        //charge token fee
        ownerCap.burnUrc20Token(urc20Addr, tokenFee);
        accountStore.put(ownerAddr, ownerCap);
        urc20OwnerCap.addUrc20Token(urc20Addr, tokenFee);
        accountStore.put(urc20OwnerAddr, urc20OwnerCap);
      }

      //set quota
      var spenderKey = Urc20SpenderCapsule.genKey(spenderAddr, urc20Addr);
      if(!spenderStore.has(spenderKey)){
        var quota = new Urc20SpenderCapsule(spenderAddr, urc20Addr, ownerAddr, limit);
        spenderStore.put(spenderKey, quota);
      }
      else {
        var quota = spenderStore.get(spenderKey);
        quota.setQuotaTo(ownerAddr, limit);
        spenderStore.put(spenderKey, quota);
      }

      //charge pool fee by unw
      urc20Contract.setFeePool(Math.subtractExact(urc20Contract.getFeePool(), fee));
      urc20Contract.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
      urc20ContractStore.put(urc20Addr, urc20Contract);

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
      Assert.isTrue(contract.is(Urc20ApproveContract.class), "contract type error,expected type [Urc20ApproveContract],real type[" + contract.getClass() + "]");

      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc20ContractStore();
      var spenderStore = dbManager.getUrc20SpenderStore();
      val ctx = this.contract.unpack(Urc20ApproveContract.class);

      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var spenderAddr = ctx.getSpender().toByteArray();
      var urc20Addr = ctx.getAddress().toByteArray();

      Assert.isTrue( Wallet.addressValid(urc20Addr) && contractStore.has(urc20Addr)
              && Wallet.addressValid(ownerAddr) && accountStore.has(ownerAddr)
              && Wallet.addressValid(spenderAddr), "Unrecognized owner|contract|spender address");

      Assert.isTrue(!Arrays.equals(dbManager.getBurnAddress(), spenderAddr), "Spender can not be burn address");
      Assert.isTrue(!Arrays.equals(ownerAddr, spenderAddr), "Spender must not be owner");

      var urc20AddrBase58 = Wallet.encode58Check(urc20Addr);
      var urc20Cap = contractStore.get(urc20Addr);
      var ownerCap = accountStore.get(ownerAddr);

      //validate fee
      var urc20OwnerAddr = urc20Cap.getOwnerAddress().toByteArray();
      var urc20OwnerCap = accountStore.get(urc20OwnerAddr);
      var createSpenderAcc = !accountStore.has(spenderAddr);

      if(!createSpenderAcc){
        Assert.isTrue(accountStore.get(spenderAddr).getType() != Protocol.AccountType.Contract, "Spender must be not contract account");
      }

      var ctxCap = new Urc20ApproveContractCapsule(ctx);
      var fee = calcFee();
      var tokenFee = BigInteger.ZERO;
      var tokenAvailable = ownerCap.getUrc20TokenAvailable(urc20AddrBase58);
      Assert.isTrue(tokenAvailable.compareTo(BigInteger.ZERO) > 0, "No available token amount found!");

      if(Arrays.equals(ownerAddr, urc20OwnerAddr)){
        if(createSpenderAcc){
          //owner is contract owner: check balance!
          var moreFee = dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract();
          Assert.isTrue(urc20OwnerCap.getBalance() >= moreFee, "Owner account not enough fee, expected gas: " + moreFee);
        }
      }
      else {
        //check token fee
        tokenFee = tokenFee.add(BigInteger.valueOf(urc20Cap.getFee()));

        tokenFee = tokenFee.add(
                Utils.divideCeiling(
                        ctxCap.getAmount().multiply(BigInteger.valueOf(urc20Cap.getExtraFeeRate())),
                        BigInteger.valueOf(100L)));
        if (createSpenderAcc) {
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
          tokenFee = tokenFee.add(BigInteger.valueOf(urc20Cap.getCreateAccountFee()));
        }
        Assert.isTrue(tokenAvailable.compareTo(tokenFee) >= 0, "Not enough token to cover fee, expected: " + tokenFee);
      }

      //check pool fee
      Assert.isTrue(urc20Cap.getFeePool() >= fee, "Not enough urc20 pool fee, expected gas: " + fee);

      //left token avail
      tokenAvailable = tokenAvailable.subtract(tokenFee);

      //validate quota
      var limit = ctxCap.getAmount();
      Assert.isTrue(urc20Cap.getTotalSupply().compareTo(limit) >= 0, "Spender limit reached out contract total supply!");

      var spenderKey = Urc20SpenderCapsule.genKey(spenderAddr, urc20Addr);
      if(!spenderStore.has(spenderKey))
      {
        Assert.isTrue(tokenAvailable.compareTo(limit) >= 0, "Spender amount and fee reached out available token!");
      }
      else {
        spenderStore.get(spenderKey).checkSetQuota(ownerAddr, limit, tokenAvailable);
      }

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc20ApproveContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0L;
  }
}
