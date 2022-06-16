package org.unichain.core.db;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.exception.AccountResourceInsufficientException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.Arrays;

/**
 * With block v4:
 * - all fee
 */
@Slf4j(topic = "DB")
public class BandwidthProcessorV4 extends BandwidthProcessorV2 {
  public BandwidthProcessorV4(Manager manager) {
    super(manager);
  }
  @Override
  protected void consumeCreateNewAccountIfUrc30Transfer(AccountCapsule ownerAccountCapsule, Protocol.Transaction.Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    try {
      var ctx = contract.getParameter().unpack(Contract.TransferTokenContract.class);
      var tokenPool = dbManager.getTokenPoolStore().get(Util.stringAsBytesUppercase(ctx.getTokenName()));
      var ownerAddr = ctx.getOwnerAddress().toByteArray();
      var tokenPoolOwnerAddr = tokenPool.getOwnerAddress().toByteArray();

      if(Arrays.equals(ownerAddr, tokenPoolOwnerAddr)){
        //direct charge owner account
        consumeForCreateNewAccount(ownerAccountCapsule, trace);
      }
      else {
        //now owner, charge pool and in actuator charge token
        super.consumeCreateNewAccountIfUrc30Transfer(ownerAccountCapsule, contract, trace);
      }
    }
    catch (InvalidProtocolBufferException e){
      logger.error("bad contract format {}", e.getMessage(), e);
      throw new ContractValidateException("bad contract format:" + e.getMessage());
    }
  }

  @Override
  protected void consumeCreateNewAccountIfUrc20Transfer(AccountCapsule ownerAccountCapsule, Protocol.Transaction.Contract contract, TransactionTrace trace) throws AccountResourceInsufficientException, ContractValidateException {
    try {
      byte[] contractAddr, ownerAddr;
      switch (contract.getType()){
        case Urc20TransferContract: {
          var ctx = contract.getParameter().unpack(Contract.Urc20TransferContract.class);
          contractAddr = ctx.getAddress().toByteArray();
          ownerAddr = ctx.getOwnerAddress().toByteArray();
          break;
        }
        case Urc20TransferFromContract: {
          var ctx = contract.getParameter().unpack(Contract.Urc20TransferFromContract.class);
          contractAddr = ctx.getAddress().toByteArray();
          ownerAddr = ctx.getOwnerAddress().toByteArray();
          break;
        }
        case Urc20ApproveContract: {
          var ctx = contract.getParameter().unpack(Contract.Urc20ApproveContract.class);
          contractAddr = ctx.getAddress().toByteArray();
          ownerAddr = ctx.getOwnerAddress().toByteArray();
          break;
        }
        default:
          throw new Exception("invalid contract, expect: Urc20TransferContract|Urc20TransferFromContract|Urc20ApproveContract!");
      }

      var urc20Pool = dbManager.getUrc20ContractStore().get(contractAddr);
      var poolOwnerAddr = urc20Pool.getOwnerAddress().toByteArray();

      if(Arrays.equals(ownerAddr, poolOwnerAddr)){
        //direct charge owner account
        consumeForCreateNewAccount(ownerAccountCapsule, trace);
      }
      else {
        //now owner, charge pool and in actuator charge token
        super.consumeCreateNewAccountIfUrc20Transfer(ownerAccountCapsule, contract, trace);
      }
    }
    catch (Exception e){
      logger.error("bad contract format {}", e.getMessage(), e);
      throw new ContractValidateException("bad contract format:" + e.getMessage());
    }
  }
}
