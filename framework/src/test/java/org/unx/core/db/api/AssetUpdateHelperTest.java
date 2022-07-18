package org.unx.core.db.api;

import static org.unx.core.config.Parameter.ChainSymbol.UNW_SYMBOL_BYTES;

import com.google.protobuf.ByteString;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.unx.common.application.Application;
import org.unx.common.application.ApplicationFactory;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.FileUtil;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.ChainBaseManager;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.capsule.AssetIssueCapsule;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.ExchangeCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.protos.Protocol.Account;
import org.unx.protos.Protocol.Exchange;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.contract.AssetIssueContractOuterClass.AssetIssueContract;

public class AssetUpdateHelperTest {

  private static ChainBaseManager chainBaseManager;
  private static UnxApplicationContext context;
  private static String dbPath = "output_AssetUpdateHelperTest_test";
  private static Application AppT;

  private static ByteString assetName = ByteString.copyFrom("assetIssueName".getBytes());

  static {
    Args.setParam(new String[]{"-d", dbPath, "-w"}, "config-test-index.conf");
    Args.getInstance().setSolidityNode(true);
    context = new UnxApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
  }

  @BeforeClass
  public static void init() {

    chainBaseManager = context.getBean(ChainBaseManager.class);

    AssetIssueContract contract =
        AssetIssueContract.newBuilder().setName(assetName).setNum(12581).setPrecision(5).build();
    AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(contract);
    chainBaseManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

    BlockCapsule blockCapsule = new BlockCapsule(1,
        Sha256Hash.wrap(ByteString.copyFrom(
            ByteArray.fromHexString(
                "0000000000000002498b464ac0292229938a342238077182498b464ac0292222"))),
        1234, ByteString.copyFrom("1234567".getBytes()));

    blockCapsule.addTransaction(new TransactionCapsule(contract, ContractType.AssetIssueContract));
    chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(1L);
    chainBaseManager.getBlockIndexStore().put(blockCapsule.getBlockId());
    chainBaseManager.getBlockStore().put(blockCapsule.getBlockId().getBytes(), blockCapsule);

    ExchangeCapsule exchangeCapsule =
        new ExchangeCapsule(
            Exchange.newBuilder()
                .setExchangeId(1L)
                .setFirstTokenId(assetName)
                .setSecondTokenId(ByteString.copyFrom(UNW_SYMBOL_BYTES))
                .build());
    chainBaseManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);

    AccountCapsule accountCapsule =
        new AccountCapsule(
            Account.newBuilder()
                .setAssetIssuedName(assetName)
                .putAsset("assetIssueName", 100)
                .putFreeAssetNetUsage("assetIssueName", 20000)
                .putLatestAssetOperationTime("assetIssueName", 30000000)
                .setAddress(ByteString.copyFrom(ByteArray.fromHexString("121212abc")))
                .build());
    chainBaseManager.getAccountStore().put(ByteArray.fromHexString("121212abc"),
        accountCapsule);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void test() {

    if (chainBaseManager == null) {
      init();
    }
    AssetUpdateHelper assetUpdateHelper = new AssetUpdateHelper(chainBaseManager);
    assetUpdateHelper.init();
    {
      assetUpdateHelper.updateAsset();

      String idNum = "1000001";

      AssetIssueCapsule assetIssueCapsule =
          chainBaseManager.getAssetIssueStore().get(assetName.toByteArray());
      Assert.assertEquals(idNum, assetIssueCapsule.getId());
      Assert.assertEquals(5L, assetIssueCapsule.getPrecision());

      AssetIssueCapsule assetIssueCapsule2 =
          chainBaseManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(idNum)));

      Assert.assertEquals(idNum, assetIssueCapsule2.getId());
      Assert.assertEquals(assetName, assetIssueCapsule2.getName());
      Assert.assertEquals(0L, assetIssueCapsule2.getPrecision());
    }

    {
      assetUpdateHelper.updateExchange();

      try {
        ExchangeCapsule exchangeCapsule =
            chainBaseManager.getExchangeV2Store().get(ByteArray.fromLong(1L));
        Assert.assertEquals("1000001", ByteArray.toStr(exchangeCapsule.getFirstTokenId()));
        Assert.assertEquals("_", ByteArray.toStr(exchangeCapsule.getSecondTokenId()));
      } catch (Exception ex) {
        throw new RuntimeException("testUpdateExchange error");
      }
    }

    {
      assetUpdateHelper.updateAccount();

      AccountCapsule accountCapsule =
          chainBaseManager.getAccountStore().get(ByteArray.fromHexString("121212abc"));

      Assert.assertEquals(
          ByteString.copyFrom(ByteArray.fromString("1000001")), accountCapsule.getAssetIssuedID());

      Assert.assertEquals(1, accountCapsule.getAssetV2MapForTest().size());

      Assert.assertEquals(100L, accountCapsule.getAssetV2MapForTest().get("1000001").longValue());

      Assert.assertEquals(1, accountCapsule.getAllFreeAssetNetUsageV2().size());

      Assert.assertEquals(
          20000L, accountCapsule.getAllFreeAssetNetUsageV2().get("1000001").longValue());

      Assert.assertEquals(1, accountCapsule.getLatestAssetOperationTimeMapV2().size());

      Assert.assertEquals(
          30000000L, accountCapsule.getLatestAssetOperationTimeMapV2().get("1000001").longValue());
    }
  }
}
