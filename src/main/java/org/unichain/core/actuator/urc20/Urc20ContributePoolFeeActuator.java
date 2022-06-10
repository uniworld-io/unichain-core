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
import org.unichain.core.config.Parameter;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc20ContributePoolFeeContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Slf4j(topic = "actuator")
public class Urc20ContributePoolFeeActuator extends AbstractActuator {

    public Urc20ContributePoolFeeActuator(Any contract, Manager dbManager) {
        super(contract, dbManager);
    }

    @Override
    public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
        var ctx = contract.unpack(Urc20ContributePoolFeeContract.class);
        var contractStore = dbManager.getUrc20ContractStore();
        var contractAddr = ctx.getAddress().toByteArray();
        var ownerAddr = ctx.getOwnerAddress().toByteArray();
        var contributeAmount =  ctx.getAmount();

        var contractCap = contractStore.get(contractAddr);
        contractCap.setFeePool(Math.addExact(contractCap.getFeePool(), contributeAmount));
        contractStore.put(contractAddr, contractCap);

        dbManager.adjustBalance(ownerAddr, -Math.addExact(ctx.getAmount(), fee));
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
          Assert.isTrue(contract.is(Urc20ContributePoolFeeContract.class), "Contract type error,expected type [Urc20ContributePoolFeeContract],real type[" + contract.getClass() + "]");

          var accountStore = dbManager.getAccountStore();
          var contractStore = dbManager.getUrc20ContractStore();

          val ctx  = this.contract.unpack(Urc20ContributePoolFeeContract.class);
          var ownerAddr = ctx.getOwnerAddress().toByteArray();
          var contractAddr = ctx.getAddress().toByteArray();
          Assert.isTrue(Wallet.addressValid(ownerAddr) && accountStore.has(ownerAddr)
                  && Wallet.addressValid(contractAddr) && contractStore.has(contractAddr), "Unrecognized owner|contract address");

          var ownerAccount = accountStore.get(ownerAddr);
          Assert.notNull(ownerAccount, "Not found ownerAddress");

          var contractAddrBase58 = Wallet.encode58Check(contractAddr);
          var contributeAmount = ctx.getAmount();
          var urc20Contract = contractStore.get(contractAddr);
          urc20Contract.setLatestOperationTime(dbManager.getHeadBlockTimeStamp());
          Assert.notNull(urc20Contract, "Contract not exist: " + contractAddrBase58);

          Assert.isTrue(dbManager.getHeadBlockTimeStamp() < urc20Contract.getEndTime(), "Contract expired at: " + Utils.formatDateLong(urc20Contract.getEndTime()));
          Assert.isTrue(dbManager.getHeadBlockTimeStamp() >= urc20Contract.getStartTime(), "Contract pending to start at: " + Utils.formatDateLong(urc20Contract.getStartTime()));
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
    return contract.unpack(Urc20ContributePoolFeeContract.class).getOwnerAddress();
    }

    @Override
    public long calcFee() {
      return Parameter.ChainConstant.TRANSFER_FEE;
    }
}
