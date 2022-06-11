package org.unichain.program;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.unichain.common.application.Application;
import org.unichain.common.application.ApplicationFactory;
import org.unichain.common.application.UnichainApplicationContext;
import org.unichain.core.Constant;
import org.unichain.core.config.DefaultConfig;
import org.unichain.core.config.args.Args;
import org.unichain.core.services.RpcApiService;
import org.unichain.core.services.WitnessService;
import org.unichain.core.services.http.fullnode.FullNodeHttpApiService;
import org.unichain.core.services.interfaceOnSolidity.HttpApiOnSolidityService;
import org.unichain.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;

import java.io.File;


@Slf4j(topic = "app")
public class FullNode {
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
    Args cfgArgs = Args.getInstance();

    load(cfgArgs.getLogbackPath());

    if (cfgArgs.isHelp()) {
      logger.info("Here is the help message.");
      return;
    }

    if (Args.getInstance().isDebug()) {
      logger.info("In debug mode, it won't check energy time");
    } else {
      logger.info("Not in debug mode, it will check energy time");
    }

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setAllowCircularReferences(false);
    UnichainApplicationContext context = new UnichainApplicationContext(beanFactory);
    context.register(DefaultConfig.class);

    context.refresh();
    Application appT = ApplicationFactory.create(context);
    shutdown(appT);

    // grpc api server
    RpcApiService rpcApiService = context.getBean(RpcApiService.class);
    appT.addService(rpcApiService);
    if (cfgArgs.isWitness()) {
      appT.addService(new WitnessService(appT, context));
    }

    // http api server
    FullNodeHttpApiService httpApiService = context.getBean(FullNodeHttpApiService.class);
    appT.addService(httpApiService);

    // full node and solidity node fuse together, provide solidity rpc and http server on the full node.
    if (Args.getInstance().getStorage().getDbVersion() == 2) {
      RpcApiServiceOnSolidity rpcApiServiceOnSolidity = context.getBean(RpcApiServiceOnSolidity.class);
      appT.addService(rpcApiServiceOnSolidity);
      HttpApiOnSolidityService httpApiOnSolidityService = context.getBean(HttpApiOnSolidityService.class);
      appT.addService(httpApiOnSolidityService);
    }

    appT.initServices(cfgArgs);
    appT.startServices();
    appT.startup();

    System.out.println("     _   _ _ __ (_) ___| |__   __ _(_)_ __  ");
    System.out.println("    | | | | \'_ \\| |/ __| \'_ \\ / _` | | \'_ \\ ");
    System.out.println("    | |_| | | | | | (__| | | | (_| | | | | |");
    System.out.println("     \\__,_|_| |_|_|\\___|_| |_|\\__,_|_|_| |_|");

    rpcApiService.blockUntilShutdown();
  }

  public static void shutdown(final Application app) {
    logger.info("********register application shutdown hook********");
    Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
  }
}
