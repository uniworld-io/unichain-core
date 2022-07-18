package org.unx.core.db;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.FileUtil;
import org.unx.core.ChainBaseManager;
import org.unx.core.Constant;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.db2.ISession;
import org.unx.core.store.AccountAssetStore;
import org.unx.core.store.AccountStore;
import org.unx.core.store.AssetIssueStore;
import org.unx.core.store.DynamicPropertiesStore;
import org.unx.protos.Protocol.AccountType;

public class AccountStoreTest {

  private static final byte[] data = TransactionStoreTest.randomBytes(32);
  private static String dbPath = "output_AccountStore_test";
  private static String dbDirectory = "db_AccountStore_test";
  private static String indexDirectory = "index_AccountStore_test";
  private static UnxApplicationContext context;
  private static AccountStore accountStore;
  private static AccountAssetStore accountAssetStore;
  private static Manager manager;
  private static DynamicPropertiesStore dynamicPropertiesStore;
  private static AssetIssueStore assetIssueStore;
  private static ChainBaseManager chainBaseManager;
  private static byte[] address = TransactionStoreTest.randomBytes(32);
  private static byte[] accountName = TransactionStoreTest.randomBytes(32);

  static {
    Args.setParam(
        new String[]{
            "--output-directory", dbPath,
            "--storage-db-directory", dbDirectory,
            "--storage-index-directory", indexDirectory
        },
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
    accountStore = context.getBean(AccountStore.class);
    accountAssetStore = context.getBean(AccountAssetStore.class);
    dynamicPropertiesStore = context.getBean(DynamicPropertiesStore.class);
    manager = context.getBean(Manager.class);
    chainBaseManager = context.getBean(ChainBaseManager.class);
    assetIssueStore = chainBaseManager.getAssetIssueStore();
    dynamicPropertiesStore.saveAllowBlackHoleOptimization(1);
    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom(address),
        ByteString.copyFrom(accountName),
        AccountType.forNumber(1));

    accountStore.put(data, accountCapsule);
  }

  @Test
  public void get() {
    //test get and has Method
    Assert
        .assertEquals(ByteArray.toHexString(address), ByteArray
            .toHexString(accountStore.get(data).getInstance().getAddress().toByteArray()))
    ;
    Assert
        .assertEquals(ByteArray.toHexString(accountName), ByteArray
            .toHexString(accountStore.get(data).getInstance().getAccountName().toByteArray()))
    ;
    Assert.assertTrue(accountStore.has(data));
  }

  @Test
  public void put() {
    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom(address),
            ByteString.copyFrom(accountName),
            AccountType.forNumber(1));
    accountStore.put(data, accountCapsule);
  }

  @Test
  public void assetTest() {

    dynamicPropertiesStore.setAllowAssetOptimization(1);
    dynamicPropertiesStore.saveAllowSameTokenName(1);
    AccountCapsule accountCapsule = new AccountCapsule(ByteString.copyFrom(address),
            ByteString.copyFrom(accountName),
            AccountType.forNumber(1));
    accountCapsule.addAsset("100".getBytes(), 1);
    accountCapsule.addAssetAmountV2("100".getBytes(), 1,
            dynamicPropertiesStore, assetIssueStore);
    accountCapsule.addAssetAmountV2("200".getBytes(), 1,
            dynamicPropertiesStore, assetIssueStore);
    accountCapsule = saveAccount(accountCapsule);
    assertEquals(0, accountCapsule.getAssetMap().size());
    assertEquals(1, accountCapsule.getAssetV2("100"));
    assertEquals(1, accountCapsule.getAssetV2("200"));
    assertEquals(2, accountCapsule.getAssetMapV2().size());
    assertEquals(0, accountCapsule.getAssetV2("300"));

    accountCapsule.clearAsset();
    accountCapsule.reduceAssetAmountV2("100".getBytes(), 1,
            dynamicPropertiesStore, assetIssueStore);
    accountCapsule = saveAccount(accountCapsule);
    assertEquals(1, accountCapsule.getAssetMapV2().size());
    assertEquals(0, accountCapsule.getAssetV2("100"));
    assertEquals(1, accountCapsule.getAssetV2("200"));
    assertEquals(0, accountCapsule.getAssetV2("300"));
    assertEquals(3, accountCapsule.getAssetMapV2().size());

    accountCapsule.clearAsset();
    accountStore.delete(accountCapsule.createDbKey());
    accountCapsule = saveAccount(accountCapsule);
    assertEquals(0, accountCapsule.getAssetMapV2().size());

    Map<String, Long> map = new HashMap<>();
    map.put("100", 100L);
    map.put("200", 100L);
    accountCapsule.addAssetMapV2(map);
    accountCapsule = saveAccount(accountCapsule);
    assertEquals(100, accountCapsule.getAssetV2("100"));
    assertEquals(100, accountCapsule.getAssetV2("200"));

    accountCapsule.clearAsset();
    Map<String, Long> assets = accountCapsule.getAssetV2MapForTest();
    assertEquals(2, assets.size());
    assertEquals(100, (long)assets.get("100"));
    assertEquals(100, (long)assets.get("200"));

    accountCapsule.clearAsset();
    try (ISession tmpSession = manager.getRevokingStore().buildSession()) {
      accountCapsule.addAssetAmountV2("100".getBytes(), 1,
              dynamicPropertiesStore, assetIssueStore);
      accountCapsule.reduceAssetAmountV2("200".getBytes(), 1,
              dynamicPropertiesStore, assetIssueStore);
      accountCapsule = saveAccount(accountCapsule);
      tmpSession.commit();
    }
    assertEquals(101, accountCapsule.getAssetV2("100"));
    assertEquals(99, accountCapsule.getAssetV2("200"));

    try (ISession tmpSession = manager.getRevokingStore().buildSession()) {
      tmpSession.commit();
    }

    try (ISession tmpSession = manager.getRevokingStore().buildSession()) {
      accountCapsule.reduceAssetAmountV2("200".getBytes(), 89,
              dynamicPropertiesStore, assetIssueStore);
      accountCapsule.addAssetAmountV2("300".getBytes(), 10,
              dynamicPropertiesStore, assetIssueStore);
      accountCapsule = saveAccount(accountCapsule);
      tmpSession.commit();
    }
    assets = accountCapsule.getAssetV2MapForTest();
    assertEquals(3, assets.size());
    assertEquals(101, (long)assets.get("100"));
    assertEquals(10, (long)assets.get("200"));
    assertEquals(10, (long)assets.get("300"));

    try (ISession tmpSession = manager.getRevokingStore().buildSession()) {
      accountCapsule.reduceAssetAmountV2("100".getBytes(), 91,
              dynamicPropertiesStore, assetIssueStore);
      accountCapsule.addAssetAmountV2("200".getBytes(), 0,
              dynamicPropertiesStore, assetIssueStore);
      accountCapsule.addAssetAmountV2("400".getBytes(), 10,
              dynamicPropertiesStore, assetIssueStore);
      accountCapsule = saveAccount(accountCapsule);
      tmpSession.commit();
    }
    assets = accountCapsule.getAssetV2MapForTest();
    assertEquals(4, assets.size());
    assertEquals(10, (long)assets.get("100"));
    assertEquals(10, (long)assets.get("200"));
    assertEquals(10, (long)assets.get("300"));
    assertEquals(10, (long)assets.get("400"));
  }

  private AccountCapsule saveAccount(AccountCapsule accountCapsule) {
    accountStore.put(accountCapsule.createDbKey(), accountCapsule);
    accountCapsule = accountStore.get(accountCapsule.createDbKey());
    return accountCapsule;
  }

}
