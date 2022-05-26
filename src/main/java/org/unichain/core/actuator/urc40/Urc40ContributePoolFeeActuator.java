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

package org.unichain.core.actuator.urc40;

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
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc40ContributePoolFeeContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class Urc40ContributePoolFeeActuator extends AbstractActuator {

    public Urc40ContributePoolFeeActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
        var ctx = contract.unpack(Urc40ContributePoolFeeContract.class);
        var contractAddr = ctx.getAddress().toByteArray();
        var contributeAmount =  ctx.getAmount();
        var contractCap = dbManager.getUrc40ContractStore().get(contractAddr);
        contractCap.setFeePool(Math.addExact(contractCap.getFeePool(), contributeAmount));
        dbManager.getUrc40ContractStore().put(contractAddr, contractCap);
        var ownerAddress = ctx.getOwnerAddress().toByteArray();
        dbManager.adjustBalance(ownerAddress, -Math.addExact(ctx.getAmount(), fee));
        dbManager.burnFee(fee);
        ret.setStatus(fee, code.SUCESS);
    }
    catch (Exception e) {
        logger.error("Actuator error: {} --> ", e.getMessage(), e);;
        ret.setStatus(fee, code.FAILED);
        throw new ContractExeException(e.getMessage());
    }
    return true;
    }

    @Override
    public boolean validate() throws ContractValidateException {
      try {
          Assert.notNull(contract, "No contract!");
          Assert.notNull(dbManager, "No dbManager!");
          Assert.isTrue(contract.is(Urc40ContributePoolFeeContract.class), "Contract type error,expected type [Urc40ContributePoolFeeContract],real type[" + contract.getClass() + "]");

          val ctx  = this.contract.unpack(Urc40ContributePoolFeeContract.class);
          var ownerAccount = dbManager.getAccountStore().get(ctx.getOwnerAddress().toByteArray());
          Assert.notNull(ownerAccount, "Not found ownerAddress");

          var contractAddr = ctx.getAddress().toByteArray();
          var contractAddrBase58 = Wallet.encode58Check(contractAddr);
          var contributeAmount = ctx.getAmount();
          var urc40Contract = dbManager.getUrc40ContractStore().get(contractAddr);
          urc40Contract.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
          Assert.notNull(urc40Contract, "Contract not exist: " + contractAddrBase58);

          Assert.isTrue(dbManager.getHeadBlockTimeStamp() < urc40Contract.getEndTime(), "Contract expired at: " + Utils.formatDateLong(urc40Contract.getEndTime()));
          Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= urc40Contract.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(urc40Contract.getStartTime()));
          Assert.isTrue(ownerAccount.getBalance() >= Math.addExact(contributeAmount, calcFee()), "Not enough balance");
          return true;
      }
      catch (Exception e){
          logger.error("Actuator validate error: {} --> ", e.getMessage(), e);;
          throw  new ContractValidateException(e.getMessage());
      }
    }

    @Override
    public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc40ContributePoolFeeContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
      return Parameter.ChainConstant.TRANSFER_FEE;
    }
}
