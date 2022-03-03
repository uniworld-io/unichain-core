package org.unichain.core.services.http.fullnode;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.application.Service;
import org.unichain.core.config.args.Args;
import org.unichain.core.services.http.fullnode.servlet.*;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

@Component
@Slf4j(topic = "API")
public class FullNodeHttpApiService implements Service {

  private int port = Args.getInstance().getFullNodeHttpPort();

  private Server server;

  @Autowired
  private ShowFutureDealServlet showFutureDealServlet;

  @Autowired
  private GetAccountServlet getAccountServlet;
  @Autowired
  private TransferServlet transferServlet;
  @Autowired
  private TransferFutureServlet transferFutureServlet;
  @Autowired
  private WithdrawFutureServlet withdrawFutureServlet;
  @Autowired
  private BroadcastServlet broadcastServlet;
  @Autowired
  private TransactionSignServlet transactionSignServlet;
  @Autowired
  private UpdateAccountServlet updateAccountServlet;
  @Autowired
  private VoteWitnessAccountServlet voteWitnessAccountServlet;
  @Autowired
  private CreateAssetIssueServlet createAssetIssueServlet;

  @Autowired
  private CreateNftTemplateServlet createNftTemplateServlet;
  @Autowired
  private RemoveNftMinterServlet removeNftMinterServlet;
  @Autowired
  private MintNftTokenServlet mintNftTokenServlet;
  @Autowired
  private AddNftMinterServlet addNftMinterServlet;
  @Autowired
  private RenounceNftMinterServlet renounceNftMinterServlet;
  @Autowired
  private BurnNftTokenServlet burnNftTokenServlet;
  @Autowired
  private ApproveNftTokenServlet approveNftTokenServlet;
  @Autowired
  private ApproveForAllNftTokenServlet approveForAllNftTokenServlet;
  @Autowired
  private TransferNftTokenServlet transferNftTokenServlet;


  @Autowired
  private CreateTokenServlet createTokenServlet;
  @Autowired
  private TransferTokenOwnerServlet transferTokenOwnerServlet;
  @Autowired
  private ExchangeTokenServlet exchangeTokenServlet;
  @Autowired
  private ContributeTokenPoolFeeServlet contributeTokenPoolFeeServlet;
  @Autowired
  private UpdateTokenParamsServlet updateTokenParamsServlet;
  @Autowired
  private MineTokenServlet mineTokenServlet;
  @Autowired
  private BurnTokenServlet burnTokenServlet;
  @Autowired
  private TransferTokenServlet transferTokenServlet;
  @Autowired
  private WithdrawFutureTokenServlet withdrawFutureTokenServlet;
  @Autowired
  private GetTokenPoolServlet getTokenPoolServlet;

  @Autowired
  private ListNftTemplateServlet listNftTemplateServlet;
  @Autowired
  private ListNftTokenApproveServlet listNftTokenApproveServlet;
  @Autowired
  private ListNftTokenApproveAllServlet listNftTokenApproveAllServlet;
  @Autowired
  private ListNftTokenServlet listNftTokenServlet;
  @Autowired
  private GetNftTemplateServlet getNftTemplateServlet;
  @Autowired
  private GetNftTokenServlet getNftTokenServlet;
  @Autowired
  private GetNftBalanceOfServlet getNftBalanceOfServlet;
  @Autowired
  private GetNftApprovedForAllServlet getNftApprovedForAllServlet;

  @Autowired
  private GetTokenFutureServlet getTokenFutureServlet;
  @Autowired
  private GetFutureTransferServlet getFutureTransferServlet;

