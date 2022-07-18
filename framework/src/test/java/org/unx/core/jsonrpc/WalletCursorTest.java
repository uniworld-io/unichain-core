package org.unx.core.jsonrpc;

import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.FileUtil;
import org.unx.core.Constant;
import org.unx.core.Wallet;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.db.Manager;
import org.unx.core.db2.core.Chainbase.Cursor;
import org.unx.core.services.NodeInfoService;
import org.unx.core.services.jsonrpc.JsonRpcImpl;
import org.unx.core.services.jsonrpc.JsonRpcImpl.RequestSource;
import org.unx.core.services.jsonrpc.types.BuildArguments;
import org.unx.protos.Protocol;

@Slf4j
public class WalletCursorTest {
  private static String dbPath = "output_wallet_cursor_test";
  private static final String OWNER_ADDRESS;
  private static final String OWNER_ADDRESS_ACCOUNT_NAME = "first";

  private static UnxApplicationContext context;
  private static Manager dbManager;
  private static Wallet wallet;
  private static NodeInfoService nodeInfoService;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new UnxApplicationContext(DefaultConfig.class);

    OWNER_ADDRESS =
        Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    nodeInfoService = context.getBean("nodeInfoService", NodeInfoService.class);
  }

  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
    wallet = context.getBean(Wallet.class);

    AccountCapsule accountCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            Protocol.AccountType.Normal,
            10000_000_000L);
    dbManager.getAccountStore().put(accountCapsule.getAddress().toByteArray(), accountCapsule);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    context.destroy();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
  }

  @Test
  public void testSource() {
    JsonRpcImpl jsonRpc = new JsonRpcImpl(nodeInfoService, wallet, dbManager);

    Assert.assertEquals(Cursor.HEAD, wallet.getCursor());
    Assert.assertEquals(RequestSource.FULLNODE, jsonRpc.getSource());

    dbManager.setCursor(Cursor.HEAD);
    Assert.assertEquals(Cursor.HEAD, wallet.getCursor());
    Assert.assertEquals(RequestSource.FULLNODE, jsonRpc.getSource());
    dbManager.resetCursor();

    dbManager.setCursor(Cursor.SOLIDITY);
    Assert.assertEquals(Cursor.SOLIDITY, wallet.getCursor());
    Assert.assertEquals(RequestSource.SOLIDITY, jsonRpc.getSource());
    dbManager.resetCursor();

    dbManager.setCursor(Cursor.PBFT);
    Assert.assertEquals(Cursor.PBFT, wallet.getCursor());
    Assert.assertEquals(RequestSource.PBFT, jsonRpc.getSource());
    dbManager.resetCursor();
  }

  @Test
  public void testDisableInSolidity() {
    BuildArguments buildArguments = new BuildArguments();
    buildArguments.setFrom("0xabd4b9367799eaa3197fecb144eb71de1e049abc");
    buildArguments.setTo("0x548794500882809695a8a687866e76d4271a1abc");
    buildArguments.setTokenId(1000016L);
    buildArguments.setTokenValue(20L);

    dbManager.setCursor(Cursor.SOLIDITY);

    JsonRpcImpl unxJsonRpc = new JsonRpcImpl(nodeInfoService, wallet, dbManager);
    try {
      unxJsonRpc.buildTransaction(buildArguments);
    } catch (Exception e) {
      Assert.assertEquals("the method buildTransaction does not exist/is not available in "
          + "SOLIDITY", e.getMessage());
    }

    dbManager.resetCursor();
  }

  @Test
  public void testDisableInPBFT() {
    BuildArguments buildArguments = new BuildArguments();
    buildArguments.setFrom("0xabd4b9367799eaa3197fecb144eb71de1e049abc");
    buildArguments.setTo("0x548794500882809695a8a687866e76d4271a1abc");
    buildArguments.setTokenId(1000016L);
    buildArguments.setTokenValue(20L);

    dbManager.setCursor(Cursor.PBFT);

    JsonRpcImpl unxJsonRpc = new JsonRpcImpl(nodeInfoService, wallet, dbManager);
    try {
      unxJsonRpc.buildTransaction(buildArguments);
    } catch (Exception e) {
      Assert.assertEquals("the method buildTransaction does not exist/is not available in "
          + "PBFT", e.getMessage());
    }

    String method = "test";
    try {
      unxJsonRpc.disableInPBFT(method);
    } catch (Exception e) {
      String expMsg = String.format("the method %s does not exist/is not available in PBFT",
          method);
      Assert.assertEquals(expMsg, e.getMessage());
    }

    dbManager.resetCursor();
  }

  @Test
  public void testEnableInFullNode() {
    BuildArguments buildArguments = new BuildArguments();
    buildArguments.setFrom("0xabd4b9367799eaa3197fecb144eb71de1e049abc");
    buildArguments.setTo("0x548794500882809695a8a687866e76d4271a1abc");
    buildArguments.setValue("0x1f4");

    JsonRpcImpl unxJsonRpc = new JsonRpcImpl(nodeInfoService, wallet, dbManager);

    try {
      unxJsonRpc.buildTransaction(buildArguments);
    } catch (Exception e) {
      Assert.fail();
    }
  }

}