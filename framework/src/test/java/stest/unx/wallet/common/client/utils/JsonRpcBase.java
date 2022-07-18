package stest.unx.wallet.common.client.utils;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.HashMap;
import java.util.Optional;
// import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.unx.api.GrpcAPI;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.api.GrpcAPI.EmptyMessage;
import org.unx.api.GrpcAPI.Note;
import org.unx.api.GrpcAPI.ShieldedURC20Parameters;
import org.unx.api.GrpcAPI.TransactionExtention;
import org.unx.api.WalletGrpc;
import org.unx.api.WalletSolidityGrpc;
import org.unx.common.utils.ByteArray;
import org.unx.core.Wallet;
import org.unx.core.services.http.Util;
import org.unx.protos.Protocol.TransactionInfo;
import org.unx.protos.contract.ShieldContract;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.Parameter.CommonConstant;

@Slf4j
public class JsonRpcBase {

  public final String foundationAccountKey =
      Configuration.getByPath("testng.conf").getString("foundationAccount.key1");
  public final byte[] foundationAccountAddress = PublicMethed.getFinalAddress(foundationAccountKey);

  public static final String jsonRpcOwnerKey =
      Configuration.getByPath("testng.conf").getString("defaultParameter.jsonRpcOwnerKey");
  public static final byte[] jsonRpcOwnerAddress = PublicMethed.getFinalAddress(jsonRpcOwnerKey);
  public static final String jsonRpcOwnerAddressString =
      PublicMethed.getAddressString(jsonRpcOwnerKey);
  public static String jsonRpcNode =
      Configuration.getByPath("testng.conf").getStringList("jsonRpcNode.ip.list").get(0);
  public static String jsonRpcNodeForSolidity =
      Configuration.getByPath("testng.conf").getStringList("jsonRpcNode.ip.list").get(1);
  public static String httpFullNode =
      Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list").get(0);
  public static String httpsolidityNode =
      Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list").get(3);
  public static String ethHttpsNode =
      Configuration.getByPath("testng.conf").getStringList("ethHttpsNode.host.list").get(0);

  public ManagedChannel channelFull = null;
  public WalletGrpc.WalletBlockingStub blockingStubFull = null;
  public ManagedChannel channelSolidity = null;
  public ManagedChannel channelPbft = null;
  public static String data = null;
  public String paramString = null;
  public WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  public WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubPbft = null;
  public String fullnode =
      Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list").get(0);

  public static long maxFeeLimit =
      Configuration.getByPath("testng.conf").getLong("defaultParameter.maxFeeLimit");
  public static String urc20AddressByteString;
  public static String urc20AddressHex;
  public static String contractAddressFrom58;
  public static String contractUrc20AddressFrom58;
  public static String contractAddressFromHex;
  public static ByteString shieldAddressByteString;
  public static byte[] shieldAddressByte;
  public static String shieldAddress;
  public static String deployUrc20Txid;
  public static String deployShieldTxid;
  public static String mint = "mint(uint256,bytes32[9],bytes32[2],bytes32[21])";
  public static String transfer =
      "transfer(bytes32[10][],bytes32[2][],bytes32[9][],bytes32[2],bytes32[21][])";
  public static String burn =
      "burn(bytes32[10],bytes32[2],uint256,bytes32[2],address,"
          + "bytes32[3],bytes32[9][],bytes32[21][])";
  public Wallet wallet = new Wallet();
  static HttpResponse response;
  static HttpPost httppost;
  static JSONObject responseContent;
  public static Integer scalingFactorLogarithm = 0;
  public static Long totalSupply = 1000000000000L;
  public static String name = "jsonrpc-test";
  public static String jsonRpcAssetId;
  public static Integer blockNum;
  public static Integer blockNumForUrc20;
  public static String blockNumHex;
  public static String blockId;
  public static String txid;
  public static String urc20Txid;

  /** constructor. */
  @BeforeSuite(enabled = true, description = "Deploy json rpc test case resource")
  public void deployJsonRpcUseResource() throws Exception {
    Wallet.setAddressPreFixByte(CommonConstant.ADD_PRE_FIX_BYTE_MAINNET);
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
    Assert.assertTrue(
        PublicMethed.sendcoin(
            jsonRpcOwnerAddress,
            2048000000L,
            foundationAccountAddress,
            foundationAccountKey,
            blockingStubFull));
    if (PublicMethed.queryAccount(jsonRpcOwnerAddress, blockingStubFull).getAssetV2Count() == 0L) {
      Assert.assertTrue(
          PublicMethed.sendcoin(
              jsonRpcOwnerAddress,
              2048000000L,
              foundationAccountAddress,
              foundationAccountKey,
              blockingStubFull));
      PublicMethed.waitProduceNextBlock(blockingStubFull);

      // Create a new Asset Issue
      Assert.assertTrue(
          PublicMethed.createAssetIssue(
              jsonRpcOwnerAddress,
              name,
              totalSupply,
              1,
              1,
              System.currentTimeMillis() + 5000,
              System.currentTimeMillis() + 1000000000,
              1,
              "description",
              "urlurlurl",
              2000L,
              2000L,
              1L,
              1L,
              jsonRpcOwnerKey,
              blockingStubFull));

      PublicMethed.waitProduceNextBlock(blockingStubFull);
    }

    response = HttpMethed.getAccount(httpFullNode, jsonRpcOwnerAddress);
    responseContent = HttpMethed.parseResponseContent(response);
    jsonRpcAssetId = responseContent.getString("asset_issued_ID");

    deployContract();
    triggerContract();
    deployUrc20Contract();
  }

