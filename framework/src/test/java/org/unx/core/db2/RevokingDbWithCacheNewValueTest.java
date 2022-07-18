package org.unx.core.db2;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.unx.common.application.Application;
import org.unx.common.application.ApplicationFactory;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.FileUtil;
import org.unx.common.utils.SessionOptional;
import org.unx.core.Constant;
import org.unx.core.capsule.utils.MarketUtils;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.db.UnxStoreWithRevoking;
import org.unx.core.db2.SnapshotRootTest.ProtoCapsuleTest;
import org.unx.core.db2.core.SnapshotManager;
import org.unx.core.exception.RevokingStoreIllegalStateException;

@Slf4j
public class RevokingDbWithCacheNewValueTest {

  private SnapshotManager revokingDatabase;
  private UnxApplicationContext context;
  private Application appT;
  private TestRevokingUnxStore unxDatabase;

  @Before
  public void init() {
    Args.setParam(new String[]{"-d", "output_revokingStore_test"},
        Constant.TEST_CONF);
    context = new UnxApplicationContext(DefaultConfig.class);
    appT = ApplicationFactory.create(context);
  }

  @After
  public void removeDb() {
    Args.clearParam();
    context.destroy();
    unxDatabase.close();
    FileUtil.deleteDir(new File("output_revokingStore_test"));
  }

  @Test
  public synchronized void testPop() throws RevokingStoreIllegalStateException {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testRevokingDBWithCacheNewValue-testPop");
    revokingDatabase.add(unxDatabase.getRevokingDB());

    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    for (int i = 1; i < 11; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("pop" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(1, revokingDatabase.getActiveSession());
        tmpSession.commit();
        Assert.assertEquals(i, revokingDatabase.getSize());
        Assert.assertEquals(0, revokingDatabase.getActiveSession());
      }
    }

    for (int i = 1; i < 11; i++) {
      revokingDatabase.pop();
      Assert.assertEquals(10 - i, revokingDatabase.getSize());
    }

    Assert.assertEquals(0, revokingDatabase.getSize());
  }

