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
import org.unichain.core.services.http.fullnode.servlet.posbridge.*;
import org.unichain.core.services.http.fullnode.servlet.urc30.*;
import org.unichain.core.services.http.fullnode.servlet.urc20.*;
import org.unichain.core.services.http.fullnode.servlet.urc721.*;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

@Component
@Slf4j(topic = "API")
public class FullNodeHttpApiService implements Service {

  private int port = Args.getInstance().getFullNodeHttpPort();

  private Server server;

  @Autowired
  private GetAccountServlet getAccountServlet;
  @Autowired
  private TransferServlet transferServlet;
  @Autowired
  private TransferFutureServlet transferFutureServlet;
  @Autowired
  private TransferFutureDealServlet transferFutureDealServlet;
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

  /**
   * urc721
   */
  @Autowired
  private Urc721CreateContractServlet urc721CreateContractServlet;
  @Autowired
  private Urc721RemoveMinterServlet urc721RemoveMinterServlet;
  @Autowired
  private Urc721MintServlet urc721MintServlet;
  @Autowired
  private Urc721AddMinterServlet urc721AddMinterServlet;
  @Autowired
  private Urc721RenounceMinterServlet urc721RenounceMinterServlet;
  @Autowired
  private Urc721BurnServlet urc721BurnTokenServlet;
  @Autowired
  private Urc721ApproveServlet urc721ApproveServlet;
  @Autowired
  private Urc721SetApprovalForAllServlet urc721ApprovalForAllServlet;
  @Autowired
  private Urc721TransferFromServlet urc721TransferFromServlet;
  @Autowired
  private Urc721ContractListServlet urc721ContractListServlet;
  @Autowired
  private Urc721TokenListServlet urc721TokenListServlet;
  @Autowired
  private Urc721ContractGetServlet urc721ContractGetServlet;
  @Autowired
  private Urc721TokenGetServlet urc721TokenGetServlet;
  @Autowired
  private Urc721BalanceOfServlet urc721BalanceOfServlet;
  @Autowired
  private Urc721GetApprovedServlet urc721GetApprovedServlet;
  @Autowired
  private Urc721GetApprovedForAllServlet urc721GetApprovedForAllServlet;

  @Autowired
  private Urc721NameServlet urc721NameServlet;
  @Autowired
  private Urc721SymbolServlet urc721SymbolServlet;
  @Autowired
  private Urc721TokenUriServlet urc721TokenUriServlet;
  @Autowired
  private Urc721TotalSupplyServlet urc721TotalSupplyServlet;
  @Autowired
  private Urc721IsApprovedForAllServlet urc721IsApprovedForAllServlet;
  @Autowired
  private Urc721OwnerOfServlet urc721OwnerOfServlet;

  /**
   * urc20
   */
  @Autowired
  private Urc20ContractListServlet urc20ContractListServlet;
  @Autowired
  private Urc20FutureGetServlet urc20FutureGetServlet;
  @Autowired
  private Urc20NameServlet urc20NameServlet;
  @Autowired
  private Urc20SymbolServlet urc20SymbolServlet;
  @Autowired
  private Urc20DecimalsServlet urc20DecimalsServlet;
  @Autowired
  private Urc20TotalSupplyServlet urc20TotalSupplyServlet;
  @Autowired
  private Urc20BalanceOfServlet urc20BalanceOfServlet;
  @Autowired
  private Urc20GetOwnerServlet urc20GetOwnerServlet;
  @Autowired
  private Urc20AllowanceServlet urc20AllowanceServlet;

  @Autowired
  private Urc20CreateContractServlet urc20CreateContractServlet;
  @Autowired
  private Urc20TransferFromServlet urc20TransferFromServlet;
  @Autowired
  private Urc20TransferServlet urc20TransferServlet;

  @Autowired
  private Urc20ApproveServlet urc20ApproveServlet;
  @Autowired
  private Urc20ContributePoolFeeServlet urc20ContributePoolFeeServlet;
  @Autowired
  private Urc20UpdateParamsServlet urc20UpdateParamsServlet;
  @Autowired
  private Urc20MintServlet urc20MintServlet;
  @Autowired
  private Urc20BurnServlet urc20BurnServlet;
  @Autowired
  private Urc20WithdrawFutureServlet urc20WithdrawFutureServlet;
  @Autowired
  private Urc20TransferOwnerServlet urc20TransferOwnerServlet;
  @Autowired
  private Urc20ExchangeServlet urc20ExchangeServlet;

