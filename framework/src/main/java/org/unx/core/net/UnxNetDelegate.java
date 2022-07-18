package org.unx.core.net;

import static org.unx.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.prometheus.client.Histogram;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.common.backup.BackupServer;
import org.unx.common.overlay.message.Message;
import org.unx.common.overlay.server.ChannelManager;
import org.unx.common.overlay.server.SyncPool;
import org.unx.common.prometheus.MetricKeys;
import org.unx.common.prometheus.MetricLabels;
import org.unx.common.prometheus.Metrics;
import org.unx.common.utils.Sha256Hash;
import org.unx.core.ChainBaseManager;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.BlockCapsule.BlockId;
import org.unx.core.capsule.PbftSignCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.db.Manager;
import org.unx.core.exception.AccountResourceInsufficientException;
import org.unx.core.exception.BadBlockException;
import org.unx.core.exception.BadItemException;
import org.unx.core.exception.BadNumberBlockException;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractSizeNotEqualToOneException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.DupTransactionException;
import org.unx.core.exception.EventBloomException;
import org.unx.core.exception.ItemNotFoundException;
import org.unx.core.exception.NonCommonBlockException;
import org.unx.core.exception.P2pException;
import org.unx.core.exception.P2pException.TypeEnum;
import org.unx.core.exception.ReceiptCheckErrException;
import org.unx.core.exception.StoreException;
import org.unx.core.exception.TaposException;
import org.unx.core.exception.TooBigTransactionException;
import org.unx.core.exception.TooBigTransactionResultException;
import org.unx.core.exception.TransactionExpirationException;
import org.unx.core.exception.UnLinkedBlockException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.exception.ValidateScheduleException;
import org.unx.core.exception.ValidateSignatureException;
import org.unx.core.exception.ZksnarkException;
import org.unx.core.metrics.MetricsService;
import org.unx.core.net.message.BlockMessage;
import org.unx.core.net.message.MessageTypes;
import org.unx.core.net.message.TransactionMessage;
import org.unx.core.net.peer.PeerConnection;
import org.unx.core.store.WitnessScheduleStore;
import org.unx.protos.Protocol.Inventory.InventoryType;

@Slf4j(topic = "net")
@Component
public class UnxNetDelegate {

  @Autowired
  private SyncPool syncPool;

  @Autowired
  private ChannelManager channelManager;

  @Autowired
  private Manager dbManager;

  @Autowired
  private ChainBaseManager chainBaseManager;

  @Autowired
  private WitnessScheduleStore witnessScheduleStore;

  @Getter
  private Object blockLock = new Object();

  @Autowired
  private BackupServer backupServer;

  @Autowired
  private MetricsService metricsService;

  private volatile boolean backupServerStartFlag;

  private int blockIdCacheSize = 100;

  private long timeout = 1000;

  @Getter // for test
  private volatile boolean  hitDown = false;

  private Thread hitThread;

  // for Test
  @Setter
  private volatile boolean  test = false;

  private Cache<BlockId, Long> freshBlockId = CacheBuilder.newBuilder()
          .maximumSize(blockIdCacheSize).expireAfterWrite(1, TimeUnit.HOURS)
          .recordStats().build();

  @PostConstruct
  public void init() {
    hitThread =  new Thread(() -> {
      LockSupport.park();
      // to Guarantee Some other thread invokes unpark with the current thread as the target
      if (hitDown && !test) {
        System.exit(0);
      }
    });
    hitThread.setName("hit-thread");
    hitThread.start();
  }

  public Collection<PeerConnection> getActivePeer() {
    return syncPool.getActivePeers();
  }

  public long getSyncBeginNumber() {
    return dbManager.getSyncBeginNumber();
  }

  public long getBlockTime(BlockId id) throws P2pException {
    try {
      return chainBaseManager.getBlockById(id).getTimeStamp();
    } catch (BadItemException | ItemNotFoundException e) {
      throw new P2pException(TypeEnum.DB_ITEM_NOT_FOUND, id.getString());
    }
  }

  public BlockId getHeadBlockId() {
    return chainBaseManager.getHeadBlockId();
  }

  public BlockId getSolidBlockId() {
    return chainBaseManager.getSolidBlockId();
  }

  public BlockId getGenesisBlockId() {
    return chainBaseManager.getGenesisBlockId();
  }

  public BlockId getBlockIdByNum(long num) throws P2pException {
    try {
      return chainBaseManager.getBlockIdByNum(num);
    } catch (ItemNotFoundException e) {
      throw new P2pException(TypeEnum.DB_ITEM_NOT_FOUND, "num: " + num);
    }
  }

  public BlockCapsule getGenesisBlock() {
    return chainBaseManager.getGenesisBlock();
  }

  public long getHeadBlockTimeStamp() {
    return chainBaseManager.getHeadBlockTimeStamp();
  }

  public boolean containBlock(BlockId id) {
    return chainBaseManager.containBlock(id);
  }

  public boolean containBlockInMainChain(BlockId id) {
    return chainBaseManager.containBlockInMainChain(id);
  }

  public List<BlockId> getBlockChainHashesOnFork(BlockId forkBlockHash) throws P2pException {
    try {
      return dbManager.getBlockChainHashesOnFork(forkBlockHash);
    } catch (NonCommonBlockException e) {
      throw new P2pException(TypeEnum.HARD_FORKED, forkBlockHash.getString());
    }
  }

  public boolean canChainRevoke(long num) {
    return num >= dbManager.getSyncBeginNumber();
  }

  public boolean contain(Sha256Hash hash, MessageTypes type) {
    if (type.equals(MessageTypes.BLOCK)) {
      return chainBaseManager.containBlock(hash);
    } else if (type.equals(MessageTypes.UNX)) {
      return dbManager.getTransactionStore().has(hash.getBytes());
    }
    return false;
  }