  @Autowired
  private UpdateWitnessServlet updateWitnessServlet;
  @Autowired
  private CreateAccountServlet createAccountServlet;
  @Autowired
  private CreateWitnessServlet createWitnessServlet;
  @Autowired
  private TransferAssetServlet transferAssetServlet;
  @Autowired
  private ParticipateAssetIssueServlet participateAssetIssueServlet;
  @Autowired
  private FreezeBalanceServlet freezeBalanceServlet;
  @Autowired
  private UnFreezeBalanceServlet unFreezeBalanceServlet;
  @Autowired
  private UnFreezeAssetServlet unFreezeAssetServlet;
  @Autowired
  private WithdrawBalanceServlet withdrawBalanceServlet;
  @Autowired
  private UpdateAssetServlet updateAssetServlet;
  @Autowired
  private ListNodesServlet listNodesServlet;
  @Autowired
  private GetAssetIssueByAccountServlet getAssetIssueByAccountServlet;
  @Autowired
  private GetAccountNetServlet getAccountNetServlet;
  @Autowired
  private GetAssetIssueByNameServlet getAssetIssueByNameServlet;
  @Autowired
  private GetAssetIssueListByNameServlet getAssetIssueListByNameServlet;
  @Autowired
  private GetAssetIssueByIdServlet getAssetIssueByIdServlet;
  @Autowired
  private GetNowBlockServlet getNowBlockServlet;
  @Autowired
  private GetBlockByNumServlet getBlockByNumServlet;
  @Autowired
  private GetBlockByIdServlet getBlockByIdServlet;
  @Autowired
  private GetBlockByLimitNextServlet getBlockByLimitNextServlet;
  @Autowired
  private GetBlockByLatestNumServlet getBlockByLatestNumServlet;
  @Autowired
  private GetTransactionByIdServlet getTransactionByIdServlet;
  @Autowired
  private GetTransactionInfoByIdServlet getTransactionInfoByIdServlet;
  @Autowired
  private GetTransactionCountByBlockNumServlet getTransactionCountByBlockNumServlet;
  @Autowired
  private ListWitnessesServlet listWitnessesServlet;
  @Autowired
  private GetAssetIssueListServlet getAssetIssueListServlet;
  @Autowired
  private GetPaginatedAssetIssueListServlet getPaginatedAssetIssueListServlet;
  @Autowired
  private GetPaginatedProposalListServlet getPaginatedProposalListServlet;
  @Autowired
  private GetPaginatedExchangeListServlet getPaginatedExchangeListServlet;
  @Autowired
  private TotalTransactionServlet totalTransactionServlet;
  @Autowired
  private GetNextMaintenanceTimeServlet getNextMaintenanceTimeServlet;
  @Autowired
  private EasyTransferServlet easyTransferServlet;
  @Autowired
  private EasyTransferByPrivateServlet easyTransferByPrivateServlet;
  @Autowired
  private EasyTransferAssetServlet easyTransferAssetServlet;
  @Autowired
  private EasyTransferAssetByPrivateServlet easyTransferAssetByPrivateServlet;
  @Autowired
  private CreateAddressServlet createAddressServlet;
  @Autowired
  private GenerateAddressServlet generateAddressServlet;
  @Autowired
  private ValidateAddressServlet validateAddressServlet;
  @Autowired
  private DeployContractServlet deployContractServlet;
  @Autowired
  private TriggerSmartContractServlet triggerSmartContractServlet;
  @Autowired
  private TriggerConstantContractServlet triggerConstantContractServlet;
  @Autowired
  private GetContractServlet getContractServlet;
  @Autowired
  private ClearABIServlet clearABIServlet;
  @Autowired
  private ProposalCreateServlet proposalCreateServlet;
  @Autowired
  private ProposalApproveServlet proposalApproveServlet;
  @Autowired
  private ProposalDeleteServlet proposalDeleteServlet;
  @Autowired
  private ListProposalsServlet listProposalsServlet;
  @Autowired
  private GetProposalByIdServlet getProposalByIdServlet;
  @Autowired
  private ExchangeCreateServlet exchangeCreateServlet;
  @Autowired
  private ExchangeInjectServlet exchangeInjectServlet;
  @Autowired
  private ExchangeTransactionServlet exchangeTransactionServlet;
  @Autowired
  private ExchangeWithdrawServlet exchangeWithdrawServlet;
  @Autowired
  private GetExchangeByIdServlet getExchangeByIdServlet;
  @Autowired
  private ListExchangesServlet listExchangesServlet;
  @Autowired
  private GetChainParametersServlet getChainParametersServlet;
  @Autowired
  private GetAccountResourceServlet getAccountResourceServlet;
  @Autowired
  private GetNodeInfoServlet getNodeInfoServlet;
  @Autowired
  private AddTransactionSignServlet addTransactionSignServlet;
  @Autowired
  private GetTransactionSignWeightServlet getTransactionSignWeightServlet;
  @Autowired
  private GetTransactionApprovedListServlet getTransactionApprovedListServlet;
  @Autowired
  private AccountPermissionUpdateServlet accountPermissionUpdateServlet;
  @Autowired
  private UpdateSettingServlet updateSettingServlet;
  @Autowired
  private UpdateEnergyLimitServlet updateEnergyLimitServlet;
  @Autowired
  private GetDelegatedResourceAccountIndexServlet getDelegatedResourceAccountIndexServlet;
  @Autowired
  private GetDelegatedResourceServlet getDelegatedResourceServlet;
  @Autowired
  private SetAccountIdServlet setAccountServlet;
  @Autowired
  private GetAccountByIdServlet getAccountByIdServlet;
  @Autowired
  private GetBrokerageServlet getBrokerageServlet;
  @Autowired
  private GetRewardServlet getRewardServlet;
  @Autowired
  private UpdateBrokerageServlet updateBrokerageServlet;


