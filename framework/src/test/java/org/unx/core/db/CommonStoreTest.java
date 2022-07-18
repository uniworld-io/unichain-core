package org.unx.core.db;

import java.io.File;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.utils.FileUtil;
import org.unx.core.Constant;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;

public class CommonStoreTest {
  private static String dbPath = "output_CommonStore_test";
  private static String dbDirectory = "db_CommonStore_test";
  private static String indexDirectory = "index_CommonStore_test";
  private static UnxApplicationContext context;
  private static CommonStore commonStore;

  static {
    Args.setParam(new String[] {
        "--output-directory", dbPath,
        "--storage-db-directory", dbDirectory,
        "--storage-index-directory", indexDirectory},
        Constant.TEST_CONF
    );
    context = new UnxApplicationContext(DefaultConfig.class);
  }

  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @BeforeClass
  public static void init() {
    commonStore = context.getBean(CommonStore.class);
  }

  @Test
  public void nodeTypeTest() {
    Assert.assertEquals(0, commonStore.getNodeType());
    commonStore.setNodeType(1);
    Assert.assertEquals(1, commonStore.getNodeType());
  }

  @Test
  public void lowestBlockNumTest() {
    Assert.assertEquals(0, commonStore.getLowestBlockNum());
    commonStore.setLowestBlockNum(100);
    Assert.assertEquals(100, commonStore.getLowestBlockNum());
  }
}
