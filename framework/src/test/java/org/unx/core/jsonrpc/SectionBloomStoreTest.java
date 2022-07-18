package org.unx.core.jsonrpc;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testng.Assert;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.runtime.vm.DataWord;
import org.unx.common.runtime.vm.LogInfo;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.FileUtil;
import org.unx.core.Constant;
import org.unx.core.capsule.TransactionRetCapsule;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.exception.EventBloomException;
import org.unx.core.services.jsonrpc.JsonRpc.FilterRequest;
import org.unx.core.services.jsonrpc.filters.LogBlockQuery;
import org.unx.core.services.jsonrpc.filters.LogFilterWrapper;
import org.unx.core.store.SectionBloomStore;
import org.unx.protos.Protocol.TransactionInfo;
import org.unx.protos.Protocol.TransactionInfo.Log;

public class SectionBloomStoreTest {

  private static final String dbPath = "output-sectionBloomStore-test";
  static SectionBloomStore sectionBloomStore;
  private static UnxApplicationContext context;

  static {
    Args.setParam(new String[] {"--output-directory", dbPath},
        Constant.TEST_CONF);
    context = new UnxApplicationContext(DefaultConfig.class);
  }

  @BeforeClass
  public static void init() {
    sectionBloomStore = context.getBean(SectionBloomStore.class);
  }

  @AfterClass
  public static void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void testPutAndGet() {
    BitSet bitSet = new BitSet(SectionBloomStore.BLOCK_PER_SECTION);
    bitSet.set(1);
    bitSet.set(100);
    bitSet.set(1000);
    try {
      sectionBloomStore.put(100, 101, bitSet);
      Assert.assertEquals(bitSet, sectionBloomStore.get(100, 101));
    } catch (EventBloomException e) {
      Assert.fail();
    }
  }

  private byte[] bytesToAddress(byte[] address) {
    byte[] data = new byte[20];
    System.arraycopy(address, 0, data, 20 - address.length, address.length);
    return data;
  }

  private TransactionInfo createTransactionInfo(byte[] address1, byte[] address2, byte[] topic) {
    List<Log> logList = new ArrayList<>();
    List<DataWord> topics = new ArrayList<>();
    topics.add(new DataWord(topic));

    TransactionInfo.Builder builder = TransactionInfo.newBuilder();

    LogInfo logInfo = new LogInfo(address1, topics, null);
    logList.add(LogInfo.buildLog(logInfo));
    logInfo = new LogInfo(address2, topics, null);
    logList.add(LogInfo.buildLog(logInfo));
    builder.addAllLog(logList);

    return builder.build();
  }

  private TransactionInfo createTransactionInfo(byte[] address, byte[] topic) {
    List<Log> logList = new ArrayList<>();
    List<DataWord> topics = new ArrayList<>();
    topics.add(new DataWord(topic));

    TransactionInfo.Builder builder = TransactionInfo.newBuilder();

    LogInfo logInfo = new LogInfo(address, topics, null);
    logList.add(LogInfo.buildLog(logInfo));
    builder.addAllLog(logList);

    return builder.build();
  }

