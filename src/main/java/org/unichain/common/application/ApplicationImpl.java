package org.unichain.common.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.common.logsfilter.EventPluginLoader;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.BlockStore;
import org.unichain.core.db.Manager;
import org.unichain.core.net.UnichainNetService;

@Slf4j(topic = "app")
@Component
public class ApplicationImpl implements Application {

  private BlockStore blockStoreDb;
  private ServiceContainer services;

  @Autowired
  private UnichainNetService unichainNetService;

  @Autowired
  private Manager dbManager;

  private boolean isProducer;

  @Override
  public void setOptions(Args args) {
    // not used
  }

  @Override
  @Autowired
  public void init(Args args) {
    blockStoreDb = dbManager.getBlockStore();
    services = new ServiceContainer();
  }

  @Override
  public void addService(Service service) {
    services.add(service);
  }

  @Override
  public void initServices(Args args) {
    services.init(args);
  }

  /**
   * start up the app.
   */
  public void startup() {
    unichainNetService.start();
  }

  @Override
  public void shutdown() {
    logger.info("******** begin to shutdown ********");
    unichainNetService.close();
    synchronized (dbManager.getRevokingStore()) {
      closeRevokingStore();
      closeAllStore();
    }
    dbManager.stopRePushThread();
    dbManager.stopRePushTriggerThread();
    EventPluginLoader.getInstance().stopPlugins();
    logger.info("******** end to shutdown ********");
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
  public BlockStore getBlockStoreS() {
    return blockStoreDb;
  }

  @Override
  public Manager getDbManager() {
    return dbManager;
  }

  public boolean isProducer() {
    return isProducer;
  }

  public void setIsProducer(boolean producer) {
    isProducer = producer;
  }

  private void closeRevokingStore() {
    logger.info("******** begin to closeRevokingStore ********");
    dbManager.getRevokingStore().shutdown();
  }

  private void closeAllStore() {
//    if (dbManager.getRevokingStore().getClass() == SnapshotManager.class) {
//      ((SnapshotManager) dbManager.getRevokingStore()).getDbs().forEach(IRevokingDB::close);
//    } else {
//      dbManager.closeAllStore();
//    }
    dbManager.closeAllStore();
  }

}