  @Override
  public void init() {

  }

  @Override
  public void init(Args args) {
  }

  @Override
  public void start() {
    try {
      server = new Server(port);
      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      context.setContextPath("/wallet/");

      /**
       * Enable CORS
       */
      FilterHolder holder = new FilterHolder(CrossOriginFilter.class);
      holder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
      holder.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
      holder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET, POST, PUT, DELETE, OPTIONS, HEAD");
      holder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
      context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));

      server.setHandler(context);

      //@todo show all future deals
//      context.addServlet(new ServletHolder(showFutureDealServlet), "/showfuturedeal");
      context.addServlet(new ServletHolder(getAccountServlet), "/getaccount");
      context.addServlet(new ServletHolder(getTokenPoolServlet), "/gettokenpool");

      context.addServlet(new ServletHolder(listNftTemplateServlet), "/listnfttemplate");
      context.addServlet(new ServletHolder(listNftTokenServlet), "/listnfttoken");

      context.addServlet(new ServletHolder(listNftTokenApproveServlet), "/listnfttokenapprove");
      context.addServlet(new ServletHolder(listNftTokenApproveAllServlet), "/listnfttokenapproveall");

      context.addServlet(new ServletHolder(getNftTemplateServlet), "/getnfttemplate");
      context.addServlet(new ServletHolder(getNftTokenServlet), "/getnfttoken");
      context.addServlet(new ServletHolder(getNftBalanceOfServlet), "/getnftbalanceOf");
      context.addServlet(new ServletHolder(getNftApprovedForAllServlet), "/getnftapprovedforall");

      context.addServlet(new ServletHolder(getTokenFutureServlet), "/getfuturetoken");
      context.addServlet(new ServletHolder(getFutureTransferServlet), "/getfuturetransfer");
      context.addServlet(new ServletHolder(transferServlet), "/createtransaction");
      context.addServlet(new ServletHolder(transferFutureServlet), "/createfuturetransaction");
      context.addServlet(new ServletHolder(withdrawFutureServlet), "/withdrawfuturetransaction");
      context.addServlet(new ServletHolder(broadcastServlet), "/broadcasttransaction");
      context.addServlet(new ServletHolder(transactionSignServlet), "/gettransactionsign");
      context.addServlet(new ServletHolder(updateAccountServlet), "/updateaccount");
      context.addServlet(new ServletHolder(voteWitnessAccountServlet), "/votewitnessaccount");
      context.addServlet(new ServletHolder(createAssetIssueServlet), "/createassetissue");


      context.addServlet(new ServletHolder(createNftTemplateServlet), "/createnfttemplate");
      context.addServlet(new ServletHolder(removeNftMinterServlet), "/removenftminter");
      context.addServlet(new ServletHolder(mintNftTokenServlet), "/mintnfttoken");
      context.addServlet(new ServletHolder(addNftMinterServlet), "/addnftminter");
      context.addServlet(new ServletHolder(renounceNftMinterServlet), "/renouncenftminter");
      context.addServlet(new ServletHolder(burnNftTokenServlet), "/burnnfttoken");
      context.addServlet(new ServletHolder(approveNftTokenServlet), "/approvenfttoken");
      context.addServlet(new ServletHolder(approveForAllNftTokenServlet), "/approveforallnfttoken");
      context.addServlet(new ServletHolder(transferNftTokenServlet), "/transfernfttoken");

