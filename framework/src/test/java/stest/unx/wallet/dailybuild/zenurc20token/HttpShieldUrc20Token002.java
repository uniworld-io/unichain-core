package stest.unx.wallet.dailybuild.zenurc20token;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.HttpMethed;
import stest.unx.wallet.common.client.utils.ZenUrc20Base;

@Slf4j
public class HttpShieldUrc20Token002 extends ZenUrc20Base {

  JSONArray shieldedReceives = new JSONArray();
  String txid;
  private String httpnode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(0);
  private String anotherHttpnode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(1);
  private String httpSolidityNode = Configuration.getByPath("testng.conf")
      .getStringList("httpnode.ip.list").get(2);
  private JSONObject responseContent;
  private HttpResponse response;
  private JSONObject shieldAccountInfo;
  private JSONArray noteTxs;
  private Long publicFromAmount = getRandomLongAmount();

  @Test(enabled = true, description = "Get new shield account  by http")
  public void test01GetNewShieldAccountByHttp() {
    response = getNewShieldedAddress(httpnode);
    shieldAccountInfo = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(shieldAccountInfo);
    Assert.assertEquals(shieldAccountInfo.getString("sk").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("ask").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("nsk").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("ovk").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("ak").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("nk").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("ivk").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("d").length(), 22);
    Assert.assertEquals(shieldAccountInfo.getString("pkD").length(), 64);
    Assert.assertEquals(shieldAccountInfo.getString("payment_address").length(), 81);

    response = HttpMethed.getRcm(httpnode);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
  }

  @Test(enabled = true, description = "Create mint parameters by http")
  public void test02MintByHttp() {
    shieldedReceives = getHttpShieldedReceivesJsonArray(shieldedReceives, publicFromAmount,
        shieldAccountInfo.getString("payment_address"), getRcm((httpnode)));
    response = createShieldContractParameters(httpnode, publicFromAmount, shieldAccountInfo,
        shieldedReceives);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);

    txid = HttpMethed.triggerContractGetTxidWithVisibleTrue(httpnode,anotherHttpnode,
        zenUrc20TokenOwnerAddressString, shieldAddress, mint, responseContent
            .getString("trigger_contract_input"), maxFeeLimit, 0L, 0, 0L,
        zenUrc20TokenOwnerKey);
    HttpMethed.waitToProduceOneBlock(httpnode);
    HttpMethed.waitToProduceOneBlock(httpnode);
    response = HttpMethed.getTransactionInfoById(httpnode, txid, true);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(responseContent.getJSONObject("receipt")
        .getLong("energy_usage_total") > 250000L);
    Assert.assertEquals(responseContent.getString("contract_address"), shieldAddress);
    Assert.assertEquals(responseContent.getJSONObject("receipt").getString("result"),
        "SUCCESS");

