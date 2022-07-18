package org.unx.core.store;

import org.unx.core.ChainBaseManager;

public class StoreFactory {

  private static StoreFactory INSTANCE;

  private ChainBaseManager chainBaseManager;

  private StoreFactory() {

  }

  public static void init() {
    INSTANCE = new StoreFactory();
  }


  public static StoreFactory getInstance() {
    return INSTANCE;
  }


  public ChainBaseManager getChainBaseManager() {
    return chainBaseManager;
  }

  public StoreFactory setChainBaseManager(ChainBaseManager chainBaseManager) {
    this.chainBaseManager = chainBaseManager;
    return this;
  }
}