      context.addServlet(new ServletHolder(createTokenServlet), "/createtoken");
      context.addServlet(new ServletHolder(transferTokenOwnerServlet), "/transfertokenowner");
      context.addServlet(new ServletHolder(exchangeTokenServlet), "/exchangetoken");
      context.addServlet(new ServletHolder(contributeTokenPoolFeeServlet), "/contributetokenfee");
      context.addServlet(new ServletHolder(updateTokenParamsServlet), "/updatetokenparams");
      context.addServlet(new ServletHolder(mineTokenServlet), "/minetoken");
      context.addServlet(new ServletHolder(burnTokenServlet), "/burntoken");
      context.addServlet(new ServletHolder(transferTokenServlet), "/transfertoken");
      context.addServlet(new ServletHolder(withdrawFutureTokenServlet), "/withdrawfuturetoken");

      context.addServlet(new ServletHolder(updateWitnessServlet), "/updatewitness");
      context.addServlet(new ServletHolder(createAccountServlet), "/createaccount");
      context.addServlet(new ServletHolder(createWitnessServlet), "/createwitness");
      context.addServlet(new ServletHolder(transferAssetServlet), "/transferasset");
      context.addServlet(new ServletHolder(participateAssetIssueServlet), "/participateassetissue");
      context.addServlet(new ServletHolder(freezeBalanceServlet), "/freezebalance");
      context.addServlet(new ServletHolder(unFreezeBalanceServlet), "/unfreezebalance");
      context.addServlet(new ServletHolder(unFreezeAssetServlet), "/unfreezeasset");
      context.addServlet(new ServletHolder(withdrawBalanceServlet), "/withdrawbalance");
      context.addServlet(new ServletHolder(updateAssetServlet), "/updateasset");
      context.addServlet(new ServletHolder(listNodesServlet), "/listnodes");
      context.addServlet(new ServletHolder(getAssetIssueByAccountServlet), "/getassetissuebyaccount");
      context.addServlet(new ServletHolder(getAccountNetServlet), "/getaccountnet");
      context.addServlet(new ServletHolder(getAssetIssueByNameServlet), "/getassetissuebyname");
      context.addServlet(new ServletHolder(getAssetIssueListByNameServlet), "/getassetissuelistbyname");
      context.addServlet(new ServletHolder(getAssetIssueByIdServlet), "/getassetissuebyid");
      context.addServlet(new ServletHolder(getNowBlockServlet), "/getnowblock");
      context.addServlet(new ServletHolder(getBlockByNumServlet), "/getblockbynum");
      context.addServlet(new ServletHolder(getBlockByIdServlet), "/getblockbyid");
      context.addServlet(new ServletHolder(getBlockByLimitNextServlet), "/getblockbylimitnext");
      context.addServlet(new ServletHolder(getBlockByLatestNumServlet), "/getblockbylatestnum");
      context.addServlet(new ServletHolder(getTransactionByIdServlet), "/gettransactionbyid");

