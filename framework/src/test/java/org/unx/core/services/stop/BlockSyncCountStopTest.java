package org.unx.core.services.stop;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.unx.common.parameter.CommonParameter;

@Slf4j
public class BlockSyncCountStopTest extends ConditionallyStopTest {

  private static final long sync = 512;

  protected void initParameter(CommonParameter parameter) {
    parameter.setShutdownBlockCount(sync);
    // will ignore
    parameter.setShutdownBlockHeight(1024);
  }

  @Override
  protected void check() throws Exception {

    Assert.assertEquals(sync + currentHeader, dbManager
        .getDynamicPropertiesStore().getLatestBlockHeaderNumberFromDB());
  }

  @Override
  protected void initDbPath() {
    dbPath = "output-sync-stop";
  }

}
