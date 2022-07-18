package org.unx.core.services.stop;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.unx.common.parameter.CommonParameter;

@Slf4j
public class BlockHeightStopTest extends ConditionallyStopTest {

  private static final long height = 64;

  protected void initParameter(CommonParameter parameter) {
    parameter.setShutdownBlockHeight(height);
    // will ignore
    parameter.setShutdownBlockCount(128);
  }

  @Override
  protected void check() throws Exception {
    Assert.assertEquals(height, dbManager.getDynamicPropertiesStore()
        .getLatestBlockHeaderNumberFromDB());
  }

  @Override
  protected void initDbPath() {
    dbPath = "output-height-stop";
  }

}
