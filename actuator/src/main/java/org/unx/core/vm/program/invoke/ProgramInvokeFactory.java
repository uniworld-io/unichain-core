package org.unx.core.vm.program.invoke;

import static org.unx.common.runtime.InternalTransaction.UnxType.UNX_CONTRACT_CALL_TYPE;
import static org.unx.common.runtime.InternalTransaction.UnxType.UNX_CONTRACT_CREATION_TYPE;
import static org.unx.common.utils.WalletUtil.generateContractAddress;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.unx.common.runtime.InternalTransaction;
import org.unx.common.runtime.vm.DataWord;
import org.unx.common.utils.ByteUtil;
import org.unx.core.capsule.ContractCapsule;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.vm.program.Program;
import org.unx.core.vm.repository.Repository;
import org.unx.protos.Protocol.Block;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.unx.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "vm")
public class ProgramInvokeFactory {

  /**
   * Invocation by the wire tx
   */
  public static ProgramInvoke createProgramInvoke(InternalTransaction.UnxType unxType,
                                                  InternalTransaction.ExecutorType executorType, Transaction tx, long tokenValue, long tokenId,
                                                  Block block,
                                                  Repository deposit, long vmStartInUs,
                                                  long vmShouldEndInUs, long energyLimit) throws ContractValidateException {
    byte[] contractAddress;
    byte[] ownerAddress;
    long balance;
    byte[] data;
    byte[] lastHash = null;
    byte[] coinbase = null;
    long timestamp = 0L;
    long number = -1L;

    if (unxType == UNX_CONTRACT_CREATION_TYPE) {
      CreateSmartContract contract = ContractCapsule.getSmartContractFromTransaction(tx);
      contractAddress = generateContractAddress(tx);
      ownerAddress = contract.getOwnerAddress().toByteArray();
      balance = deposit.getBalance(ownerAddress);
      data = ByteUtil.EMPTY_BYTE_ARRAY;
      long callValue = contract.getNewContract().getCallValue();

      switch (executorType) {
        case ET_NORMAL_TYPE:
        case ET_PRE_TYPE:
          if (null != block) {
            lastHash = block.getBlockHeader().getRawDataOrBuilder().getParentHash().toByteArray();
            coinbase = block.getBlockHeader().getRawDataOrBuilder().getWitnessAddress()
                .toByteArray();
            timestamp = block.getBlockHeader().getRawDataOrBuilder().getTimestamp() / 1000;
            number = block.getBlockHeader().getRawDataOrBuilder().getNumber();
          }
          break;
        default:
          break;
      }

      return new ProgramInvokeImpl(contractAddress, ownerAddress, ownerAddress, balance, callValue,
          tokenValue, tokenId, data, lastHash, coinbase, timestamp, number, deposit, vmStartInUs,
          vmShouldEndInUs, energyLimit);

    } else if (unxType == UNX_CONTRACT_CALL_TYPE) {
      TriggerSmartContract contract = ContractCapsule
          .getTriggerContractFromTransaction(tx);
      /***         ADDRESS op       ***/
      // YP: Get address of currently executing account.
      byte[] address = contract.getContractAddress().toByteArray();

      /***         ORIGIN op       ***/
      // YP: This is the sender of original transaction; it is never a contract.
      byte[] origin = contract.getOwnerAddress().toByteArray();

      /***         CALLER op       ***/
      // YP: This is the address of the account that is directly responsible for this execution.
      byte[] caller = contract.getOwnerAddress().toByteArray();

      /***         BALANCE op       ***/
      balance = deposit.getBalance(caller);

      /***        CALLVALUE op      ***/
      long callValue = contract.getCallValue();

      /***     CALLDATALOAD  op   ***/
      /***     CALLDATACOPY  op   ***/
      /***     CALLDATASIZE  op   ***/
      data = contract.getData().toByteArray();

      switch (executorType) {
        case ET_CONSTANT_TYPE:
          break;
        case ET_PRE_TYPE:
        case ET_NORMAL_TYPE:
          if (null != block) {
            /***    PREVHASH  op  ***/
            lastHash = block.getBlockHeader().getRawDataOrBuilder().getParentHash().toByteArray();
            /***   COINBASE  op ***/
            coinbase = block.getBlockHeader().getRawDataOrBuilder().getWitnessAddress()
                .toByteArray();
            /*** TIMESTAMP  op  ***/
            timestamp = block.getBlockHeader().getRawDataOrBuilder().getTimestamp() / 1000;
            /*** NUMBER  op  ***/
            number = block.getBlockHeader().getRawDataOrBuilder().getNumber();
          }
          break;
        default:
          break;
      }

      return new ProgramInvokeImpl(address, origin, caller, balance, callValue, tokenValue, tokenId,
          data,
          lastHash, coinbase, timestamp, number, deposit, vmStartInUs, vmShouldEndInUs,
          energyLimit);
    }
    throw new ContractValidateException("Unknown contract type");
  }

  /**
   * This invocation created for contract call contract
   */
  public static ProgramInvoke createProgramInvoke(Program program, DataWord toAddress,
      DataWord callerAddress,
      DataWord inValue, DataWord tokenValue, DataWord tokenId, long balanceInt, byte[] dataIn,
      Repository deposit, boolean isStaticCall, boolean byTestingSuite, long vmStartInUs,
      long vmShouldEndInUs, long energyLimit) {

    DataWord address = toAddress;
    DataWord origin = program.getOriginAddress();
    DataWord caller = callerAddress;
    DataWord balance = new DataWord(balanceInt);
    DataWord callValue = inValue;

    byte[] data = Arrays.clone(dataIn);
    DataWord lastHash = program.getPrevHash();
    DataWord coinbase = program.getCoinbase();
    DataWord timestamp = program.getTimestamp();
    DataWord number = program.getNumber();
    DataWord difficulty = program.getDifficulty();

    return new ProgramInvokeImpl(address, origin, caller, balance, callValue, tokenValue, tokenId,
        data, lastHash, coinbase, timestamp, number, difficulty,
        deposit, program.getCallDeep() + 1, isStaticCall, byTestingSuite, vmStartInUs,
        vmShouldEndInUs, energyLimit);
  }


}