  /** constructor. */
  public void deployContract() throws Exception {
    final Long beforeTokenBalance =
        PublicMethed.getAssetBalanceByAssetId(
            ByteString.copyFromUtf8(jsonRpcAssetId), jsonRpcOwnerKey, blockingStubFull);

    JsonObject param = new JsonObject();
    param.addProperty("from", ByteArray.toHexString(jsonRpcOwnerAddress));
    param.addProperty("name", "transferTokenContract");
    param.addProperty("gas", "0x245498");
    String filePath = "./src/test/resources/soliditycode/contractUrcToken001.sol";
    String contractName = "tokenTest";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    System.out.println("CODE:" + code);
    String abi = retMap.get("abI").toString();
    System.out.println("abi:" + abi);

    param.addProperty("abi", abi);
    param.addProperty("data", code);
    param.addProperty("consumeUserResourcePercent", 100);
    param.addProperty("originEnergyLimit", 11111111111111L);
    param.addProperty("value", "0x1f4");
    param.addProperty("tokenId", Long.valueOf(jsonRpcAssetId));
    param.addProperty("tokenValue", 1);
    JsonArray params = new JsonArray();
    params.add(param);
    JsonObject requestBody = getJsonRpcBody("buildTransaction", params);
    response = getJsonRpc(jsonRpcNode, requestBody);
    responseContent = HttpMethed.parseResponseContent(response);
    String transactionString = responseContent.getJSONObject("result").getString("transaction");
    String transactionSignString =
        HttpMethed.gettransactionsign(httpFullNode, transactionString, jsonRpcOwnerKey);

    responseContent = HttpMethed.parseStringContent(transactionString);
    final String txid = responseContent.getString("txID");
    response = HttpMethed.broadcastTransaction(httpFullNode, transactionSignString);
    org.junit.Assert.assertTrue(HttpMethed.verificationResult(response));

    HttpMethed.waitToProduceOneBlock(httpFullNode);
    Long afterTokenBalance =
        PublicMethed.getAssetBalanceByAssetId(
            ByteString.copyFromUtf8(jsonRpcAssetId), jsonRpcOwnerKey, blockingStubFull);

    org.junit.Assert.assertEquals(beforeTokenBalance - afterTokenBalance, 1L);

    response = HttpMethed.getTransactionById(httpFullNode, txid);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    org.junit.Assert.assertTrue(!responseContent.getString("contract_address").isEmpty());
    contractAddressFrom58 = responseContent.getString("contract_address");
    logger.info("contractAddressFrom58:" + contractAddressFrom58);
  }

  /** constructor. */
  public void triggerContract() throws Exception {
    final Long beforeTokenBalance =
        PublicMethed.getAssetBalanceByAssetId(
            ByteString.copyFromUtf8(jsonRpcAssetId), foundationAccountKey, blockingStubFull);
    final Long beforeBalance = HttpMethed.getBalance(httpFullNode, jsonRpcOwnerAddress);
    JsonObject param = new JsonObject();
    param.addProperty("from", "0x" + ByteArray.toHexString(jsonRpcOwnerAddress).substring(2));
    param.addProperty("to", "0x" + contractAddressFrom58);

    String addressParam =
        "000000000000000000000000"
            + ByteArray.toHexString(foundationAccountAddress).substring(2); // [0,3)

    String tokenIdParam =
        "00000000000000000000000000000000000000000000000000000000000"
            + Integer.toHexString(Integer.valueOf(jsonRpcAssetId));

    String tokenValueParam = "0000000000000000000000000000000000000000000000000000000000000001";
    paramString = addressParam + tokenIdParam + tokenValueParam;
    logger.info("paramString:" + paramString);

    String selector = "TransferTokenTo(address,urcToken,uint256)";
    // exit(1);
    param.addProperty("data", "0x" + Util.parseMethod(selector, paramString));
    data = "0x" + Util.parseMethod(selector, paramString);
    param.addProperty("gas", "0x245498");
    param.addProperty("value", "0x1389");
    param.addProperty("tokenId", Long.valueOf(jsonRpcAssetId));
    param.addProperty("tokenValue", 1);
    JsonArray params = new JsonArray();
    params.add(param);
    JsonObject requestBody = getJsonRpcBody("buildTransaction", params);
    response = getJsonRpc(jsonRpcNode, requestBody);
    responseContent = HttpMethed.parseResponseContent(response);
    String transactionString = responseContent.getJSONObject("result").getString("transaction");
    logger.info("transactionString : " + transactionString);
    String transactionSignString =
        HttpMethed.gettransactionsign(httpFullNode, transactionString, jsonRpcOwnerKey);
    logger.info("transactionSignString:" + transactionSignString);
    responseContent = HttpMethed.parseStringContent(transactionString);
    txid = responseContent.getString("txID");
    logger.info("triggerTxid:" + txid);

    response = HttpMethed.broadcastTransaction(httpFullNode, transactionSignString);
    logger.info("response:" + response);
    HttpMethed.verificationResult(response);
    org.junit.Assert.assertTrue(HttpMethed.verificationResult(response));

    HttpMethed.waitToProduceOneBlock(httpFullNode);
    Long afterTokenBalance =
        PublicMethed.getAssetBalanceByAssetId(
            ByteString.copyFromUtf8(jsonRpcAssetId), foundationAccountKey, blockingStubFull);
    Long afterBalance = HttpMethed.getBalance(httpFullNode, jsonRpcOwnerAddress);

    org.junit.Assert.assertEquals(beforeTokenBalance - afterTokenBalance, -1L);
    org.junit.Assert.assertTrue(beforeBalance - afterBalance >= 5000);

    blockNum =
        (int) (PublicMethed.getTransactionInfoById(txid, blockingStubFull).get().getBlockNumber());
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    response = HttpMethed.getBlockByNum(httpFullNode, blockNum);
    org.junit.Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    blockId = responseContent.get("blockID").toString();
  }