    shieldedReceives.clear();
    shieldedReceives = getHttpShieldedReceivesJsonArray(shieldedReceives, publicFromAmount,
        shieldAccountInfo.getString("payment_address"), getRcm(httpnode));
    response = createShieldContractParameters(httpnode, publicFromAmount, shieldAccountInfo,
        shieldedReceives);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);

    txid = HttpMethed.triggerContractGetTxidWithVisibleTrue(httpnode,anotherHttpnode,
        zenUrc20TokenOwnerAddressString, shieldAddress, mint, responseContent
            .getString("trigger_contract_input"), maxFeeLimit, 0L, 0, 0L,
        zenUrc20TokenOwnerKey);

    HttpMethed.waitToProduceOneBlock(httpnode);
    HttpMethed.waitToProduceOneBlock(httpnode);
    response = HttpMethed.getTransactionInfoById(httpnode, txid, true);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(responseContent.getJSONObject("receipt")
        .getLong("energy_usage_total") > 250000L);
    Assert.assertEquals(responseContent.getString("contract_address"), shieldAddress);
    Assert.assertEquals(responseContent.getJSONObject("receipt").getString("result"),
        "SUCCESS");


  }


  @Test(enabled = true, description = "Scan shield URC20 note by http")
  public void test03ScanUrc20NodeByHttp() {
    noteTxs = scanShieldUrc20NoteByIvk(httpnode, shieldAccountInfo);
    logger.info(noteTxs.toJSONString());
    Assert.assertEquals(noteTxs.size(), 2);
    Assert.assertEquals(noteTxs.getJSONObject(1)
        .getJSONObject("note").getLong("value"), publicFromAmount);
    Assert.assertEquals(noteTxs.getJSONObject(1)
            .getJSONObject("note").getString("payment_address"),
        shieldAccountInfo.getString("payment_address"));
    Assert.assertEquals(noteTxs.getJSONObject(1)
        .getString("txid"), txid);
  }

  @Test(enabled = true, description = "Shield urc20 burn by http")
  public void test04ShiledUrc20BurnByHttp() {
    JSONArray shieldSpends = new JSONArray();
    shieldSpends = createAndSetShieldedSpends(httpnode, shieldSpends, noteTxs.getJSONObject(0));

    logger.info(shieldSpends.toJSONString());

    response = createShieldContractParametersForBurn(httpnode, shieldAccountInfo, shieldSpends,
        zenUrc20TokenOwnerAddressString, publicFromAmount);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);

    txid = HttpMethed.triggerContractGetTxidWithVisibleTrue(httpnode,anotherHttpnode,
        zenUrc20TokenOwnerAddressString, shieldAddress, burn, responseContent
            .getString("trigger_contract_input"), maxFeeLimit, 0L, 0, 0L,
        zenUrc20TokenOwnerKey);

    HttpMethed.waitToProduceOneBlock(httpnode);
    HttpMethed.waitToProduceOneBlock(httpnode);
    response = HttpMethed.getTransactionInfoById(httpnode, txid, true);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(responseContent.getJSONObject("receipt")
        .getLong("energy_usage_total") > 150000L);
    Assert.assertEquals(responseContent.getString("contract_address"), shieldAddress);
    Assert.assertEquals(responseContent.getJSONObject("receipt").getString("result"),
        "SUCCESS");

    noteTxs = scanShieldUrc20NoteByOvk(httpnode, shieldAccountInfo);
    logger.info("noteTxs ovk:" + noteTxs);
    Assert.assertEquals(noteTxs.getJSONObject(0).getLong("to_amount"), publicFromAmount);
    Assert.assertEquals(noteTxs.getJSONObject(0).getString("transparent_to_address"),
        zenUrc20TokenOwnerAddressString);
    Assert.assertEquals(noteTxs.getJSONObject(0).getString("txid"), txid);
  }


  @Test(enabled = true, description = "Shield urc20 burn with ask by http")
  public void test05ShiledUrc20BurnWithoutAskByHttp() {
    noteTxs = scanShieldUrc20NoteByIvk(httpnode, shieldAccountInfo);
    JSONArray shieldSpends = new JSONArray();
    shieldSpends = createAndSetShieldedSpends(httpnode, shieldSpends, noteTxs.getJSONObject(1));

    logger.info(shieldSpends.toJSONString());

    response = createShieldContractParametersWithoutAskForBurn(httpnode, shieldAccountInfo,
        shieldSpends, zenUrc20TokenOwnerAddressString, publicFromAmount);
    JSONObject shieldedUrc20Parameters = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(shieldedUrc20Parameters);
    JSONObject spendAuthSig = createSpendAuthSig(httpnode, shieldAccountInfo,
        shieldedUrc20Parameters.getString("message_hash"), noteTxs.getJSONObject(1)
            .getJSONObject("note").getString("rcm"));
    HttpMethed.printJsonContent(spendAuthSig);
    JSONArray spendAuthSigArray = new JSONArray();
    spendAuthSigArray.add(spendAuthSig);

    response = getTriggerInputForShieldedUrc20BurnContract(httpnode,
        shieldedUrc20Parameters, spendAuthSigArray, publicFromAmount,
        zenUrc20TokenOwnerAddressString);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);

    txid = HttpMethed.triggerContractGetTxidWithVisibleTrue(httpnode,anotherHttpnode,
        zenUrc20TokenOwnerAddressString, shieldAddress, burn, responseContent
            .getString("value"), maxFeeLimit, 0L, 0, 0L,
        zenUrc20TokenOwnerKey);

    HttpMethed.waitToProduceOneBlock(httpnode);
    response = HttpMethed.getTransactionInfoById(httpnode, txid, true);
    responseContent = HttpMethed.parseResponseContent(response);
    HttpMethed.printJsonContent(responseContent);
    Assert.assertTrue(responseContent.getJSONObject("receipt")
        .getLong("energy_usage_total") > 150000L);
    Assert.assertEquals(responseContent.getString("contract_address"), shieldAddress);
    Assert.assertEquals(responseContent.getJSONObject("receipt").getString("result"), "SUCCESS");

  }


  @AfterClass(enabled = true)
  public void shutdown() throws InterruptedException {
  }
}