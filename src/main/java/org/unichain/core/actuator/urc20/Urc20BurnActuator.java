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
import org.unichain.core.capsule.urc20.Urc20BurnContractCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc20BurnContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.math.BigInteger;
import java.util.Arrays;

@Slf4j(topic = "actuator")
public class Urc20BurnActuator extends AbstractActuator {

  public Urc20BurnActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc20BurnContract.class);
      var ctxCap = new Urc20BurnContractCapsule(ctx);
      var contractAddr = ctx.getAddress().toByteArray();
      var contractStore = dbManager.getUrc20ContractStore();
      var accountStore = dbManager.getAccountStore();
      var contractCap = contractStore.get(contractAddr);
      var contractOwner = contractCap.getOwnerAddress().toByteArray();
      var burnerAddr = ctx.getOwnerAddress().toByteArray();

      if(Arrays.equals(contractOwner, burnerAddr)){
        //if owner: just burn token
        var ownerCap = accountStore.get(burnerAddr);
        ownerCap.burnUrc20Token(contractAddr, ctxCap.getAmount());
        accountStore.put(burnerAddr, ownerCap);

        //update contract info
        contractCap.burnToken(ctxCap.getAmount());
        contractCap.setFeePool(Math.subtractExact(contractCap.getFeePool(), fee));
        contractCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
        contractCap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
        contractStore.put(contractAddr, contractCap);
      }
      else {
        var tokenFee = BigInteger.valueOf(contractCap.getFee())
                .add(Utils.divideCeiling(ctxCap.getAmount()
                                .multiply(BigInteger.valueOf(contractCap.getExtraFeeRate())),
                        BigInteger.valueOf(100L)));
        var realBurn = ctxCap.getAmount().subtract(tokenFee);

        //update burner
        var burnerCap = accountStore.get(burnerAddr);
        burnerCap.burnUrc20Token(contractAddr, ctxCap.getAmount());
        accountStore.put(burnerAddr, burnerCap);

        //update contract owner
        var contractOwnerCap = accountStore.get(contractOwner);
        contractOwnerCap.addUrc20Token(contractAddr, tokenFee);
        accountStore.put(contractOwner, contractOwnerCap);

        //update contract info
        contractCap.burnToken(realBurn);
        contractCap.setFeePool(Math.subtractExact(contractCap.getFeePool(), fee));
        contractCap.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
        contractCap.setCriticalUpdateTime(dbManager.getHeadBlockTimeStamp());
        contractStore.put(contractAddr, contractCap);
      }

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
      Assert.isTrue(contract.is(Urc20BurnContract.class), "Contract type error,expected type [Urc20BurnContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Urc20BurnContract.class);
      val ctxCap = new Urc20BurnContractCapsule(ctx);

      var contractStore = dbManager.getUrc20ContractStore();
      var accountStore = dbManager.getAccountStore();

      var burnerAddr = ctx.getOwnerAddress().toByteArray();
      var burnerCap = accountStore.get(burnerAddr);

      Assert.notNull(burnerCap, "Owner address not exist");

      var contractAddr = ctx.getAddress().toByteArray();
      var contractBase58 =  Wallet.encode58Check(contractAddr);
      var contractCap = contractStore.get(contractAddr);
      Assert.notNull(contractCap, "Contract not exist :" + contractBase58);

      var fee = calcFee();
      Assert.isTrue(contractCap.getFeePool() >= fee, "Not enough token pool fee balance, require at least " + fee);
      Assert.notNull(contractCap.getTotalSupply().subtract(ctxCap.getAmount()).compareTo(BigInteger.ZERO) >= 0, "Bad burn amount: violate total supply!");

      var contractOwner = contractCap.getOwnerAddress().toByteArray();

      if(Arrays.equals(contractOwner, burnerAddr)){
          Assert.isTrue(burnerCap.getUrc20TokenAvailable(contractBase58).compareTo(ctxCap.getAmount()) >= 0, "Not enough contract balance of" + contractBase58 + "at least " + ctx.getAmount());
      }
      else {
        var tokenFee = BigInteger.valueOf(contractCap.getFee())
                .add(Utils.divideCeiling(ctxCap.getAmount()
                                .multiply(BigInteger.valueOf(contractCap.getExtraFeeRate())),
                        BigInteger.valueOf(100L)));
        Assert.isTrue(burnerCap.getUrc20TokenAvailable(contractBase58).compareTo(tokenFee) >= 0, "Not enough token balance to cover fee");
      }

      Assert.isTrue(dbManager.getHeadBlockTimeStamp() < contractCap.getEndTime(), "Contract expired at: "+ Utils.formatDateLong(contractCap.getEndTime()));
      Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= contractCap.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(contractCap.getStartTime()));
      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc20BurnContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return Parameter.ChainConstant.TOKEN_TRANSFER_FEE;
  }
}
