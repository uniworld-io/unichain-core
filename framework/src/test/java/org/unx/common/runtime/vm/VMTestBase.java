package org.unx.common.runtime.vm;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.Before;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.runtime.Runtime;
import org.unx.common.utils.FileUtil;
import org.unx.core.Constant;
import org.unx.core.Wallet;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.db.Manager;
import org.unx.core.store.StoreFactory;
import org.unx.core.vm.repository.Repository;
import org.unx.core.vm.repository.RepositoryImpl;
import org.unx.protos.Protocol.AccountType;

@Slf4j
public class VMTestBase {

  protected Manager manager;
  protected UnxApplicationContext context;
  protected String dbPath;
  protected Repository rootRepository;
  protected String OWNER_ADDRESS;
  protected Runtime runtime;

  @Before
  public void init() {
    dbPath = "output_" + this.getClass().getName();
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"}, Constant.TEST_CONF);
    context = new UnxApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    manager = context.getBean(Manager.class);
    rootRepository = RepositoryImpl.createRoot(StoreFactory.getInstance());
    rootRepository.createAccount(Hex.decode(OWNER_ADDRESS), AccountType.Normal);
    rootRepository.addBalance(Hex.decode(OWNER_ADDRESS), 30000000000000L);
    rootRepository.commit();
  }

  @After
  public void destroy() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.error("Release resources failure.");
    }
  }

}