  @Test
  public void testWriteAndQuery() {

    byte[] address1 = bytesToAddress(new byte[] {0x11});
    byte[] address2 = bytesToAddress(new byte[] {0x22});
    byte[] address3 = bytesToAddress(new byte[] {0x33});
    byte[] address4 = bytesToAddress(new byte[] {0x44});
    byte[] address5 = bytesToAddress(new byte[] {0x55});
    byte[] topic1 = ByteArray
        .fromHexString("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    byte[] topic2 = ByteArray
        .fromHexString("0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67");

    //add1
    TransactionRetCapsule capsule1 = new TransactionRetCapsule();
    capsule1.addTransactionInfo(createTransactionInfo(address1, address2, topic1));
    sectionBloomStore.initBlockSection(capsule1);
    try {
      sectionBloomStore.write(10000);
    } catch (EventBloomException e) {
      Assert.fail();
    }

    //add2
    TransactionRetCapsule capsule2 = new TransactionRetCapsule();
    capsule2.addTransactionInfo(createTransactionInfo(address3, address4, topic1));
    sectionBloomStore.initBlockSection(capsule2);
    try {
      sectionBloomStore.write(20000);
    } catch (EventBloomException e) {
      Assert.fail();
    }

    //add3
    TransactionRetCapsule capsule3 = new TransactionRetCapsule();
    capsule3.addTransactionInfo(createTransactionInfo(address5, topic2));
    sectionBloomStore.initBlockSection(capsule3);
    try {
      sectionBloomStore.write(30000);
    } catch (EventBloomException e) {
      Assert.fail();
    }

    long currentMaxBlockNum = 50000;
    ExecutorService sectionExecutor = Executors.newFixedThreadPool(5);

    //query one address
    try {
      LogFilterWrapper logFilterWrapper = new LogFilterWrapper(
          new FilterRequest("earliest", "latest", ByteArray.toJsonHex(address1), null, null),
          currentMaxBlockNum, null);
      LogBlockQuery logBlockQuery =
          new LogBlockQuery(logFilterWrapper, sectionBloomStore, currentMaxBlockNum,
              sectionExecutor);
      List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();
      Assert.assertTrue(possibleBlockList.contains(10000L));
    } catch (Exception e) {
      Assert.fail();
    }

    //query multi address
    List<String> addressList = new ArrayList<>();
    addressList.add(ByteArray.toJsonHex(address1));
    addressList.add(ByteArray.toJsonHex(address5));
    try {
      LogFilterWrapper logFilterWrapper = new LogFilterWrapper(
          new FilterRequest("earliest", "latest", addressList, null, null),
          currentMaxBlockNum, null);
      LogBlockQuery logBlockQuery =
          new LogBlockQuery(logFilterWrapper, sectionBloomStore, currentMaxBlockNum,
              sectionExecutor);
      List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();
      Assert.assertTrue(possibleBlockList.contains(10000L));
      Assert.assertTrue(possibleBlockList.contains(30000L));
    } catch (Exception e) {
      Assert.fail();
    }

    //query one topic
    try {
      LogFilterWrapper logFilterWrapper = new LogFilterWrapper(
          new FilterRequest("earliest", "latest", null,
              new String[] {ByteArray.toHexString(topic1)}, null),
          currentMaxBlockNum, null);
      LogBlockQuery logBlockQuery =
          new LogBlockQuery(logFilterWrapper, sectionBloomStore, currentMaxBlockNum,
              sectionExecutor);
      List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();
      Assert.assertTrue(possibleBlockList.contains(10000L));
      Assert.assertTrue(possibleBlockList.contains(20000L));
    } catch (Exception e) {
      Assert.fail();
    }

    //query another topic
    try {
      LogFilterWrapper logFilterWrapper = new LogFilterWrapper(
          new FilterRequest("earliest", "latest", null,
              new String[] {ByteArray.toHexString(topic2)}, null),
          currentMaxBlockNum, null);
      LogBlockQuery logBlockQuery =
          new LogBlockQuery(logFilterWrapper, sectionBloomStore, currentMaxBlockNum,
              sectionExecutor);
      List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();
      Assert.assertTrue(possibleBlockList.contains(30000L));
    } catch (Exception e) {
      Assert.fail();
    }

    //query multi topic in "or" condition
    List<String> topicList = new ArrayList<>();
    topicList.add(ByteArray.toJsonHex(topic1));
    topicList.add(ByteArray.toJsonHex(topic2));
    try {
      LogFilterWrapper logFilterWrapper = new LogFilterWrapper(
          new FilterRequest("earliest", "latest", null,
              new Object[] {topicList}, null),
          currentMaxBlockNum, null);
      LogBlockQuery logBlockQuery =
          new LogBlockQuery(logFilterWrapper, sectionBloomStore, currentMaxBlockNum,
              sectionExecutor);
      List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();
      Assert.assertTrue(possibleBlockList.contains(10000L));
      Assert.assertTrue(possibleBlockList.contains(20000L));
      Assert.assertTrue(possibleBlockList.contains(30000L));
    } catch (Exception e) {
      Assert.fail();
    }

    //add4
    TransactionRetCapsule capsule4 = new TransactionRetCapsule();
    capsule4.addTransactionInfo(createTransactionInfo(address1, address2, topic2));
    sectionBloomStore.initBlockSection(capsule4);
    try {
      sectionBloomStore.write(10000);
    } catch (EventBloomException e) {
      Assert.fail();
    }

    //query multi topic in "and" condition. Match Bloom only, but not match exactly.
    try {
      LogFilterWrapper logFilterWrapper = new LogFilterWrapper(
          new FilterRequest("earliest", "latest", null,
              new Object[] {ByteArray.toJsonHex(topic1), ByteArray.toJsonHex(topic2)}, null),
          currentMaxBlockNum, null);
      LogBlockQuery logBlockQuery =
          new LogBlockQuery(logFilterWrapper, sectionBloomStore, currentMaxBlockNum,
              sectionExecutor);
      List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();
      Assert.assertTrue(possibleBlockList.contains(10000L));
    } catch (Exception e) {
      Assert.fail();
    }
  }
}