  /**
   * PoSBridge
   */
  @Autowired
  private PosBridgeCleanMapTokenServlet posBridgeCleanMapTokenServlet;
  @Autowired
  private PosBridgeDepositExecServlet posBridgeDepositExecServlet;
  @Autowired
  private PosBridgeDepositServlet posBridgeDepositServlet;
  @Autowired
  private PosBridgeMapTokenServlet posBridgeMapTokenServlet;
  @Autowired
  private PosBridgeSetupServlet posBridgeSetupServlet;
  @Autowired
  private PosBridgeWithdrawExecServlet posBridgeWithdrawExecServlet;
  @Autowired
  private PosBridgeWithdrawServlet posBridgeWithdrawServlet;

  @Autowired
  private PosBridgeGetConfigServlet posBridgeGetConfigServlet;
  @Autowired
  private PosBridgeGetTokenMapServlet posBridgeGetTokenMapServlet;

  /**
   * URC30
   */
  @Autowired
  private Urc30CreateTokenServlet urc30CreateTokenServlet;
  @Autowired
  private Urc30TransferTokenOwnerServlet urc30TransferTokenOwnerServlet;
  @Autowired
  private Urc30ExchangeTokenServlet urc30ExchangeTokenServlet;
  @Autowired
  private Urc30ContributeTokenPoolFeeServlet urc30ContributeTokenPoolFeeServlet;
  @Autowired
  private Urc30UpdateTokenParamsServlet urc30UpdateTokenParamsServlet;
  @Autowired
  private Urc30MineTokenServlet urc30MineTokenServlet;
  @Autowired
  private Urc30BurnTokenServlet urc30BurnTokenServlet;
  @Autowired
  private Urc30TransferTokenServlet urc30TransferTokenServlet;
  @Autowired
  private Urc30WithdrawFutureTokenServlet urc30WithdrawFutureTokenServlet;

  @Autowired
  private Urc30GetTokenPoolServlet urc30GetTokenPoolServlet;
  @Autowired
  private Urc30GetFutureTokenServlet urc30GetFutureTokenServlet;

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

      context.addServlet(new ServletHolder(getAccountServlet), "/getaccount");
      context.addServlet(new ServletHolder(urc30GetTokenPoolServlet), "/gettokenpool");


      context.addServlet(new ServletHolder(urc30GetFutureTokenServlet), "/getfuturetoken");
      context.addServlet(new ServletHolder(getFutureTransferServlet), "/getfuturetransfer");
      context.addServlet(new ServletHolder(transferServlet), "/createtransaction");
      context.addServlet(new ServletHolder(transferFutureServlet), "/createfuturetransaction");
      context.addServlet(new ServletHolder(transferFutureDealServlet), "/createfuturedealtransaction");
      context.addServlet(new ServletHolder(withdrawFutureServlet), "/withdrawfuturetransaction");
      context.addServlet(new ServletHolder(broadcastServlet), "/broadcasttransaction");
      context.addServlet(new ServletHolder(transactionSignServlet), "/gettransactionsign");
      context.addServlet(new ServletHolder(updateAccountServlet), "/updateaccount");
      context.addServlet(new ServletHolder(voteWitnessAccountServlet), "/votewitnessaccount");
      context.addServlet(new ServletHolder(createAssetIssueServlet), "/createassetissue");

      /**
       * urc721
       */
      context.addServlet(new ServletHolder(urc721CreateContractServlet), "/urc721createcontract");
      context.addServlet(new ServletHolder(urc721RemoveMinterServlet), "/urc721removeminter");
      context.addServlet(new ServletHolder(urc721MintServlet), "/urc721mint");
      context.addServlet(new ServletHolder(urc721AddMinterServlet), "/urc721addminter");
      context.addServlet(new ServletHolder(urc721RenounceMinterServlet), "/urc721renounceminter");
      context.addServlet(new ServletHolder(urc721BurnTokenServlet), "/urc721burn");
      context.addServlet(new ServletHolder(urc721ApproveServlet), "/urc721approve");
      context.addServlet(new ServletHolder(urc721ApprovalForAllServlet), "/urc721setapprovalforall");
      context.addServlet(new ServletHolder(urc721TransferFromServlet), "/urc721transferfrom");
      //standard erc721
      context.addServlet(new ServletHolder(urc721BalanceOfServlet), "/urc721balanceof");
      context.addServlet(new ServletHolder(urc721NameServlet), "/urc721name");
      context.addServlet(new ServletHolder(urc721SymbolServlet), "/urc721symbol");
      context.addServlet(new ServletHolder(urc721TokenUriServlet), "/urc721tokenuri");
      context.addServlet(new ServletHolder(urc721TotalSupplyServlet), "/urc721totalsupply");
      context.addServlet(new ServletHolder(urc721IsApprovedForAllServlet), "/urc721isapprovedforall");
      context.addServlet(new ServletHolder(urc721OwnerOfServlet), "/urc721ownerof");

