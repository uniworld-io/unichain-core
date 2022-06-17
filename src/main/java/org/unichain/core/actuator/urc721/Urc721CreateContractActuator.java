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

package org.unichain.core.actuator.urc721;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.util.Assert;
import org.unichain.common.event.NativeContractEvent;
import org.unichain.common.event.Urc721ContractCreatedEvent;
import org.unichain.core.Wallet;
import org.unichain.core.actuator.AbstractActuator;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.capsule.urc721.Urc721ContractCapsule;
import org.unichain.core.capsule.utils.TransactionUtil;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.Urc721CreateContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.Arrays;

import static org.unichain.core.capsule.urc721.Urc721ContractCapsule.URC721_CREATE_CONTRACT_FIELD_MINTER;

@Slf4j(topic = "actuator")
public class Urc721CreateContractActuator extends AbstractActuator {

  public Urc721CreateContractActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      var ctx = contract.unpack(Urc721CreateContract.class);
      var owner = ctx.getOwnerAddress().toByteArray();
      var tokenAddr = ctx.getAddress().toByteArray();
      var accountStore = dbManager.getAccountStore();

      //save contract
      dbManager.saveUrc721Contract(new Urc721ContractCapsule(ctx, dbManager.getHeadBlockTimeStamp(), 0));

      //create contract account
      dbManager.createDefaultAccount(tokenAddr, Protocol.AccountType.Contract);

      //create minter account
      if (ctx.hasField(URC721_CREATE_CONTRACT_FIELD_MINTER)){
        var minterAddr = ctx.getMinter().toByteArray();
        if(!accountStore.has(minterAddr)){
          dbManager.createDefaultAccount(minterAddr, Protocol.AccountType.Normal);
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }

      chargeFee(owner, fee);
      dbManager.burnFee(fee);
      ret.setStatus(fee, code.SUCESS);

      //emit event
      var event = NativeContractEvent.builder()
              .topic("Urc721ContractCreate")
              .rawData(
                      Urc721ContractCreatedEvent.builder()
                              .owner_address(Hex.encodeHexString(ctx.getOwnerAddress().toByteArray()))
                              .address(Hex.encodeHexString(ctx.getAddress().toByteArray()))
                              .symbol(ctx.getSymbol())
                              .name(ctx.getName())
                              .total_supply(ctx.getTotalSupply())
                              .minter(Hex.encodeHexString(ctx.getMinter().toByteArray()))
                              .build())
              .build();
      emitEvent(event, ret);
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
      Assert.isTrue(contract.is(Urc721CreateContract.class), "contract type error, expected type [Urc721CreateContract],real type[" + contract.getClass() + "]");

      val ctx = this.contract.unpack(Urc721CreateContract.class);
      var accountStore = dbManager.getAccountStore();
      var contractStore = dbManager.getUrc721ContractStore();
      var fee = calcFee();

      var contractAddr = ctx.getAddress().toByteArray();
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var symbol = ctx.getSymbol();
      var name = ctx.getName();
      var ownerAccountCap = accountStore.get(ownerAddr);

      Assert.isTrue(Wallet.addressValid(contractAddr)
              && !accountStore.has(contractAddr)
              && !contractStore.has(contractAddr), "Bad contract address: invalid or already exist");

      Assert.isTrue(Wallet.addressValid(ownerAddr)
                      && accountStore.has(ownerAddr)
                      && !Arrays.equals(dbManager.getBurnAddress(), ownerAddr)
                      && (accountStore.get(ownerAddr).getType() != Protocol.AccountType.Contract),
              "Bad owner account: invalid or not exist, must be normal and not burn address");

      Assert.isTrue(TransactionUtil.validTokenSymbol(symbol), "Invalid contract symbol");
      Assert.isTrue(TransactionUtil.validTokenName(name), "Invalid contract name");

      if (ctx.hasField(URC721_CREATE_CONTRACT_FIELD_MINTER)){
        var minterAddr = ctx.getMinter().toByteArray();
        Assert.isTrue(Wallet.addressValid(minterAddr)
                        && !Arrays.equals(dbManager.getBurnAddress(), minterAddr)
                        && !Arrays.equals(minterAddr, ownerAddr),
                "Bad minter address: must be valid address, not burn address, not owner address");

        if(accountStore.has(minterAddr)){
          Assert.isTrue(accountStore.get(minterAddr).getType() != Protocol.AccountType.Contract, "Minter can not be contract address");
        }
        else {
          //more fee
          fee = Math.addExact(fee, dbManager.getDynamicPropertiesStore().getCreateNewAccountFeeInSystemContract());
        }
      }

      Assert.isTrue(ctx.getTotalSupply() > 0, "TotalSupply must greater than 0");
      Assert.isTrue(ownerAccountCap.getBalance() >= fee, "Not enough balance, gas required: " + fee);

      return true;
    }
    catch (Exception e){
      logger.error("Actuator error: {} --> ", e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(Urc721CreateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getAssetIssueFee();//500 UNW default
  }
}


