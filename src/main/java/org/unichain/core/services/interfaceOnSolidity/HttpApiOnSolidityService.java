package org.unichain.core.services.interfaceOnSolidity;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.unichain.common.application.Service;
import org.unichain.core.config.args.Args;
import org.unichain.core.services.http.fullnode.servlet.GetFutureTransferServlet;
import org.unichain.core.services.http.fullnode.servlet.urc30.Urc30GetFutureTokenServlet;
import org.unichain.core.services.interfaceOnSolidity.http.*;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

@Slf4j(topic = "API")
public class HttpApiOnSolidityService implements Service {

  private int port = Args.getInstance().getSolidityHttpPort();

  private Server server;

  @Autowired
  private GetAccountOnSolidityServlet accountOnSolidityServlet;
  @Autowired
  private Urc30GetTokenPoolOnSolidityServlet tokenPoolOnSolidityServlet;

  @Autowired
  private Urc721ListContractOnSolidityServlet listNftTemplateOnSolidityServlet;
  @Autowired
  private Urc721ListTokenOnSolidityServlet nftListTokenOnSolidityServlet;
  @Autowired
  private Urc721GetOnSolidityContractServlet getNftTemplateOnSolidityServlet;
  @Autowired
  private Urc721GetTokenOnSolidityServlet getNftTokenOnSolidityServlet;
  @Autowired
  private Urc721BalanceOfOnSolidityServlet getNftBalanceOfOnSolidityServlet;
  @Autowired
  private Urc721GetApprovalOnSolidityServlet getNftApprovedForAllOnSolidityServlet;

  @Autowired
  private Urc721IsApprovedForAllOnSolidityServlet urc721IsApprovedForAllOnSolidityServlet;
  @Autowired
  private Urc721NameOnSolidityServlet urc721NameOnSolidityServlet;
  @Autowired
  private Urc721OwnerOffOnSolidityServlet urc721OwnerOffOnSolidityServlet;
  @Autowired
  private Urc721SymbolOnSolidityServlet urc721SymbolOnSolidityServlet;
  @Autowired
  private Urc721TokenUriOnSolidityServlet urc721TokenUriOnSolidityServlet;
  @Autowired
  private Urc721TotalSupplyOnSolidityServlet urc721TotalSupplyOnSolidityServlet;


  @Autowired
  private Urc30GetFutureTokenServlet urc30GetFutureTokenServlet;
  @Autowired
  private GetFutureTransferServlet getFutureTransferServlet;

  @Autowired
  private GetTransactionByIdOnSolidityServlet getTransactionByIdOnSolidityServlet;
  @Autowired
  private GetTransactionInfoByIdOnSolidityServlet getTransactionInfoByIdOnSolidityServlet;
  @Autowired
  private ListWitnessesOnSolidityServlet listWitnessesOnSolidityServlet;
  @Autowired
  private GetAssetIssueListOnSolidityServlet getAssetIssueListOnSolidityServlet;
  @Autowired
  private GetPaginatedAssetIssueListOnSolidityServlet getPaginatedAssetIssueListOnSolidityServlet;
  @Autowired
  private GetNowBlockOnSolidityServlet getNowBlockOnSolidityServlet;
  @Autowired
  private GetBlockByNumOnSolidityServlet getBlockByNumOnSolidityServlet;

  @Autowired
  private GetNodeInfoOnSolidityServlet getNodeInfoOnSolidityServlet;