  public Message getData(Sha256Hash hash, InventoryType type) throws P2pException {
    try {
      switch (type) {
        case BLOCK:
          return new BlockMessage(chainBaseManager.getBlockById(hash));
        case UNX:
          TransactionCapsule tx = chainBaseManager.getTransactionStore().get(hash.getBytes());
          if (tx != null) {
            return new TransactionMessage(tx.getInstance());
          }
          throw new StoreException();
        default:
          throw new StoreException();
      }
    } catch (StoreException e) {
      throw new P2pException(TypeEnum.DB_ITEM_NOT_FOUND,
          "type: " + type + ", hash: " + hash.getByteString());
    }
  }

  public void processBlock(BlockCapsule block, boolean isSync) throws P2pException {
    if (!hitDown && dbManager.getLatestSolidityNumShutDown() > 0
        && dbManager.getLatestSolidityNumShutDown() == dbManager.getDynamicPropertiesStore()
        .getLatestBlockHeaderNumberFromDB()) {

      logger.info("begin shutdown, currentBlockNum:{}, DbBlockNum:{} ,solidifiedBlockNum:{}.",
          dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber(),
          dbManager.getDynamicPropertiesStore().getLatestBlockHeaderNumberFromDB(),
          dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
      hitDown = true;
      LockSupport.unpark(hitThread);
      return;
    }
    if (hitDown) {
      return;
    }
    BlockId blockId = block.getBlockId();
    synchronized (blockLock) {
      try {
        if (freshBlockId.getIfPresent(blockId) == null) {
          if (block.getNum() <= getHeadBlockId().getNum()) {
            logger.warn("Receive a fork block {} witness {}, head {}",
                block.getBlockId().getString(),
                Hex.toHexString(block.getWitnessAddress().toByteArray()),
                getHeadBlockId().getString());
          }
          if (!isSync) {
            //record metrics
            metricsService.applyBlock(block);
          }
          dbManager.getBlockedTimer().set(Metrics.histogramStartTimer(
              MetricKeys.Histogram.LOCK_ACQUIRE_LATENCY, MetricLabels.BLOCK));
          Histogram.Timer timer = Metrics.histogramStartTimer(
              MetricKeys.Histogram.BLOCK_PROCESS_LATENCY, String.valueOf(isSync));
          dbManager.pushBlock(block);
          Metrics.histogramObserve(timer);
          freshBlockId.put(blockId, System.currentTimeMillis());
          logger.info("Success process block {}.", blockId.getString());
          if (!backupServerStartFlag
              && System.currentTimeMillis() - block.getTimeStamp() < BLOCK_PRODUCED_INTERVAL) {
            backupServerStartFlag = true;
            backupServer.initServer();
          }
        }
      } catch (ValidateSignatureException
          | ContractValidateException
          | ContractExeException
          | UnLinkedBlockException
          | ValidateScheduleException
          | AccountResourceInsufficientException
          | TaposException
          | TooBigTransactionException
          | TooBigTransactionResultException
          | DupTransactionException
          | TransactionExpirationException
          | BadNumberBlockException
          | BadBlockException
          | NonCommonBlockException
          | ReceiptCheckErrException
          | VMIllegalException
          | ZksnarkException
          | EventBloomException e) {
        metricsService.failProcessBlock(block.getNum(), e.getMessage());
        logger.error("Process block failed, {}, reason: {}.", blockId.getString(), e.getMessage());
        throw new P2pException(TypeEnum.BAD_BLOCK, e);
      }
    }
  }

  public void pushTransaction(TransactionCapsule unx) throws P2pException {
    try {
      unx.setTime(System.currentTimeMillis());
      dbManager.pushTransaction(unx);
    } catch (ContractSizeNotEqualToOneException
        | VMIllegalException e) {
      throw new P2pException(TypeEnum.BAD_UNX, e);
    } catch (ContractValidateException
        | ValidateSignatureException
        | ContractExeException
        | DupTransactionException
        | TaposException
        | TooBigTransactionException
        | TransactionExpirationException
        | ReceiptCheckErrException
        | TooBigTransactionResultException
        | AccountResourceInsufficientException e) {
      throw new P2pException(TypeEnum.UNX_EXE_FAILED, e);
    }
  }

  public void validSignature(BlockCapsule block) throws P2pException {
    try {
      if (!block.validateSignature(dbManager.getDynamicPropertiesStore(),
              dbManager.getAccountStore())) {
        throw new P2pException(TypeEnum.BAD_BLOCK, "valid signature failed.");
      }
    } catch (ValidateSignatureException e) {
      throw new P2pException(TypeEnum.BAD_BLOCK, e);
    }
  }

  public boolean validBlock(BlockCapsule block) throws P2pException {
    long time = System.currentTimeMillis();
    if (block.getTimeStamp() - time > timeout) {
      throw new P2pException(TypeEnum.BAD_BLOCK,
              "time:" + time + ",block time:" + block.getTimeStamp());
    }
    validSignature(block);
    return witnessScheduleStore.getActiveWitnesses().contains(block.getWitnessAddress());
  }

  public PbftSignCapsule getBlockPbftCommitData(long blockNum) {
    return chainBaseManager.getPbftSignDataStore().getBlockSignData(blockNum);
  }

  public PbftSignCapsule getSRLPbftCommitData(long epoch) {
    return chainBaseManager.getPbftSignDataStore().getSrSignData(epoch);
  }

  public boolean allowPBFT() {
    return chainBaseManager.getDynamicPropertiesStore().allowPBFT();
  }

}