      context.addServlet(new ServletHolder(getTransactionInfoByIdServlet), "/gettransactioninfobyid");
      context.addServlet(new ServletHolder(getTransactionCountByBlockNumServlet), "/gettransactioncountbyblocknum");
      context.addServlet(new ServletHolder(listWitnessesServlet), "/listwitnesses");
      context.addServlet(new ServletHolder(getAssetIssueListServlet), "/getassetissuelist");
      context.addServlet(new ServletHolder(getPaginatedAssetIssueListServlet), "/getpaginatedassetissuelist");
      context.addServlet(new ServletHolder(getPaginatedProposalListServlet), "/getpaginatedproposallist");
      context.addServlet(new ServletHolder(getPaginatedExchangeListServlet), "/getpaginatedexchangelist");
      context.addServlet(new ServletHolder(totalTransactionServlet), "/totaltransaction");
      context.addServlet(new ServletHolder(getNextMaintenanceTimeServlet), "/getnextmaintenancetime");
      context.addServlet(new ServletHolder(createAddressServlet), "/createaddress");
      context.addServlet(new ServletHolder(easyTransferServlet), "/easytransfer");
      context.addServlet(new ServletHolder(easyTransferByPrivateServlet), "/easytransferbyprivate");
      context.addServlet(new ServletHolder(easyTransferAssetServlet), "/easytransferasset");
      context.addServlet(new ServletHolder(easyTransferAssetByPrivateServlet), "/easytransferassetbyprivate");
      context.addServlet(new ServletHolder(generateAddressServlet), "/generateaddress");
      context.addServlet(new ServletHolder(validateAddressServlet), "/validateaddress");
      context.addServlet(new ServletHolder(deployContractServlet), "/deploycontract");
      context.addServlet(new ServletHolder(triggerSmartContractServlet), "/triggersmartcontract");
      context.addServlet(new ServletHolder(triggerConstantContractServlet), "/triggerconstantcontract");
      context.addServlet(new ServletHolder(getContractServlet), "/getcontract");
      context.addServlet(new ServletHolder(clearABIServlet), "/clearabi");
      context.addServlet(new ServletHolder(proposalCreateServlet), "/proposalcreate");
      context.addServlet(new ServletHolder(proposalApproveServlet), "/proposalapprove");
      context.addServlet(new ServletHolder(proposalDeleteServlet), "/proposaldelete");
      context.addServlet(new ServletHolder(listProposalsServlet), "/listproposals");
      context.addServlet(new ServletHolder(getProposalByIdServlet), "/getproposalbyid");
      context.addServlet(new ServletHolder(exchangeCreateServlet), "/exchangecreate");
      context.addServlet(new ServletHolder(exchangeInjectServlet), "/exchangeinject");
      context.addServlet(new ServletHolder(exchangeTransactionServlet), "/exchangetransaction");
      context.addServlet(new ServletHolder(exchangeWithdrawServlet), "/exchangewithdraw");
      context.addServlet(new ServletHolder(getExchangeByIdServlet), "/getexchangebyid");
      context.addServlet(new ServletHolder(listExchangesServlet), "/listexchanges");
      context.addServlet(new ServletHolder(getChainParametersServlet), "/getchainparameters");
      context.addServlet(new ServletHolder(getAccountResourceServlet), "/getaccountresource");
      context.addServlet(new ServletHolder(addTransactionSignServlet), "/addtransactionsign");
      context.addServlet(new ServletHolder(getTransactionSignWeightServlet), "/getsignweight");
      context.addServlet(new ServletHolder(getTransactionApprovedListServlet), "/getapprovedlist");
      context.addServlet(new ServletHolder(accountPermissionUpdateServlet), "/accountpermissionupdate");
      context.addServlet(new ServletHolder(getNodeInfoServlet), "/getnodeinfo");
      context.addServlet(new ServletHolder(updateSettingServlet), "/updatesetting");
      context.addServlet(new ServletHolder(updateEnergyLimitServlet), "/updateenergylimit");
      context.addServlet(new ServletHolder(getDelegatedResourceServlet), "/getdelegatedresource");
      context.addServlet(new ServletHolder(getDelegatedResourceAccountIndexServlet), "/getdelegatedresourceaccountindex");
      context.addServlet(new ServletHolder(setAccountServlet), "/setaccountid");
      context.addServlet(new ServletHolder(getAccountByIdServlet), "/getaccountbyid");
      context.addServlet(new ServletHolder(getBrokerageServlet), "/getBrokerage");
      context.addServlet(new ServletHolder(getRewardServlet), "/getReward");
      context.addServlet(new ServletHolder(updateBrokerageServlet), "/updateBrokerage");

      int maxHttpConnectNumber = Args.getInstance().getMaxHttpConnectNumber();
      if (maxHttpConnectNumber > 0) {
        server.addBean(new ConnectionLimit(maxHttpConnectNumber, server));
      }

      server.start();
    } catch (Exception e) {
      logger.debug("IOException: {}", e.getMessage());
    }
  }

  @Override
  public void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      logger.debug("IOException: {}", e.getMessage());
    }
  }
}
