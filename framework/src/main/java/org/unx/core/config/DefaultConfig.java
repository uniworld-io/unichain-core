package org.unx.core.config;

import com.alibaba.fastjson.parser.ParserConfig;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.unx.common.utils.StorageUtils;
import org.unx.core.config.args.Args;
import org.unx.core.db.RecentTransactionStore;
import org.unx.core.db.RevokingDatabase;
import org.unx.core.db.RevokingStore;
import org.unx.core.db.TransactionCache;
import org.unx.core.db.backup.BackupRocksDBAspect;
import org.unx.core.db.backup.NeedBeanCondition;
import org.unx.core.db2.core.SnapshotManager;
import org.unx.core.services.interfaceOnPBFT.RpcApiServiceOnPBFT;
import org.unx.core.services.interfaceOnPBFT.http.PBFT.HttpApiOnPBFTService;
import org.unx.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;
import org.unx.core.services.interfaceOnSolidity.http.solidity.HttpApiOnSolidityService;

@Slf4j(topic = "app")
@Configuration
@Import(CommonConfig.class)
public class DefaultConfig {

  static {
    RocksDB.loadLibrary();
    ParserConfig.getGlobalInstance().setSafeMode(true);
  }

  @Autowired
  public ApplicationContext appCtx;

  @Autowired
  public CommonConfig commonConfig;

  public DefaultConfig() {
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
  }

  @Bean
  public RevokingDatabase revokingDatabase() {
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    RevokingDatabase revokingDatabase;
    try {
      if (dbVersion == 1) {
        revokingDatabase = RevokingStore.getInstance();
      } else if (dbVersion == 2) {
        revokingDatabase = new SnapshotManager(
            StorageUtils.getOutputDirectoryByDbName("block"));
      } else {
        throw new RuntimeException("db version is error.");
      }
      return revokingDatabase;
    } finally {
      logger.info("key-value data source created.");
    }
  }


  @Bean
  public RpcApiServiceOnSolidity getRpcApiServiceOnSolidity() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new RpcApiServiceOnSolidity();
    }

    return null;
  }

  @Bean
  public HttpApiOnSolidityService getHttpApiOnSolidityService() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new HttpApiOnSolidityService();
    }

    return null;
  }

  @Bean
  public RpcApiServiceOnPBFT getRpcApiServiceOnPBFT() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new RpcApiServiceOnPBFT();
    }

    return null;
  }

  @Bean
  public HttpApiOnPBFTService getHttpApiOnPBFTService() {
    boolean isSolidityNode = Args.getInstance().isSolidityNode();
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (!isSolidityNode && dbVersion == 2) {
      return new HttpApiOnPBFTService();
    }

    return null;
  }

  @Bean
  public TransactionCache transactionCache() {
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (dbVersion == 2) {
      return new TransactionCache("trans-cache", appCtx.getBean(RecentTransactionStore.class));
    }
    return null;
  }

  @Bean
  @Conditional(NeedBeanCondition.class)
  public BackupRocksDBAspect backupRocksDBAspect() {
    return new BackupRocksDBAspect();
  }
}
