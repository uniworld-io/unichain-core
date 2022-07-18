package org.unx.common.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.common.logsfilter.EventPluginLoader;
import org.unx.common.parameter.CommonParameter;
import org.unx.core.ChainBaseManager;
import org.unx.core.config.args.Args;
import org.unx.core.consensus.ConsensusService;
import org.unx.core.db.Manager;
import org.unx.core.metrics.MetricsUtil;
import org.unx.core.net.UnxNetService;
import org.unx.program.FullNode;

@Slf4j(topic = "app")
@Component
public class ApplicationImpl implements Application {

  private ServiceContainer services;

  @Autowired
  private UnxNetService unxNetService;

  @Autowired
  private Manager dbManager;

  @Autowired
  private ChainBaseManager chainBaseManager;

  @Autowired
  private ConsensusService consensusService;

  @Override
  public void setOptions(Args args) {
    // not used
  }

  @Override
  @Autowired
  public void init(CommonParameter parameter) {
    services = new ServiceContainer();
  }

  @Override
  public void addService(Service service) {
    services.add(service);
  }

  @Override
  public void initServices(CommonParameter parameter) {
    services.init(parameter);
  }

  /**
   * start up the app.
   */
  public void startup() {
    unxNetService.start();
    consensusService.start();
    MetricsUtil.init();
  }

  @Override
  public void shutdown() {
    logger.info("******** start to shutdown ********");
    unxNetService.stop();
    consensusService.stop();
    synchronized (dbManager.getRevokingStore()) {
      closeRevokingStore();
      closeAllStore();
    }
    dbManager.stopRePushThread();
    dbManager.stopRePushTriggerThread();
    EventPluginLoader.getInstance().stopPlugin();
    dbManager.stopFilterProcessThread();
    logger.info("******** end to shutdown ********");
    FullNode.shutDownSign = true;
  }

  @Override
  public void startServices() {
    services.start();
  }

  @Override
  public void shutdownServices() {
    services.stop();
  }

  @Override
  public Manager getDbManager() {
    return dbManager;
  }

  @Override
  public ChainBaseManager getChainBaseManager() {
    return chainBaseManager;
  }

  private void closeRevokingStore() {
    logger.info("******** start to closeRevokingStore ********");
    dbManager.getRevokingStore().shutdown();
  }

  private void closeAllStore() {
    dbManager.closeAllStore();
  }

}
