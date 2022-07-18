package org.unx.core.jsonrpc;

import com.google.protobuf.ByteString;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.FileUtil;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.Constant;
import org.unx.core.Wallet;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.db.Manager;
import org.unx.core.services.NodeInfoService;
import org.unx.core.services.jsonrpc.JsonRpcImpl;
import org.unx.core.services.jsonrpc.types.BlockResult;
import org.unx.core.services.jsonrpc.types.TransactionResult;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.contract.BalanceContract.TransferContract;


@Slf4j
public class JsonrpcServiceTest {
  private static String dbPath = "output_jsonrpc_service_test";
  private static final String OWNER_ADDRESS;
  private static final String OWNER_ADDRESS_ACCOUNT_NAME = "first";

  private static JsonRpcImpl jsonRpc;
  private static UnxApplicationContext context;
  private static NodeInfoService nodeInfoService;

  private static BlockCapsule blockCapsule;
  private static TransactionCapsule transactionCapsule1;
  private static TransactionCapsule transactionCapsule2;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new UnxApplicationContext(DefaultConfig.class);

    OWNER_ADDRESS =
        Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    nodeInfoService = context.getBean("nodeInfoService", NodeInfoService.class);
  }

  @BeforeClass
  public static void init() {
    Manager dbManager = context.getBean(Manager.class);
    Wallet wallet = context.getBean(Wallet.class);

    AccountCapsule accountCapsule =
        new AccountCapsule(
            ByteString.copyFromUtf8(OWNER_ADDRESS_ACCOUNT_NAME),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
            Protocol.AccountType.Normal,
            10000_000_000L);
    dbManager.getAccountStore().put(accountCapsule.getAddress().toByteArray(), accountCapsule);

    blockCapsule = new BlockCapsule(
        1,
        Sha256Hash.wrap(ByteString.copyFrom(
            ByteArray.fromHexString(
                "0304f784e4e7bae517bcab94c3e0c9214fb4ac7ff9d7d5a937d1f40031f87b81"))),
        1,
        ByteString.copyFromUtf8("testAddress"));
    TransferContract transferContract1 = TransferContract.newBuilder()
        .setAmount(1L)
        .setOwnerAddress(ByteString.copyFrom("0x0000000000000000000".getBytes()))
        .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(
            (Wallet.getAddressPreFixString() + "A389132D6639FBDA4FBC8B659264E6B7C90DB086"))))
        .build();

    TransferContract transferContract2 = TransferContract.newBuilder()
        .setAmount(2L)
        .setOwnerAddress(ByteString.copyFrom("0x0000000000000000000".getBytes()))
        .setToAddress(ByteString.copyFrom(ByteArray.fromHexString(
            (Wallet.getAddressPreFixString() + "ED738B3A0FE390EAA71B768B6D02CDBD18FB207B"))))
        .build();

    transactionCapsule1 =
        new TransactionCapsule(transferContract1, ContractType.TransferContract);
    transactionCapsule1.setBlockNum(blockCapsule.getNum());
    transactionCapsule2 =
        new TransactionCapsule(transferContract2, ContractType.TransferContract);
    transactionCapsule2.setBlockNum(2L);

    blockCapsule.addTransaction(transactionCapsule1);
    blockCapsule.addTransaction(transactionCapsule2);
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(1L);
    dbManager.getBlockIndexStore().put(blockCapsule.getBlockId());
    dbManager.getBlockStore().put(blockCapsule.getBlockId().getBytes(), blockCapsule);

    dbManager.getTransactionStore()
        .put(transactionCapsule1.getTransactionId().getBytes(), transactionCapsule1);
    dbManager.getTransactionStore()
        .put(transactionCapsule2.getTransactionId().getBytes(), transactionCapsule2);

    jsonRpc = new JsonRpcImpl(nodeInfoService, wallet, dbManager);
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
  public void testWeb3Sha3() {
    String result = "";
    try {
      result = jsonRpc.web3Sha3("0x1122334455667788");
    } catch (Exception e) {
      Assert.fail();
    }

    Assert.assertEquals("0x1360118a9c9fd897720cf4e26de80683f402dd7c28e000aa98ea51b85c60161c",
        result);

    try {
      jsonRpc.web3Sha3("1122334455667788");
    } catch (Exception e) {
      Assert.assertEquals("invalid input value", e.getMessage());
    }
  }

  @Test
  public void testGetBlockTransactionCountByHash() {
    try {
      jsonRpc.ethGetBlockTransactionCountByHash("0x111111");
    } catch (Exception e) {
      Assert.assertEquals("invalid hash value", e.getMessage());
    }

    String result = "";
    try {
      result = jsonRpc.ethGetBlockTransactionCountByHash(
          "0x1111111111111111111111111111111111111111111111111111111111111111");
    } catch (Exception e) {
      Assert.fail();
    }
    Assert.assertNull(result);

    try {
      result = jsonRpc.ethGetBlockTransactionCountByHash(
          Hex.toHexString((blockCapsule.getBlockId().getBytes())));
    } catch (Exception e) {
      Assert.fail();
    }
    Assert.assertEquals(ByteArray.toJsonHex(blockCapsule.getTransactions().size()), result);

  }

  @Test
  public void testGetBlockTransactionCountByNumber() {
    String result = "";
    try {
      result = jsonRpc.ethGetBlockTransactionCountByNumber("0x0");
    } catch (Exception e) {
      Assert.assertNull(result);
    }

    try {
      result = jsonRpc.ethGetBlockTransactionCountByNumber("pending");
    } catch (Exception e) {
      Assert.assertEquals("TAG pending not supported", e.getMessage());
    }

    try {
      result = jsonRpc.ethGetBlockTransactionCountByNumber("qqqqq");
    } catch (Exception e) {
      Assert.assertEquals("invalid block number", e.getMessage());
    }

    try {
      result = jsonRpc.ethGetBlockTransactionCountByNumber("latest");
    } catch (Exception e) {
      Assert.fail();
    }
    Assert.assertEquals(ByteArray.toJsonHex(blockCapsule.getTransactions().size()), result);

    try {
      result = jsonRpc
          .ethGetBlockTransactionCountByNumber(ByteArray.toJsonHex(blockCapsule.getNum()));
    } catch (Exception e) {
      Assert.fail();
    }
    Assert.assertEquals(ByteArray.toJsonHex(blockCapsule.getTransactions().size()), result);

  }

  @Test
  public void testGetBlockByHash() {
    BlockResult blockResult = null;
    try {
      blockResult = jsonRpc
          .ethGetBlockByHash(Hex.toHexString((blockCapsule.getBlockId().getBytes())), false);
    } catch (Exception e) {
      Assert.fail();
    }
    Assert.assertEquals(ByteArray.toJsonHex(blockCapsule.getNum()), blockResult.getNumber());
    Assert
        .assertEquals(blockCapsule.getTransactions().size(), blockResult.getTransactions().length);
  }

  @Test
  public void testGetBlockByNumber() {
    BlockResult blockResult = null;
    try {
      blockResult = jsonRpc
          .ethGetBlockByNumber(ByteArray.toJsonHex(blockCapsule.getNum()), false);
    } catch (Exception e) {
      Assert.fail();
    }

    Assert.assertEquals(ByteArray.toJsonHex(blockCapsule.getNum()), blockResult.getNumber());
    Assert
        .assertEquals(blockCapsule.getTransactions().size(), blockResult.getTransactions().length);
    Assert.assertNull(blockResult.getNonce());

  }


  @Test
  public void testGetTransactionByHash() {
    TransactionResult transactionResult = null;
    try {
      transactionResult = jsonRpc.getTransactionByHash(
          "0x1111111111111111111111111111111111111111111111111111111111111111");
    } catch (Exception e) {
      Assert.fail();
    }
    Assert.assertNull(transactionResult);

    try {
      transactionResult = jsonRpc.getTransactionByHash(
          ByteArray.toJsonHex(transactionCapsule1.getTransactionId().getBytes()));
    } catch (Exception e) {
      Assert.fail();
    }
    Assert.assertEquals(ByteArray.toJsonHex(transactionCapsule1.getBlockNum()),
        transactionResult.getBlockNumber());
  }

}