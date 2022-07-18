package org.unx.program;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.unx.common.application.Application;
import org.unx.common.application.ApplicationFactory;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.prometheus.Metrics;
import org.unx.core.Constant;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.services.RpcApiService;
import org.unx.core.services.http.FullNodeHttpApiService;
import org.unx.core.services.interfaceJsonRpcOnPBFT.JsonRpcServiceOnPBFT;
import org.unx.core.services.interfaceJsonRpcOnSolidity.JsonRpcServiceOnSolidity;
import org.unx.core.services.interfaceOnPBFT.RpcApiServiceOnPBFT;
import org.unx.core.services.interfaceOnPBFT.http.PBFT.HttpApiOnPBFTService;
import org.unx.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;
import org.unx.core.services.interfaceOnSolidity.http.solidity.HttpApiOnSolidityService;
import org.unx.core.services.jsonrpc.FullNodeJsonRpcHttpService;

@Slf4j(topic = "app")
public class FullNode {

  public static final int dbVersion = 2;

  public static volatile boolean shutDownSign = false;

  public static void load(String path) {
    try {
      File file = new File(path);
      if (!file.exists() || !file.isFile() || !file.canRead()) {
        return;
      }
      LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(lc);
      lc.reset();
      configurator.doConfigure(file);
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  /**
   * Start the FullNode.
   */
  public static void main(String[] args) {
    logger.info("Full node running.");
    Args.setParam(args, Constant.TESTNET_CONF);
    CommonParameter parameter = Args.getInstance();

    load(parameter.getLogbackPath());

    if (parameter.isHelp()) {
      logger.info("Here is the help message.");
      return;
    }

    if (Args.getInstance().isDebug()) {
      logger.info("in debug mode, it won't check energy time");
    } else {
      logger.info("not in debug mode, it will check energy time");
    }

    // init metrics first
    Metrics.init();

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setAllowCircularReferences(false);
    UnxApplicationContext context =
        new UnxApplicationContext(beanFactory);
    context.register(DefaultConfig.class);

    context.refresh();
    Application appT = ApplicationFactory.create(context);
    shutdown(appT);

    // grpc api server
    RpcApiService rpcApiService = context.getBean(RpcApiService.class);
    appT.addService(rpcApiService);

    // http api server
    FullNodeHttpApiService httpApiService = context.getBean(FullNodeHttpApiService.class);
    if (CommonParameter.getInstance().fullNodeHttpEnable) {
      appT.addService(httpApiService);
    }

    // JSON-RPC http server
    if (CommonParameter.getInstance().jsonRpcHttpFullNodeEnable) {
      FullNodeJsonRpcHttpService jsonRpcHttpService =
          context.getBean(FullNodeJsonRpcHttpService.class);
      appT.addService(jsonRpcHttpService);
    }

    // full node and solidity node fuse together
    // provide solidity rpc and http server on the full node.
    if (CommonParameter.getInstance().getStorage().getDbVersion() == dbVersion) {
      RpcApiServiceOnSolidity rpcApiServiceOnSolidity = context
          .getBean(RpcApiServiceOnSolidity.class);
      appT.addService(rpcApiServiceOnSolidity);
      HttpApiOnSolidityService httpApiOnSolidityService = context
          .getBean(HttpApiOnSolidityService.class);
      if (CommonParameter.getInstance().solidityNodeHttpEnable) {
        appT.addService(httpApiOnSolidityService);
      }

      // JSON-RPC on solidity
      if (CommonParameter.getInstance().jsonRpcHttpSolidityNodeEnable) {
        JsonRpcServiceOnSolidity jsonRpcServiceOnSolidity = context
            .getBean(JsonRpcServiceOnSolidity.class);
        appT.addService(jsonRpcServiceOnSolidity);
      }
    }

    // PBFT API (HTTP and GRPC)
    if (CommonParameter.getInstance().getStorage().getDbVersion() == dbVersion) {
      RpcApiServiceOnPBFT rpcApiServiceOnPBFT = context
          .getBean(RpcApiServiceOnPBFT.class);
      appT.addService(rpcApiServiceOnPBFT);
      HttpApiOnPBFTService httpApiOnPBFTService = context
          .getBean(HttpApiOnPBFTService.class);
      appT.addService(httpApiOnPBFTService);

      // JSON-RPC on PBFT
      if (CommonParameter.getInstance().jsonRpcHttpPBFTNodeEnable) {
        JsonRpcServiceOnPBFT jsonRpcServiceOnPBFT = context.getBean(JsonRpcServiceOnPBFT.class);
        appT.addService(jsonRpcServiceOnPBFT);
      }
    }

    appT.initServices(parameter);
    appT.startServices();
    appT.startup();

    rpcApiService.blockUntilShutdown();
  }

  public static void shutdown(final Application app) {
    logger.info("********register application shutdown hook********");
    Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
  }
}
