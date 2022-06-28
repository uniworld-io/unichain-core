package org.unichain.core.services.http.solidity;

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
import org.unichain.core.services.http.fullnode.servlet.posbridge.PosBridgeGetConfigServlet;
import org.unichain.core.services.http.fullnode.servlet.posbridge.PosBridgeGetTokenMapServlet;
import org.unichain.core.services.http.fullnode.servlet.urc30.Urc30GetFutureTokenServlet;
import org.unichain.core.services.http.fullnode.servlet.urc30.Urc30GetTokenPoolServlet;
import org.unichain.core.services.http.fullnode.servlet.urc20.*;
import org.unichain.core.services.http.fullnode.servlet.urc721.*;
import org.unichain.core.services.http.solidity.servlet.GetTransactionByIdSolidityServlet;
import org.unichain.core.services.http.solidity.servlet.GetTransactionInfoByIdSolidityServlet;
import org.unichain.core.services.http.solidity.servlet.GetTransactionsFromThisServlet;
import org.unichain.core.services.http.solidity.servlet.GetTransactionsToThisServlet;

import javax.servlet.DispatcherType;
import java.util.EnumSet;


@Component
@Slf4j(topic = "API")
public class SolidityNodeHttpApiService implements Service {

  private int port = Args.getInstance().getSolidityHttpPort();

  private Server server;

  @Autowired
  private GetAccountServlet getAccountServlet;
  @Autowired
  private Urc30GetTokenPoolServlet urc30GetTokenPoolServlet;

  @Autowired
  private PosBridgeGetConfigServlet posBridgeGetConfigServlet;
  @Autowired
  private PosBridgeGetTokenMapServlet posBridgeGetTokenMapServlet;

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

  @Autowired
  private Urc30GetFutureTokenServlet urc30GetFutureTokenServlet;
  @Autowired
  private GetFutureTransferServlet getFutureTransferServlet;

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
  private GetTransactionByIdSolidityServlet getTransactionByIdServlet;
  @Autowired
  private GetTransactionInfoByIdSolidityServlet getTransactionInfoByIdServlet;
  @Autowired
  private GetTransactionsFromThisServlet getTransactionsFromThisServlet;
  @Autowired
  private GetTransactionsToThisServlet getTransactionsToThisServlet;
  @Autowired
  private GetTransactionCountByBlockNumServlet getTransactionCountByBlockNumServlet;
  @Autowired
  private GetDelegatedResourceServlet getDelegatedResourceServlet;
  @Autowired
  private GetDelegatedResourceAccountIndexServlet getDelegatedResourceAccountIndexServlet;
  @Autowired
  private GetExchangeByIdServlet getExchangeByIdServlet;
  @Autowired
  private ListExchangesServlet listExchangesServlet;

  @Autowired
  private ListWitnessesServlet listWitnessesServlet;
  @Autowired
  private GetAssetIssueListServlet getAssetIssueListServlet;
  @Autowired
  private GetPaginatedAssetIssueListServlet getPaginatedAssetIssueListServlet;
  @Autowired
  private GetAssetIssueByNameServlet getAssetIssueByNameServlet;
  @Autowired
  private GetAssetIssueByIdServlet getAssetIssueByIdServlet;
  @Autowired
  private GetAssetIssueListByNameServlet getAssetIssueListByNameServlet;
  @Autowired
  private GetNowBlockServlet getNowBlockServlet;
  @Autowired
  private GetBlockByNumServlet getBlockByNumServlet;
  @Autowired
  private GetNodeInfoServlet getNodeInfoServlet;
  @Autowired
  private GetAccountByIdServlet getAccountByIdServlet;
  @Autowired
  private GetBlockByIdServlet getBlockByIdServlet;
  @Autowired
  private GetBlockByLimitNextServlet getBlockByLimitNextServlet;
  @Autowired
  private GetBlockByLatestNumServlet getBlockByLatestNumServlet;
  @Autowired
  private GetBrokerageServlet getBrokerageServlet;
  @Autowired
  private GetRewardServlet getRewardServlet;
  @Autowired
  private TriggerConstantContractServlet triggerConstantContractServlet;

  @Override
  public void init() {

  }

  @Override
  public void init(Args args) {

  }