  /** constructor. */
  public void deployUrc20Contract() throws InterruptedException {
    String contractName = "shieldUrc20Token";

    String abi = Configuration.getByPath("testng.conf").getString("abi.abi_shieldUrc20Token");
    String code = Configuration.getByPath("testng.conf").getString("code.code_shieldUrc20Token");
    String constructorStr = "constructor(uint256,string,string)";
    String data = totalSupply.toString() + "," + "\"TokenURC20\"" + "," + "\"zen20\"";
    logger.info("data:" + data);
    deployUrc20Txid =
        PublicMethed.deployContractWithConstantParame(
            contractName,
            abi,
            code,
            constructorStr,
            data,
            "",
            maxFeeLimit,
            0L,
            100,
            null,
            jsonRpcOwnerKey,
            jsonRpcOwnerAddress,
            blockingStubFull);

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    logger.info("deployUrc20Txid：" + deployUrc20Txid);
    response = HttpMethed.getTransactionById(httpFullNode, deployUrc20Txid);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    org.junit.Assert.assertTrue(!responseContent.getString("contract_address").isEmpty());
    contractUrc20AddressFrom58 = responseContent.getString("contract_address");
    logger.info("contractUrc20AddressFrom58:" + contractUrc20AddressFrom58);

    //   NewFilterId = createNewFilterId();

    Optional<TransactionInfo> infoById =
        PublicMethed.getTransactionInfoById(deployUrc20Txid, blockingStubFull);

    urc20AddressHex = ByteArray.toHexString(infoById.get().getContractAddress().toByteArray());
    byte[] urc20Address = infoById.get().getContractAddress().toByteArray();

    String selector = "transfer(address,uint256)";
    String addressParam =
        "000000000000000000000000"
            + ByteArray.toHexString(foundationAccountAddress).substring(2); // [0,3)
    String transferValueParam = "0000000000000000000000000000000000000000000000000000000000000001";
    String paramString = addressParam + transferValueParam;
    urc20Txid =
        PublicMethed.triggerContract(
            urc20Address,
            selector,
            paramString,
            true,
            0,
            maxFeeLimit,
            "0",
            0,
            jsonRpcOwnerAddress,
            jsonRpcOwnerKey,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    blockNumForUrc20 =
        (int)
            (PublicMethed.getTransactionInfoById(urc20Txid, blockingStubFull)
                .get()
                .getBlockNumber());
  }

  /** constructor. */
  public static HttpResponse getEthHttps(String ethHttpsNode, JsonObject jsonRpcObject) {
    try {
      String requestUrl = "https://" + ethHttpsNode + "/v3/dfb752dd45204b8daae74249f4653584";
      response = HttpMethed.createConnect(requestUrl, jsonRpcObject);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    return response;
  }

  /** constructor. */
  public static HttpResponse getJsonRpc(String jsonRpcNode, JsonObject jsonRpcObject) {
    try {
      String requestUrl = "http://" + jsonRpcNode + "/jsonrpc";
      response = HttpMethed.createConnect(requestUrl, jsonRpcObject);
    } catch (Exception e) {
      e.printStackTrace();
      httppost.releaseConnection();
      return null;
    }
    return response;
  }

  /** constructor. */
  public static JsonObject getJsonRpcBody(String method) {
    return getJsonRpcBody(method, new JsonArray(), 1);
  }

  /** constructor. */
  public static JsonObject getJsonRpcBody(String method, JsonArray params) {
    return getJsonRpcBody(method, params, 1);
  }

  /** constructor. */
  public static JsonObject getJsonRpcBody(String method, JsonArray params, Integer id) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("jsonrpc", "2.0");
    jsonObject.addProperty("method", method);
    jsonObject.add("params", params);
    jsonObject.addProperty("id", id);

    return jsonObject;
  }
}
