package org.unx.core.net.services;

import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.unx.common.application.UnxApplicationContext;
import org.unx.common.overlay.server.SyncPool;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.utils.FileUtil;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.Constant;
import org.unx.core.capsule.BlockCapsule;
import org.unx.common.utils.ReflectUtils;
import org.unx.core.config.DefaultConfig;
import org.unx.core.config.args.Args;
import org.unx.core.net.message.BlockMessage;
import org.unx.core.net.message.TransactionMessage;
import org.unx.core.net.peer.Item;
import org.unx.core.net.peer.PeerConnection;
import org.unx.core.net.service.AdvService;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.Inventory.InventoryType;

//@Ignore
public class AdvServiceTest {

  protected UnxApplicationContext context;
  private AdvService service;
  private PeerConnection peer;
  private SyncPool syncPool;
  private String dbPath = "output-adv-service-test";

  /**
   * init context.
   */
  @Before
  public void init() {
    Args.setParam(new String[]{"--output-directory", dbPath, "--debug"},
        Constant.TEST_CONF);
    context = new UnxApplicationContext(DefaultConfig.class);
    service = context.getBean(AdvService.class);
  }

  /**
   * destroy.
   */
  @After
  public void destroy() {
    Args.clearParam();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void test() {
    testAddInv();
    testBroadcast();
    //testFastSend();
    testUnxBroadcast();
  }

  private void testAddInv() {
    boolean flag;
    Item itemUnx = new Item(Sha256Hash.ZERO_HASH, InventoryType.UNX);
    flag = service.addInv(itemUnx);
    Assert.assertTrue(flag);
    flag = service.addInv(itemUnx);
    Assert.assertFalse(flag);

    Item itemBlock = new Item(Sha256Hash.ZERO_HASH, InventoryType.BLOCK);
    flag = service.addInv(itemBlock);
    Assert.assertTrue(flag);
    flag = service.addInv(itemBlock);
    Assert.assertFalse(flag);

    service.addInvToCache(itemBlock);
    flag = service.addInv(itemBlock);
    Assert.assertFalse(flag);
  }

  private void testBroadcast() {

    try {
      peer = context.getBean(PeerConnection.class);
      syncPool = context.getBean(SyncPool.class);

      List<PeerConnection> peers = Lists.newArrayList();
      peers.add(peer);
      ReflectUtils.setFieldValue(syncPool, "activePeers", peers);
      BlockCapsule blockCapsule = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
          System.currentTimeMillis(), Sha256Hash.ZERO_HASH.getByteString());
      BlockMessage msg = new BlockMessage(blockCapsule);
      service.broadcast(msg);
      Item item = new Item(blockCapsule.getBlockId(), InventoryType.BLOCK);
      Assert.assertNotNull(service.getMessage(item));

      peer.close();
      syncPool.close();
    } catch (NullPointerException e) {
      System.out.println(e);
    }
  }
  /*
  private void testFastSend() {

    try {
      peer = context.getBean(PeerConnection.class);
      syncPool = context.getBean(SyncPool.class);

      List<PeerConnection> peers = Lists.newArrayList();
      peers.add(peer);
      ReflectUtils.setFieldValue(syncPool, "activePeers", peers);
      BlockCapsule blockCapsule = new BlockCapsule(1, Sha256Hash.ZERO_HASH,
          System.currentTimeMillis(), Sha256Hash.ZERO_HASH.getByteString());
      BlockMessage msg = new BlockMessage(blockCapsule);
      service.fastForward(msg);
      Item item = new Item(blockCapsule.getBlockId(), InventoryType.BLOCK);
      //Assert.assertNull(service.getMessage(item));

      peer.getAdvInvRequest().put(item, System.currentTimeMillis());
      service.onDisconnect(peer);

      peer.close();
      syncPool.close();
    } catch (NullPointerException e) {
      System.out.println(e);
    }
  }
  */

  private void testUnxBroadcast() {
    Protocol.Transaction unx = Protocol.Transaction.newBuilder().build();
    CommonParameter.getInstance().setValidContractProtoThreadNum(1);
    TransactionMessage msg = new TransactionMessage(unx);
    service.broadcast(msg);
    Item item = new Item(msg.getMessageId(), InventoryType.UNX);
    Assert.assertNotNull(service.getMessage(item));
  }

}