  @Override
  public void start() {
    Args args = Args.getInstance();
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

      //same as FullNode
      context.addServlet(new ServletHolder(getAccountServlet), "/walletsolidity/getaccount");
      context.addServlet(new ServletHolder(urc30GetTokenPoolServlet), "/walletsolidity/gettokenpool");

      //posbridge
      context.addServlet(new ServletHolder(posBridgeGetConfigServlet), "/walletsolidity/getposbridgeconfig");
      context.addServlet(new ServletHolder(posBridgeGetTokenMapServlet), "/walletsolidity/getposbridgetokenmap");

      //urc721
      context.addServlet(new ServletHolder(urc721ContractListServlet), "/walletsolidity/urc721contractlist");
      context.addServlet(new ServletHolder(urc721TokenListServlet), "/walletsolidity/urc721tokenlist");
      context.addServlet(new ServletHolder(urc721ContractGetServlet), "/walletsolidity/urc721contractget");
      context.addServlet(new ServletHolder(urc721TokenGetServlet), "/walletsolidity/urc721tokenget");

      context.addServlet(new ServletHolder(urc721BalanceOfServlet), "/walletsolidity/urc721balanceof");
      context.addServlet(new ServletHolder(urc721GetApprovedServlet), "/walletsolidity/urc721getapproved");
      context.addServlet(new ServletHolder(urc721GetApprovedForAllServlet), "/walletsolidity/urc721getapprovedforall");

      context.addServlet(new ServletHolder(urc721NameServlet), "/walletsolidity/urc721name");
      context.addServlet(new ServletHolder(urc721SymbolServlet), "/walletsolidity/urc721symbol");
      context.addServlet(new ServletHolder(urc721TokenUriServlet), "/walletsolidity/urc721tokenuri");
      context.addServlet(new ServletHolder(urc721TotalSupplyServlet), "/walletsolidity/urc721totalsupply");
      context.addServlet(new ServletHolder(urc721IsApprovedForAllServlet), "/walletsolidity/urc721isapprovedforall");
      context.addServlet(new ServletHolder(urc721OwnerOfServlet), "/walletsolidity/urc721ownerof");

      //urc20
      context.addServlet(new ServletHolder(urc20ContractListServlet), "/walletsolidity/urc20contractlist");
      context.addServlet(new ServletHolder(urc20FutureGetServlet), "/walletsolidity/urc20futureget");
      context.addServlet(new ServletHolder(urc20NameServlet), "/walletsolidity/urc20name");
      context.addServlet(new ServletHolder(urc20SymbolServlet), "/walletsolidity/urc20symbol");
      context.addServlet(new ServletHolder(urc20DecimalsServlet), "/walletsolidity/urc20decimals");
      context.addServlet(new ServletHolder(urc20TotalSupplyServlet), "/walletsolidity/urc20totalsupply");
      context.addServlet(new ServletHolder(urc20BalanceOfServlet), "/walletsolidity/urc20balanceof");
      context.addServlet(new ServletHolder(urc20GetOwnerServlet), "/walletsolidity/urc20getowner");
      context.addServlet(new ServletHolder(urc20AllowanceServlet), "/walletsolidity/urc20allowance");


      context.addServlet(new ServletHolder(urc30GetFutureTokenServlet), "/walletsolidity/getfuturetoken");
      context.addServlet(new ServletHolder(getFutureTransferServlet), "/walletsolidity/getfuturetransfer");
      context.addServlet(new ServletHolder(listWitnessesServlet), "/walletsolidity/listwitnesses");
      context.addServlet(new ServletHolder(getAssetIssueListServlet), "/walletsolidity/getassetissuelist");
      context.addServlet(new ServletHolder(getPaginatedAssetIssueListServlet), "/walletsolidity/getpaginatedassetissuelist");
      context.addServlet(new ServletHolder(getAssetIssueByNameServlet), "/walletsolidity/getassetissuebyname");
      context.addServlet(new ServletHolder(getAssetIssueByIdServlet), "/walletsolidity/getassetissuebyid");
      context.addServlet(new ServletHolder(getAssetIssueListByNameServlet), "/walletsolidity/getassetissuelistbyname");
      context.addServlet(new ServletHolder(getNowBlockServlet), "/walletsolidity/getnowblock");
      context.addServlet(new ServletHolder(getBlockByNumServlet), "/walletsolidity/getblockbynum");
      context.addServlet(new ServletHolder(getDelegatedResourceServlet), "/walletsolidity/getdelegatedresource");
      context.addServlet(new ServletHolder(getDelegatedResourceAccountIndexServlet), "/walletsolidity/getdelegatedresourceaccountindex");
      context.addServlet(new ServletHolder(getExchangeByIdServlet), "/walletsolidity/getexchangebyid");
      context.addServlet(new ServletHolder(listExchangesServlet), "/walletsolidity/listexchanges");
      context.addServlet(new ServletHolder(getAccountByIdServlet), "/walletsolidity/getaccountbyid");
      context.addServlet(new ServletHolder(getBlockByIdServlet), "/walletsolidity/getblockbyid");
      context.addServlet(new ServletHolder(getBlockByLimitNextServlet), "/walletsolidity/getblockbylimitnext");
      context.addServlet(new ServletHolder(getBlockByLatestNumServlet), "/walletsolidity/getblockbylatestnum");

      // only for SolidityNode
      context.addServlet(new ServletHolder(getTransactionByIdServlet), "/walletsolidity/gettransactionbyid");
      context.addServlet(new ServletHolder(getTransactionInfoByIdServlet), "/walletsolidity/gettransactioninfobyid");
      context.addServlet(new ServletHolder(getTransactionCountByBlockNumServlet), "/walletsolidity/gettransactioncountbyblocknum");
      context.addServlet(new ServletHolder(triggerConstantContractServlet), "/walletsolidity/triggerconstantcontract");

      // for extension api
      if (args.isWalletExtensionApi()) {
        context.addServlet(new ServletHolder(getTransactionsFromThisServlet), "/walletextension/gettransactionsfromthis");
        context.addServlet(new ServletHolder(getTransactionsToThisServlet), "/walletextension/gettransactionstothis");
      }

      context.addServlet(new ServletHolder(getNodeInfoServlet), "/wallet/getnodeinfo");
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
