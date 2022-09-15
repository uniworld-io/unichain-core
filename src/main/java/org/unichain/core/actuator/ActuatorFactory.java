package org.unichain.core.actuator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.actuator.posbridge.*;
import org.unichain.core.actuator.urc30.*;
import org.unichain.core.actuator.urc20.*;
import org.unichain.core.actuator.urc721.*;
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
      case FutureDealTransferContract:
        return new TransferFutureDealActuator(contract.getParameter(), manager);
      case FutureWithdrawContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new WithdrawFutureActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new WithdrawFutureActuatorV3(contract.getParameter(), manager);
          case BLOCK_VERSION_4:
          case BLOCK_VERSION_5:
            return new WithdrawFutureActuatorV4(contract.getParameter(), manager);
          default:
            return new WithdrawFutureActuatorV6(contract.getParameter(), manager);
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
            return new Urc30TokenCreateActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new Urc30TokenCreateActuatorV3(contract.getParameter(), manager);
          case BLOCK_VERSION_4:
            return new Urc30TokenCreateActuatorV4(contract.getParameter(), manager);
          default:
            throw new RuntimeException("current block version do not support this contract anymore!");
        }
      case ExchangeTokenContract:
        return new Urc30TokenExchangeActuator(contract.getParameter(), manager);
      case TransferTokenOwnerContract:
        return new Urc30TokenTransferOwnerActuator(contract.getParameter(), manager);
      case ContributeTokenPoolFeeContract:
        return (blockVersion <= BLOCK_VERSION_2) ?
                new Urc30TokenContributePoolFeeActuator(contract.getParameter(), manager) : new Urc30TokenContributePoolFeeActuatorV3(contract.getParameter(), manager);
      case UpdateTokenParamsContract:
        switch (blockVersion) {
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new Urc30TokenUpdateParamsActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new Urc30TokenUpdateParamsActuatorV3(contract.getParameter(), manager);
          default:
            return new Urc30TokenUpdateParamsActuatorV4(contract.getParameter(), manager);
        }
      case MineTokenContract:
        return (blockVersion <= BLOCK_VERSION_2) ?
                new Urc30TokenMineActuator(contract.getParameter(), manager) : new Urc30TokenMineActuatorV3(contract.getParameter(), manager);
      case BurnTokenContract:
        return (blockVersion <= BLOCK_VERSION_2) ?
                new Urc30TokenBurnActuator(contract.getParameter(), manager) : new Urc30TokenBurnActuatorV3(contract.getParameter(), manager);
      case TransferTokenContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return  new Urc30TokenTransferActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new Urc30TokenTransferActuatorV3(contract.getParameter(), manager);
          default:
            return new Urc30TokenTransferActuatorV4(contract.getParameter(), manager);
        }
      case WithdrawFutureTokenContract:
        switch (blockVersion){
          case BLOCK_VERSION_0:
          case BLOCK_VERSION_1:
          case BLOCK_VERSION_2:
            return new Urc30TokenWithdrawFutureActuator(contract.getParameter(), manager);
          case BLOCK_VERSION_3:
            return new Urc30TokenWithdrawFutureActuatorV3(contract.getParameter(), manager);
          default:
            return new Urc30TokenWithdrawFutureActuatorV4(contract.getParameter(), manager);
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
      /**
       * Urc721
       */
      case Urc721CreateContract:
          return new Urc721CreateContractActuator(contract.getParameter(), manager);
      case Urc721MintContract:
        return new Urc721MintActuator(contract.getParameter(), manager);
      case Urc721AddMinterContract:
        return new Urc721AddMinterActuator(contract.getParameter(), manager);
      case Urc721RemoveMinterContract:
        return new Urc721RemoveMinterActuator(contract.getParameter(), manager);
      case Urc721RenounceMinterContract:
        return new Urc721RenounceMinterActuator(contract.getParameter(), manager);
      case Urc721ApproveContract:
        return new Urc721ApproveActuator(contract.getParameter(), manager);
      case Urc721SetApprovalForAllContract:
        return new Urc721SetApprovalForAllActuator(contract.getParameter(), manager);
      case Urc721BurnContract:
        return new Urc721BurnActuator(contract.getParameter(), manager);
      case Urc721TransferFromContract:
        return new Urc721TransferFromActuator(contract.getParameter(), manager);

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

      /**
       * Urc20
       */
      case Urc20CreateContract:
          return new Urc20CreateContractActuator(contract.getParameter(), manager);
      case Urc20ContributePoolFeeContract:
        return new Urc20ContributePoolFeeActuator(contract.getParameter(), manager);
      case Urc20UpdateParamsContract:
        return new Urc20UpdateParamsActuator(contract.getParameter(), manager);
      case Urc20MintContract:
        return new Urc20MintActuator(contract.getParameter(), manager);
      case Urc20BurnContract:
        return new Urc20BurnActuator(contract.getParameter(), manager);
      case Urc20TransferFromContract:
        return new Urc20TransferFromActuator(contract.getParameter(), manager);
      case Urc20TransferContract:
        return new Urc20TransferActuator(contract.getParameter(), manager);
      case Urc20WithdrawFutureContract:
        return new Urc20WithdrawFutureActuator(contract.getParameter(), manager);
      case Urc20TransferOwnerContract:
        return new Urc20TransferOwnerActuator(contract.getParameter(), manager);
      case Urc20ExchangeContract:
        return new Urc20ExchangeActuator(contract.getParameter(), manager);
      case Urc20ApproveContract:
        return new Urc20ApproveActuator(contract.getParameter(), manager);
      default:
        logger.warn("un-supported contract type {}!", contract.getType().name());
        return null;
    }
  }

  public static List<Actuator> createUpgradeActuator(Manager manager, int newBlockVer, int oldBlockVer){
      var actuators = new ArrayList<Actuator>();
      if((newBlockVer >= BLOCK_VERSION_5) && (oldBlockVer < BLOCK_VERSION_5)){
        actuators.add(new Urc20UpgradeActuator(null, manager));
      }
      return actuators;
  }
}