  @Autowired
  private GetDelegatedResourceOnSolidityServlet getDelegatedResourceOnSolidityServlet;
  @Autowired
  private GetDelegatedResourceAccountIndexOnSolidityServlet getDelegatedResourceAccountIndexOnSolidityServlet;
  @Autowired
  private GetExchangeByIdOnSolidityServlet getExchangeByIdOnSolidityServlet;
  @Autowired
  private ListExchangesOnSolidityServlet listExchangesOnSolidityServlet;
  @Autowired
  private GetTransactionCountByBlockNumOnSolidityServlet getTransactionCountByBlockNumOnSolidityServlet;
  @Autowired
  private GetAssetIssueByNameOnSolidityServlet getAssetIssueByNameOnSolidityServlet;
  @Autowired
  private GetAssetIssueByIdOnSolidityServlet getAssetIssueByIdOnSolidityServlet;
  @Autowired
  private GetAssetIssueListByNameOnSolidityServlet getAssetIssueListByNameOnSolidityServlet;
  @Autowired
  private GetAccountByIdOnSolidityServlet getAccountByIdOnSolidityServlet;
  @Autowired
  private GetBlockByIdOnSolidityServlet getBlockByIdOnSolidityServlet;
  @Autowired
  private GetBlockByLimitNextOnSolidityServlet getBlockByLimitNextOnSolidityServlet;
  @Autowired
  private GetBlockByLatestNumOnSolidityServlet getBlockByLatestNumOnSolidityServlet;
  @Autowired
  private GetBrokerageOnSolidityServlet getBrokerageServlet;
  @Autowired
  private GetRewardOnSolidityServlet getRewardServlet;
  @Autowired
  private TriggerConstantContractOnSolidityServlet triggerConstantContractOnSolidityServlet;

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
      /**
       * enable cors
       */
      FilterHolder holder = new FilterHolder(CrossOriginFilter.class);
      holder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
      holder.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
      holder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET, POST, PUT, DELETE, OPTIONS, HEAD");
      holder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
      context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));

      context.setContextPath("/");
      server.setHandler(context);

      // same as FullNode
      context.addServlet(new ServletHolder(accountOnSolidityServlet), "/walletsolidity/getaccount");
      context.addServlet(new ServletHolder(tokenPoolOnSolidityServlet), "/walletsolidity/gettokenpool");

      context.addServlet(new ServletHolder(listNftTemplateOnSolidityServlet), "/walletsolidity/urc721listcontract");
      context.addServlet(new ServletHolder(nftListTokenOnSolidityServlet), "/walletsolidity/urc721listtoken");
      context.addServlet(new ServletHolder(getNftTemplateOnSolidityServlet), "/walletsolidity/urc721getcontract");
      context.addServlet(new ServletHolder(getNftTokenOnSolidityServlet), "/walletsolidity/urc721gettoken");
      context.addServlet(new ServletHolder(getNftBalanceOfOnSolidityServlet), "/walletsolidity/urc721balanceof");
      context.addServlet(new ServletHolder(getNftApprovedForAllOnSolidityServlet), "/walletsolidity/urc721getapprovedforall");

      context.addServlet(new ServletHolder(urc721NameOnSolidityServlet), "/walletsolidity/urc721name");
      context.addServlet(new ServletHolder(urc721SymbolOnSolidityServlet), "/walletsolidity/urc721symbol");
      context.addServlet(new ServletHolder(urc721TokenUriOnSolidityServlet), "/walletsolidity/urc721tokenuri");
      context.addServlet(new ServletHolder(urc721TotalSupplyOnSolidityServlet), "/walletsolidity/urc721totalsupply");
      context.addServlet(new ServletHolder(urc721IsApprovedForAllOnSolidityServlet), "/walletsolidity/urc721isapprovedforall");
      context.addServlet(new ServletHolder(urc721OwnerOffOnSolidityServlet), "/walletsolidity/urc721ownerof");

      context.addServlet(new ServletHolder(urc30GetFutureTokenServlet), "/walletsolidity/getfuturetoken");
      context.addServlet(new ServletHolder(getFutureTransferServlet), "/walletsolidity/getfuturetransfer");
      context.addServlet(new ServletHolder(listWitnessesOnSolidityServlet), "/walletsolidity/listwitnesses");
      context.addServlet(new ServletHolder(getAssetIssueListOnSolidityServlet), "/walletsolidity/getassetissuelist");
      context.addServlet(new ServletHolder(getPaginatedAssetIssueListOnSolidityServlet), "/walletsolidity/getpaginatedassetissuelist");
      context.addServlet(new ServletHolder(getAssetIssueByNameOnSolidityServlet), "/walletsolidity/getassetissuebyname");
      context.addServlet(new ServletHolder(getAssetIssueByIdOnSolidityServlet), "/walletsolidity/getassetissuebyid");
      context.addServlet(new ServletHolder(getAssetIssueListByNameOnSolidityServlet), "/walletsolidity/getassetissuelistbyname");
      context.addServlet(new ServletHolder(getNowBlockOnSolidityServlet), "/walletsolidity/getnowblock");
      context.addServlet(new ServletHolder(getBlockByNumOnSolidityServlet), "/walletsolidity/getblockbynum");
      context.addServlet(new ServletHolder(getDelegatedResourceOnSolidityServlet), "/walletsolidity/getdelegatedresource");
      context.addServlet(new ServletHolder(getDelegatedResourceAccountIndexOnSolidityServlet), "/walletsolidity/getdelegatedresourceaccountindex");
      context.addServlet(new ServletHolder(getExchangeByIdOnSolidityServlet), "/walletsolidity/getexchangebyid");
      context.addServlet(new ServletHolder(listExchangesOnSolidityServlet), "/walletsolidity/listexchanges");
      context.addServlet(new ServletHolder(getAccountByIdOnSolidityServlet), "/walletsolidity/getaccountbyid");
      context.addServlet(new ServletHolder(getBlockByIdOnSolidityServlet), "/walletsolidity/getblockbyid");
      context.addServlet(new ServletHolder(getBlockByLimitNextOnSolidityServlet), "/walletsolidity/getblockbylimitnext");
      context.addServlet(new ServletHolder(getBlockByLatestNumOnSolidityServlet), "/walletsolidity/getblockbylatestnum");
      context.addServlet(new ServletHolder(triggerConstantContractOnSolidityServlet), "/walletsolidity/triggerconstantcontract");

      // only for SolidityNode
      context.addServlet(new ServletHolder(getTransactionByIdOnSolidityServlet), "/walletsolidity/gettransactionbyid");
      context.addServlet(new ServletHolder(getTransactionInfoByIdOnSolidityServlet), "/walletsolidity/gettransactioninfobyid");
      context.addServlet(new ServletHolder(getTransactionCountByBlockNumOnSolidityServlet), "/walletsolidity/gettransactioncountbyblocknum");
      context.addServlet(new ServletHolder(getNodeInfoOnSolidityServlet), "/wallet/getnodeinfo");
      context.addServlet(new ServletHolder(getBrokerageServlet), "/walletsolidity/getBrokerage");
      context.addServlet(new ServletHolder(getRewardServlet), "/walletsolidity/getReward");
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
      logger.debug("Exception: {}", e.getMessage());
    }
  }
}