      //extended erc721
      context.addServlet(new ServletHolder(urc721GetApprovedServlet), "/urc721getapproved");
      context.addServlet(new ServletHolder(urc721GetApprovedForAllServlet), "/urc721getapprovedforall");
      context.addServlet(new ServletHolder(urc721ContractListServlet), "/urc721contractlist");
      context.addServlet(new ServletHolder(urc721TokenListServlet), "/urc721tokenlist");
      context.addServlet(new ServletHolder(urc721ContractGetServlet), "/urc721contractget");
      context.addServlet(new ServletHolder(urc721TokenGetServlet), "/urc721tokenget");

      /**
       * POSBridge
       */
      context.addServlet(new ServletHolder(posBridgeSetupServlet), "/posbridgesetup");
      context.addServlet(new ServletHolder(posBridgeMapTokenServlet), "/posbridgemaptoken");
      context.addServlet(new ServletHolder(posBridgeCleanMapTokenServlet), "/posbridgecleanmaptoken");
      context.addServlet(new ServletHolder(posBridgeDepositServlet), "/posbridgedeposit");
      context.addServlet(new ServletHolder(posBridgeDepositExecServlet), "/posbridgedepositexec");
      context.addServlet(new ServletHolder(posBridgeWithdrawServlet), "/posbridgewithdraw");
      context.addServlet(new ServletHolder(posBridgeWithdrawExecServlet), "/posbridgewithdrawexec");
      context.addServlet(new ServletHolder(posBridgeGetConfigServlet), "/getposbridgeconfig");
      context.addServlet(new ServletHolder(posBridgeGetTokenMapServlet), "/getposbridgetokenmap");

      /**
       * Urc20
       */
      context.addServlet(new ServletHolder(urc20CreateContractServlet), "/urc20createcontract");
      context.addServlet(new ServletHolder(urc20TransferFromServlet), "/urc20transferfrom");
      context.addServlet(new ServletHolder(urc20TransferServlet), "/urc20transfer");
      context.addServlet(new ServletHolder(urc20ApproveServlet), "/urc20approve");
      context.addServlet(new ServletHolder(urc20ContributePoolFeeServlet), "/urc20contributepoolfee");
      context.addServlet(new ServletHolder(urc20UpdateParamsServlet), "/urc20updateparams");
      context.addServlet(new ServletHolder(urc20MintServlet), "/urc20mint");
      context.addServlet(new ServletHolder(urc20BurnServlet), "/urc20burn");
      context.addServlet(new ServletHolder(urc20WithdrawFutureServlet), "/urc20withdrawfuture");
      context.addServlet(new ServletHolder(urc20TransferOwnerServlet), "/urc20transferowner");
      context.addServlet(new ServletHolder(urc20ExchangeServlet), "/urc20exchange");

      context.addServlet(new ServletHolder(urc20ContractListServlet), "/urc20contractlist");
      context.addServlet(new ServletHolder(urc20FutureGetServlet), "/urc20futureget");
      context.addServlet(new ServletHolder(urc20NameServlet), "/urc20name");
      context.addServlet(new ServletHolder(urc20SymbolServlet), "/urc20symbol");
      context.addServlet(new ServletHolder(urc20DecimalsServlet), "/urc20decimals");
      context.addServlet(new ServletHolder(urc20TotalSupplyServlet), "/urc20totalsupply");
      context.addServlet(new ServletHolder(urc20BalanceOfServlet), "/urc20balanceof");
      context.addServlet(new ServletHolder(urc20GetOwnerServlet), "/urc20getowner");
      context.addServlet(new ServletHolder(urc20AllowanceServlet), "/urc20allowance");

      context.addServlet(new ServletHolder(urc30CreateTokenServlet), "/createtoken");
      context.addServlet(new ServletHolder(urc30TransferTokenOwnerServlet), "/transfertokenowner");
      context.addServlet(new ServletHolder(urc30ExchangeTokenServlet), "/exchangetoken");
      context.addServlet(new ServletHolder(urc30ContributeTokenPoolFeeServlet), "/contributetokenfee");
      context.addServlet(new ServletHolder(urc30UpdateTokenParamsServlet), "/updatetokenparams");
      context.addServlet(new ServletHolder(urc30MineTokenServlet), "/minetoken");
      context.addServlet(new ServletHolder(urc30BurnTokenServlet), "/burntoken");
      context.addServlet(new ServletHolder(urc30TransferTokenServlet), "/transfertoken");
      context.addServlet(new ServletHolder(urc30WithdrawFutureTokenServlet), "/withdrawfuturetoken");

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
