package org.unichain.core.config;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.BooleanUtils;
import org.rocksdb.RocksDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.RevokingDatabase;
import org.unichain.core.db.TransactionCache;
import org.unichain.core.db.api.IndexHelper;
import org.unichain.core.db.backup.BackupRocksDBAspect;
import org.unichain.core.db.backup.NeedBeanCondition;
import org.unichain.core.db2.core.SnapshotManager;
import org.unichain.core.services.interfaceOnSolidity.HttpApiOnSolidityService;
import org.unichain.core.services.interfaceOnSolidity.RpcApiServiceOnSolidity;

@Slf4j(topic = "app")
@Configuration
@Import(CommonConfig.class)
public class DefaultConfig {

  static {
    RocksDB.loadLibrary();
  }

  @Autowired
  public ApplicationContext appCtx;

  @Autowired
  public CommonConfig commonConfig;

  public DefaultConfig() {
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
  }

  @Bean
  public IndexHelper indexHelper() {
    if (Args.getInstance().isSolidityNode()
        && BooleanUtils.toBoolean(Args.getInstance().getStorage().getIndexSwitch())) {
      return new IndexHelper();
    }
    return null;
  }

  @Bean
  public RevokingDatabase revokingDatabase() {
    var dbVersion = Args.getInstance().getStorage().getDbVersion();
    RevokingDatabase revokingDatabase;
    try {
      if (dbVersion == 2) {
        revokingDatabase = new SnapshotManager();
      } else {
        throw new RuntimeException("DB version 2 only, found: "+ dbVersion);
      }
      return revokingDatabase;
    } finally {
      logger.info("Key-value data source created.");
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
  public TransactionCache transactionCache() {
    int dbVersion = Args.getInstance().getStorage().getDbVersion();
    if (dbVersion == 2) {
      return new TransactionCache("trans-cache");
    }

    return null;
  }

  @Bean
  @Conditional(NeedBeanCondition.class)
  public BackupRocksDBAspect backupRocksDBAspect() {
    return new BackupRocksDBAspect();
  }
}
