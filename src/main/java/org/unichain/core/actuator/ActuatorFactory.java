package org.unichain.core.actuator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.db.Manager;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Contract;

import java.util.List;

import static org.unichain.core.config.Parameter.ChainConstant.BLOCK_VERSION;
import static org.unichain.core.config.Parameter.ChainConstant.BLOCK_VERSION_2;

@Slf4j(topic = "actuator")
public class ActuatorFactory {

  public static final ActuatorFactory INSTANCE = new ActuatorFactory();

  private ActuatorFactory() {
  }

  public static ActuatorFactory getInstance() {
    return INSTANCE;
  }

  public static List<Actuator> createActuator(BlockCapsule blockCap, TransactionCapsule txCap, Manager manager) {
    List<Actuator> actuators = Lists.newArrayList();
    if (null == txCap || null == txCap.getInstance()) {
      return actuators;
    }

    Preconditions.checkNotNull(manager, "DB manager is null");
    Protocol.Transaction.raw rawData = txCap.getInstance().getRawData();
    rawData.getContractList().forEach(contract -> actuators.add(getActuatorByContract(blockCap, contract, manager)));
    return actuators;
  }

  private static Actuator getActuatorByContract(final BlockCapsule block, final Contract contract, final Manager manager) {
    final int blockVersion = manager.findBlockVersion(block);
    switch (contract.getType()) {
      case AccountUpdateContract:
        return new UpdateAccountActuator(contract.getParameter(), manager);
      case TransferContract:
        return new TransferActuator(contract.getParameter(), manager);
      case FutureTransferContract:
      {
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TransferFutureActuator(contract.getParameter(), manager);
          default:
            return new TransferFutureActuatorV3(contract.getParameter(), manager);
        }
      }
      case FutureWithdrawContract:{
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new WithdrawFutureActuator(contract.getParameter(), manager);
          default:
            return new WithdrawFutureActuatorV3(contract.getParameter(), manager);
        }
      }
      case TransferAssetContract:
        return new TransferAssetActuator(contract.getParameter(), manager);
      case VoteAssetContract:
        break;
      case VoteWitnessContract:
        return new VoteWitnessActuator(contract.getParameter(), manager);
      case WitnessCreateContract:
        return new WitnessCreateActuator(contract.getParameter(), manager);
      case AccountCreateContract:
        return new CreateAccountActuator(contract.getParameter(), manager);
      case AssetIssueContract:
        return new AssetIssueActuator(contract.getParameter(), manager);
      case CreateTokenContract:{
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TokenCreateActuator(contract.getParameter(), manager);
          default:
            return new TokenCreateActuatorV3(contract.getParameter(), manager);
        }
      }
      case ExchangeTokenContract:
        return new TokenExchangeActuator(contract.getParameter(), manager);
      case TransferTokenOwnerContract:
        return new TokenTransferOwnerActuator(contract.getParameter(), manager);
      case ContributeTokenPoolFeeContract:{
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TokenContributePoolFeeActuator(contract.getParameter(), manager);
          default:
            return new TokenContributePoolFeeActuatorV3(contract.getParameter(), manager);
        }
      }
      case UpdateTokenParamsContract:
      {
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TokenUpdateParamsActuator(contract.getParameter(), manager);
          default:
            return new TokenUpdateParamsActuatorV3(contract.getParameter(), manager);
        }
      }
      case MineTokenContract:
      {
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TokenMineActuator(contract.getParameter(), manager);
          default:
            return new TokenMineActuatorV3(contract.getParameter(), manager);
        }
      }
      case BurnTokenContract:
      {
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TokenBurnActuator(contract.getParameter(), manager);
          default:
            return new TokenBurnActuatorV3(contract.getParameter(), manager);
        }
      }
      case TransferTokenContract: {
        switch (blockVersion) {
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TokenTransferActuator(contract.getParameter(), manager);
          default:
            return new TokenTransferActuatorV3(contract.getParameter(), manager);
        }
      }
      case WithdrawFutureTokenContract:{
        switch (blockVersion){
          case BLOCK_VERSION:
          case BLOCK_VERSION_2:
            return new TokenWithdrawFutureActuator(contract.getParameter(), manager);
          default:
            return new TokenWithdrawFutureActuatorV3(contract.getParameter(), manager);
        }
      }
      case UnfreezeAssetContract:
        return new UnfreezeAssetActuator(contract.getParameter(), manager);
      case WitnessUpdateContract:
        return new WitnessUpdateActuator(contract.getParameter(), manager);
      case ParticipateAssetIssueContract:
        return new ParticipateAssetIssueActuator(contract.getParameter(), manager);
      case FreezeBalanceContract:
        return new FreezeBalanceActuator(contract.getParameter(), manager);
      case UnfreezeBalanceContract:
        return new UnfreezeBalanceActuator(contract.getParameter(), manager);
      case WithdrawBalanceContract:
        return new WithdrawBalanceActuator(contract.getParameter(), manager);
      case UpdateAssetContract:
        return new UpdateAssetActuator(contract.getParameter(), manager);
      case ProposalCreateContract:
        return new ProposalCreateActuator(contract.getParameter(), manager);
      case ProposalApproveContract:
        return new ProposalApproveActuator(contract.getParameter(), manager);
      case ProposalDeleteContract:
        return new ProposalDeleteActuator(contract.getParameter(), manager);
      case SetAccountIdContract:
        return new SetAccountIdActuator(contract.getParameter(), manager);
//      case BuyStorageContract:
//        return new BuyStorageActuator(contract.getParameter(), manager);
//      case BuyStorageBytesContract:
//        return new BuyStorageBytesActuator(contract.getParameter(), manager);
//      case SellStorageContract:
//        return new SellStorageActuator(contract.getParameter(), manager);
      case UpdateSettingContract:
        return new UpdateSettingContractActuator(contract.getParameter(), manager);
      case UpdateEnergyLimitContract:
        return new UpdateEnergyLimitContractActuator(contract.getParameter(), manager);
      case ClearABIContract:
        return new ClearABIContractActuator(contract.getParameter(), manager);
      case ExchangeCreateContract:
        return new ExchangeCreateActuator(contract.getParameter(), manager);
      case ExchangeInjectContract:
        return new ExchangeInjectActuator(contract.getParameter(), manager);
      case ExchangeWithdrawContract:
        return new ExchangeWithdrawActuator(contract.getParameter(), manager);
      case ExchangeTransactionContract:
        return new ExchangeTransactionActuator(contract.getParameter(), manager);
      case AccountPermissionUpdateContract:
        return new AccountPermissionUpdateActuator(contract.getParameter(), manager);
      case UpdateBrokerageContract:
        return new UpdateBrokerageActuator(contract.getParameter(), manager);
      default:
        break;
    }
    return null;
  }

}
