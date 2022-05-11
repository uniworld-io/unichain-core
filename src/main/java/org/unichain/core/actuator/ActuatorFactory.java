package org.unichain.core.actuator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.db.Manager;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Transaction.Contract;

import java.util.ArrayList;
import java.util.List;

import static org.unichain.core.config.Parameter.ChainConstant.*;

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
    val blockVersion = manager.findBlockVersion(block);
    Assert.isTrue(blockVersion >= 0, "invalid block version, found: " + blockVersion);
    switch (contract.getType()) {
      case AccountUpdateContract:
        return new UpdateAccountActuator(contract.getParameter(), manager);
      case TransferContract:
        return new TransferActuator(contract.getParameter(), manager);
      case FutureTransferContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new TransferFutureActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new TransferFutureActuatorV3(contract.getParameter(), manager);
          default:
            return new TransferFutureActuatorV4(contract.getParameter(), manager);
        }
      case FutureWithdrawContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new WithdrawFutureActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new WithdrawFutureActuatorV3(contract.getParameter(), manager);
          default:
            return new WithdrawFutureActuatorV4(contract.getParameter(), manager);
        }
      case TransferAssetContract:
        return new TransferAssetActuator(contract.getParameter(), manager);
      case VoteAssetContract:
        logger.warn("un-supported VoteAssetContract!");
        return null;
      case VoteWitnessContract:
        return new VoteWitnessActuator(contract.getParameter(), manager);
      case WitnessCreateContract:
        return new WitnessCreateActuator(contract.getParameter(), manager);
      case AccountCreateContract:
        return new CreateAccountActuator(contract.getParameter(), manager);
      case AssetIssueContract:
        return new AssetIssueActuator(contract.getParameter(), manager);
      case CreateTokenContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new TokenCreateActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new TokenCreateActuatorV3(contract.getParameter(), manager);
          case BLOCK_VERSION_4:
            return new TokenCreateActuatorV4(contract.getParameter(), manager);
          default:
            return new TokenCreateActuatorV5(contract.getParameter(), manager);
        }
      case ExchangeTokenContract:
        return new TokenExchangeActuator(contract.getParameter(), manager);
      case TransferTokenOwnerContract:
        return new TokenTransferOwnerActuator(contract.getParameter(), manager);
      case ContributeTokenPoolFeeContract:
        return (blockVersion <= BLOCK_VERSION_2) ?
                new TokenContributePoolFeeActuator(contract.getParameter(), manager) : new TokenContributePoolFeeActuatorV3(contract.getParameter(), manager);
      case UpdateTokenParamsContract:
        switch (blockVersion) {
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new TokenUpdateParamsActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new TokenUpdateParamsActuatorV3(contract.getParameter(), manager);
          default:
            return new TokenUpdateParamsActuatorV4(contract.getParameter(), manager);
        }
      case MineTokenContract:
        return (blockVersion <= BLOCK_VERSION_2) ?
                new TokenMineActuator(contract.getParameter(), manager) : new TokenMineActuatorV3(contract.getParameter(), manager);
      case BurnTokenContract:
        return (blockVersion <= BLOCK_VERSION_2) ?
                new TokenBurnActuator(contract.getParameter(), manager) : new TokenBurnActuatorV3(contract.getParameter(), manager);
      case TransferTokenContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return  new TokenTransferActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new TokenTransferActuatorV3(contract.getParameter(), manager);
          default:
            return new TokenTransferActuatorV4(contract.getParameter(), manager);
        }
      case WithdrawFutureTokenContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new TokenWithdrawFutureActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new TokenWithdrawFutureActuatorV3(contract.getParameter(), manager);
          default:
            return new TokenWithdrawFutureActuatorV4(contract.getParameter(), manager);
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
      case CreateNftTemplateContract:
        switch (blockVersion) {
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
          case BLOCK_VERSION_3:
          case BLOCK_VERSION_4:
            break;
          default:
            return new NftCreateContractActuator(contract.getParameter(), manager);
        }
      case MintNftTokenContract:
        return new NftMintTokenActuator(contract.getParameter(), manager);
      case AddNftMinterContract:
        return new NftAddMinterActuator(contract.getParameter(), manager);
      case RemoveNftMinterContract:
        return new NftRemoveMinterActuator(contract.getParameter(), manager);
      case RenounceNftMinterContract:
        return new NftRenounceMinterActuator(contract.getParameter(), manager);
      case ApproveNftTokenContract:
        return new NftApproveTokenActuator(contract.getParameter(), manager);
      case ApproveForAllNftTokenContract:
        return new NftApproveForAllTokenActuator(contract.getParameter(), manager);
      case BurnNftTokenContract:
        return new NftBurnTokenActuator(contract.getParameter(), manager);
      case TransferNftTokenContract:
        return new NftTransferTokenActuator(contract.getParameter(), manager);

      /**
        POSBridge
       */
      case PosBridgeSetupContract:
        return new PosBridgeSetupActuator(contract.getParameter(), manager);
      case PosBridgeMapTokenContract:
        return new PosBridgeMapTokenActuator(contract.getParameter(), manager);
      case PosBridgeCleanMapTokenContract:
        return new PosBridgeCleanMapTokenActuator(contract.getParameter(), manager);
      case PosBridgeDepositContract:
        return new PosBridgeDepositActuator(contract.getParameter(), manager);
      case PosBridgeDepositExecContract:
        return new PosBridgeDepositExecActuator(contract.getParameter(), manager);
      case PosBridgeWithdrawContract:
        return new PosBridgeWithdrawActuator(contract.getParameter(), manager);
      case PosBridgeWithdrawExecContract:
        return new PosBridgeWithdrawExecActuator(contract.getParameter(), manager);
      default:
        logger.warn("un-supported contract type {}!", contract.getType().name());
        return null;
    }
  }

  public static List<Actuator> createUpgradeActuator(Manager manager, int newBlockVer){
      var actuators = new ArrayList<Actuator>();
      return actuators;
  }
}