  @Test
  public synchronized void testMerge() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testRevokingDBWithCacheNewValue-testMerge");
    revokingDatabase.add(unxDatabase.getRevokingDB());

    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }
    SessionOptional dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
    dialog.setValue(revokingDatabase.buildSession());
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("merge".getBytes());
    ProtoCapsuleTest testProtoCapsule2 = new ProtoCapsuleTest("merge2".getBytes());

    unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule);

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule2);
      tmpSession.merge();
    }
    Assert.assertEquals(testProtoCapsule2, unxDatabase.get(testProtoCapsule.getData()));

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.delete(testProtoCapsule.getData());
      tmpSession.merge();
    }
    Assert.assertNull(unxDatabase.get(testProtoCapsule.getData()));
    dialog.reset();
  }


  @Test
  public synchronized void testRevoke() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testRevokingDBWithCacheNewValue-testRevoke");
    revokingDatabase.add(unxDatabase.getRevokingDB());

    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }
    SessionOptional dialog = SessionOptional.instance().setValue(revokingDatabase.buildSession());
    for (int i = 0; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("undo" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        Assert.assertEquals(2, revokingDatabase.getSize());
        tmpSession.merge();
        Assert.assertEquals(1, revokingDatabase.getSize());
      }
    }

    Assert.assertEquals(1, revokingDatabase.getSize());
    dialog.reset();
    Assert.assertEquals(0, revokingDatabase.getSize());
    Assert.assertEquals(0, revokingDatabase.getActiveSession());

    dialog.setValue(revokingDatabase.buildSession());
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest("revoke".getBytes());
    ProtoCapsuleTest testProtoCapsule2 = new ProtoCapsuleTest("revoke2".getBytes());
    unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
    dialog.setValue(revokingDatabase.buildSession());

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule2);
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("revoke22".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(testProtoCapsule.getData(), new ProtoCapsuleTest("revoke222".getBytes()));
      tmpSession.merge();
    }

    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.delete(testProtoCapsule.getData());
      tmpSession.merge();
    }

    dialog.reset();

    logger.info(
        "**********testProtoCapsule:" + (unxDatabase.getUnchecked(testProtoCapsule.getData()))
            .toString());
    Assert.assertEquals(testProtoCapsule, unxDatabase.get(testProtoCapsule.getData()));
  }

  @Test
  public synchronized void testGetlatestValues() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testSnapshotManager-testGetlatestValues");
    revokingDatabase.add(unxDatabase.getRevokingDB());
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    for (int i = 1; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getLastestValues" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    Set<ProtoCapsuleTest> result = unxDatabase.getRevokingDB().getlatestValues(5).stream()
        .map(ProtoCapsuleTest::new)
        .collect(Collectors.toSet());

    for (int i = 9; i >= 5; i--) {
      Assert.assertTrue(result.contains(new ProtoCapsuleTest(("getLastestValues" + i).getBytes())));
    }
  }

  @Test
  public synchronized void testGetValuesNext() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testSnapshotManager-testGetValuesNext");
    revokingDatabase.add(unxDatabase.getRevokingDB());
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    for (int i = 1; i < 10; i++) {
      ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getValuesNext" + i).getBytes());
      try (ISession tmpSession = revokingDatabase.buildSession()) {
        unxDatabase.put(testProtoCapsule.getData(), testProtoCapsule);
        tmpSession.commit();
      }
    }

    Set<ProtoCapsuleTest> result =
        unxDatabase.getRevokingDB().getValuesNext(
            new ProtoCapsuleTest("getValuesNext2".getBytes()).getData(), 3
        ).stream().map(ProtoCapsuleTest::new).collect(Collectors.toSet());

    for (int i = 2; i < 5; i++) {
      Assert.assertTrue(result.contains(new ProtoCapsuleTest(("getValuesNext" + i).getBytes())));
    }
  }

  @Test
  public synchronized void testGetKeysNext() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testSnapshotManager-testGetKeysNext");
    revokingDatabase.add(unxDatabase.getRevokingDB());
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey0 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        0L,
        0L
    );
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1L,
        4L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        3L,
        9L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        2L,
        7L
    );

    // put: 2 1 0 3
    // comparator: 0 2 3 1
    // lexicographical order: 0 1 3 2
    ProtoCapsuleTest testProtoCapsule = new ProtoCapsuleTest(("getKeysNext2").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey2, testProtoCapsule);
      tmpSession.commit();
    }
    testProtoCapsule = new ProtoCapsuleTest(("getKeysNext1").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey1, testProtoCapsule);
      tmpSession.commit();
    }

    testProtoCapsule = new ProtoCapsuleTest(("getKeysNext0").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey0, testProtoCapsule);
      tmpSession.commit();
    }
    testProtoCapsule = new ProtoCapsuleTest(("getKeysNext3").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey3, testProtoCapsule);
      tmpSession.commit();
    }

    List<byte[]> result = unxDatabase.getRevokingDB().getKeysNext(pairPriceKey0, 4);

    // lexicographical order: 0 1 3 2
    List<byte[]> list = Arrays.asList(pairPriceKey0, pairPriceKey2, pairPriceKey3, pairPriceKey1);
    for (int i = 0; i < 4; i++) {
      Assert.assertArrayEquals(list.get(i), result.get(i));
    }
  }

  @Test
  public synchronized void testGetKeysNextWithSameKey() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testSnapshotManager-testGetKeysNextWithSameKey");
    revokingDatabase.add(unxDatabase.getRevokingDB());
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();
    }

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey0 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        0L,
        0L
    );
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        2L,
        6L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        3L,
        9L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1L,
        4L
    );

    Assert.assertArrayEquals(pairPriceKey1, pairPriceKey2);

    // put: 2 1 0 3
    // comparator: 0 1 3
    // lexicographical order: 0 1 3
    ProtoCapsuleTest testProtoCapsule2 = new ProtoCapsuleTest(("getKeysNext2").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey2, testProtoCapsule2);
      tmpSession.commit();
    }
    Assert.assertArrayEquals(testProtoCapsule2.getData(),
        unxDatabase.get(pairPriceKey2).getData());

    ProtoCapsuleTest testProtoCapsule1 = new ProtoCapsuleTest(("getKeysNext1").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey1, testProtoCapsule1);
      tmpSession.commit();
    }

    // pairPriceKey1 equals pairPriceKey2, the latter will overwrite the previous
    Assert.assertArrayEquals(testProtoCapsule1.getData(),
        unxDatabase.get(pairPriceKey1).getData());
    Assert.assertArrayEquals(testProtoCapsule1.getData(),
        unxDatabase.get(pairPriceKey2).getData());

    ProtoCapsuleTest testProtoCapsule0 = new ProtoCapsuleTest(("getKeysNext0").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey0, testProtoCapsule0);
      tmpSession.commit();
    }

    ProtoCapsuleTest testProtoCapsule3 = new ProtoCapsuleTest(("getKeysNext3").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey3, testProtoCapsule3);
      tmpSession.commit();
    }

    List<byte[]> result = unxDatabase.getRevokingDB().getKeysNext(pairPriceKey0, 3);

    List<byte[]> list = Arrays.asList(pairPriceKey0, pairPriceKey1, pairPriceKey3);
    for (int i = 0; i < 3; i++) {
      Assert.assertArrayEquals(list.get(i), result.get(i));
    }
  }

  @Test
  public synchronized void testGetKeysNextWithSameKeyOrderCheck() {
    revokingDatabase = context.getBean(SnapshotManager.class);
    revokingDatabase.enable();
    unxDatabase = new TestRevokingUnxStore("testSnapshotManager-testGetKeysNextWithSameKey");
    revokingDatabase.add(unxDatabase.getRevokingDB());
    while (revokingDatabase.size() != 0) {
      revokingDatabase.pop();

    }

    byte[] sellTokenID1 = ByteArray.fromString("100");
    byte[] buyTokenID1 = ByteArray.fromString("200");
    byte[] pairPriceKey0 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        0L,
        0L
    );
    byte[] pairPriceKey1 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        1L,
        4L
    );
    byte[] pairPriceKey2 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        2L,
        8L
    );
    byte[] pairPriceKey3 = MarketUtils.createPairPriceKey(
        sellTokenID1,
        buyTokenID1,
        2L,
        7L
    );

    Assert.assertArrayEquals(pairPriceKey1, pairPriceKey2);

    // put: 2 1 0 3
    // comparator: 0 3 1
    // lexicographical order: 0 1 3
    ProtoCapsuleTest testProtoCapsule2 = new ProtoCapsuleTest(("getKeysNext2").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey2, testProtoCapsule2);
      tmpSession.commit();
    }
    Assert.assertArrayEquals(testProtoCapsule2.getData(),
        unxDatabase.get(pairPriceKey2).getData());

    ProtoCapsuleTest testProtoCapsule1 = new ProtoCapsuleTest(("getKeysNext1").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey1, testProtoCapsule1);
      tmpSession.commit();
    }

    // pairPriceKey1 equals pairPriceKey2, the latter will overwrite the previous
    Assert.assertArrayEquals(testProtoCapsule1.getData(),
        unxDatabase.get(pairPriceKey1).getData());
    Assert.assertArrayEquals(testProtoCapsule1.getData(),
        unxDatabase.get(pairPriceKey2).getData());

    ProtoCapsuleTest testProtoCapsule0 = new ProtoCapsuleTest(("getKeysNext0").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey0, testProtoCapsule0);
      tmpSession.commit();
    }

    ProtoCapsuleTest testProtoCapsule3 = new ProtoCapsuleTest(("getKeysNext3").getBytes());
    try (ISession tmpSession = revokingDatabase.buildSession()) {
      unxDatabase.put(pairPriceKey3, testProtoCapsule3);
      tmpSession.commit();
    }

    List<byte[]> result = unxDatabase.getRevokingDB().getKeysNext(pairPriceKey0, 3);

    List<byte[]> list = Arrays.asList(pairPriceKey0, pairPriceKey3, pairPriceKey1);
    for (int i = 0; i < 3; i++) {
      Assert.assertArrayEquals(list.get(i), result.get(i));
    }
  }

  public static class TestRevokingUnxStore extends UnxStoreWithRevoking<ProtoCapsuleTest> {

    protected TestRevokingUnxStore(String dbName) {
      super(dbName);
    }

    @Override
    public ProtoCapsuleTest get(byte[] key) {
      byte[] value = this.revokingDB.getUnchecked(key);
      return ArrayUtils.isEmpty(value) ? null : new ProtoCapsuleTest(value);
    }
  }

  public static class TestSnapshotManager extends SnapshotManager {

    public TestSnapshotManager(String checkpointPath) {
      super(checkpointPath);
    }
  }
}
