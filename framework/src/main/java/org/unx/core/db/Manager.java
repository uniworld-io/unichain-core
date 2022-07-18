package org.unx.core.db;

import static org.unx.common.utils.Commons.adjustBalance;
import static org.unx.protos.Protocol.Transaction.Contract.ContractType.TransferContract;
import static org.unx.protos.Protocol.Transaction.Result.contractResult.SUCCESS;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import io.prometheus.client.Histogram;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unx.api.GrpcAPI.TransactionInfoList;
import org.unx.common.args.GenesisBlock;
import org.unx.common.bloom.Bloom;
import org.unx.common.logsfilter.EventPluginLoader;
import org.unx.common.logsfilter.FilterQuery;
import org.unx.common.logsfilter.capsule.BlockFilterCapsule;
import org.unx.common.logsfilter.capsule.BlockLogTriggerCapsule;
import org.unx.common.logsfilter.capsule.ContractTriggerCapsule;
import org.unx.common.logsfilter.capsule.FilterTriggerCapsule;
import org.unx.common.logsfilter.capsule.LogsFilterCapsule;
import org.unx.common.logsfilter.capsule.SolidityTriggerCapsule;
import org.unx.common.logsfilter.capsule.TransactionLogTriggerCapsule;
import org.unx.common.logsfilter.capsule.TriggerCapsule;
import org.unx.common.logsfilter.trigger.ContractEventTrigger;
import org.unx.common.logsfilter.trigger.ContractLogTrigger;
import org.unx.common.logsfilter.trigger.ContractTrigger;
import org.unx.common.logsfilter.trigger.Trigger;
import org.unx.common.overlay.message.Message;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.prometheus.MetricKeys;
import org.unx.common.prometheus.MetricLabels;
import org.unx.common.prometheus.Metrics;
import org.unx.common.runtime.RuntimeImpl;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.JsonUtil;
import org.unx.common.utils.Pair;
import org.unx.common.utils.SessionOptional;
import org.unx.common.utils.Sha256Hash;
import org.unx.common.utils.StringUtil;
import org.unx.common.zksnark.MerkleContainer;
import org.unx.consensus.Consensus;
import org.unx.consensus.base.Param.Miner;
import org.unx.core.ChainBaseManager;
import org.unx.core.Constant;
import org.unx.core.Wallet;
import org.unx.core.actuator.ActuatorCreator;
import org.unx.core.capsule.AccountCapsule;
import org.unx.core.capsule.BlockBalanceTraceCapsule;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.BlockCapsule.BlockId;
import org.unx.core.capsule.BytesCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.capsule.TransactionInfoCapsule;
import org.unx.core.capsule.TransactionRetCapsule;
import org.unx.core.capsule.WitnessCapsule;
import org.unx.core.capsule.utils.TransactionUtil;
import org.unx.core.config.Parameter.ChainConstant;
import org.unx.core.config.args.Args;
import org.unx.core.consensus.ProposalController;
import org.unx.core.db.KhaosDatabase.KhaosBlock;
import org.unx.core.db.accountstate.TrieService;
import org.unx.core.db.accountstate.callback.AccountStateCallBack;
import org.unx.core.db.api.AssetUpdateHelper;
import org.unx.core.db.api.EnergyPriceHistoryLoader;
import org.unx.core.db.api.MoveAbiHelper;
import org.unx.core.db2.ISession;
import org.unx.core.db2.core.Chainbase;
import org.unx.core.db2.core.IUnxChainBase;
import org.unx.core.db2.core.SnapshotManager;
import org.unx.core.exception.AccountResourceInsufficientException;
import org.unx.core.exception.BadBlockException;
import org.unx.core.exception.BadItemException;
import org.unx.core.exception.BadNumberBlockException;
import org.unx.core.exception.BalanceInsufficientException;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractSizeNotEqualToOneException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.DupTransactionException;
import org.unx.core.exception.EventBloomException;
import org.unx.core.exception.ItemNotFoundException;
import org.unx.core.exception.NonCommonBlockException;
import org.unx.core.exception.ReceiptCheckErrException;
import org.unx.core.exception.TaposException;
import org.unx.core.exception.TooBigTransactionException;
import org.unx.core.exception.TooBigTransactionResultException;
import org.unx.core.exception.TransactionExpirationException;
import org.unx.core.exception.UnLinkedBlockException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.exception.ValidateScheduleException;
import org.unx.core.exception.ValidateSignatureException;
import org.unx.core.exception.ZksnarkException;
import org.unx.core.metrics.MetricsKey;
import org.unx.core.metrics.MetricsUtil;
import org.unx.core.service.MortgageService;
import org.unx.core.store.AccountAssetStore;
import org.unx.core.store.AccountIdIndexStore;
import org.unx.core.store.AccountIndexStore;
import org.unx.core.store.AccountStore;
import org.unx.core.store.AssetIssueStore;
import org.unx.core.store.AssetIssueV2Store;
import org.unx.core.store.CodeStore;
import org.unx.core.store.ContractStore;
import org.unx.core.store.DelegatedResourceAccountIndexStore;
import org.unx.core.store.DelegatedResourceStore;
import org.unx.core.store.DelegationStore;
import org.unx.core.store.DynamicPropertiesStore;
import org.unx.core.store.ExchangeStore;
import org.unx.core.store.ExchangeV2Store;
import org.unx.core.store.IncrementalMerkleTreeStore;
import org.unx.core.store.NullifierStore;
import org.unx.core.store.ProposalStore;
import org.unx.core.store.StorageRowStore;
import org.unx.core.store.StoreFactory;
import org.unx.core.store.TransactionHistoryStore;
import org.unx.core.store.TransactionRetStore;
import org.unx.core.store.VotesStore;
import org.unx.core.store.WitnessScheduleStore;
import org.unx.core.store.WitnessStore;
import org.unx.core.utils.TransactionRegister;
import org.unx.protos.Protocol.AccountType;
import org.unx.protos.Protocol.Permission;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract;
import org.unx.protos.Protocol.TransactionInfo;
import org.unx.protos.contract.BalanceContract;


@Slf4j(topic = "DB")
@Component
public class Manager {

  private static final int SHIELDED_TRANS_IN_BLOCK_COUNTS = 1;
  private static final String SAVE_BLOCK = "save block: ";
  private static final int SLEEP_TIME_OUT = 50;
  private static final int TX_ID_CACHE_SIZE = 100_000;
  private final int shieldedTransInPendingMaxCounts =
      Args.getInstance().getShieldedTransInPendingMaxCounts();
  @Getter
  @Setter
  public boolean eventPluginLoaded = false;
  private int maxTransactionPendingSize = Args.getInstance().getMaxTransactionPendingSize();
  @Autowired(required = false)
  @Getter
  private TransactionCache transactionCache;
  @Autowired
  private KhaosDatabase khaosDb;
  @Getter
  @Autowired
  private RevokingDatabase revokingStore;
  @Getter
  private SessionOptional session = SessionOptional.instance();
  @Getter
  @Setter
  private boolean isSyncMode;

  // map<Long, IncrementalMerkleTree>
  @Getter
  @Setter
  private String netType;
  @Getter
  @Setter
  private ProposalController proposalController;
  @Getter
  @Setter
  private MerkleContainer merkleContainer;
  private ExecutorService validateSignService;
  private boolean isRunRePushThread = true;
  private boolean isRunTriggerCapsuleProcessThread = true;
  private BlockingQueue<TransactionCapsule> pushTransactionQueue = new LinkedBlockingQueue<>();
  @Getter
  private Cache<Sha256Hash, Boolean> transactionIdCache = CacheBuilder
      .newBuilder().maximumSize(TX_ID_CACHE_SIZE)
      .expireAfterWrite(1, TimeUnit.HOURS).recordStats().build();
  @Autowired
  private AccountStateCallBack accountStateCallBack;
  @Autowired
  private TrieService trieService;
  private Set<String> ownerAddressSet = new HashSet<>();
  @Getter
  @Autowired
  private MortgageService mortgageService;
  @Autowired
  private Consensus consensus;
  @Autowired
  @Getter
  private ChainBaseManager chainBaseManager;
  // transactions cache
  private BlockingQueue<TransactionCapsule> pendingTransactions;
  @Getter
  private AtomicInteger shieldedTransInPendingCounts = new AtomicInteger(0);
  // transactions popped
  private List<TransactionCapsule> poppedTransactions =
      Collections.synchronizedList(Lists.newArrayList());
  // the capacity is equal to Integer.MAX_VALUE default
  private BlockingQueue<TransactionCapsule> rePushTransactions;
  private BlockingQueue<TriggerCapsule> triggerCapsuleQueue;
  // log filter
  private boolean isRunFilterProcessThread = true;
  private BlockingQueue<FilterTriggerCapsule> filterCapsuleQueue;

  @Getter
  private volatile long latestSolidityNumShutDown;

  @Getter
  private final ThreadLocal<Histogram.Timer> blockedTimer = new ThreadLocal<>();

  /**
   * Cycle thread to rePush Transactions
   */
  private Runnable rePushLoop =
      () -> {
        while (isRunRePushThread) {
          TransactionCapsule tx = null;
          try {
            tx = getRePushTransactions().peek();
            if (tx != null) {
              this.rePush(tx);
            } else {
              TimeUnit.MILLISECONDS.sleep(SLEEP_TIME_OUT);
            }
          } catch (Throwable ex) {
            if (ex instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            logger.error("unknown exception happened in rePush loop", ex);
            if (tx != null) {
              Metrics.counterInc(MetricKeys.Counter.TXS, 1,
                  MetricLabels.Counter.TXS_FAIL, MetricLabels.Counter.TXS_FAIL_ERROR);
            }
          } finally {
            if (tx != null && getRePushTransactions().remove(tx)) {
              Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, -1,
                  MetricLabels.Gauge.QUEUE_REPUSH);
            }
          }
        }
      };
  private Runnable triggerCapsuleProcessLoop =
      () -> {
        while (isRunTriggerCapsuleProcessThread) {
          try {
            TriggerCapsule triggerCapsule = triggerCapsuleQueue.poll(1, TimeUnit.SECONDS);
            if (triggerCapsule != null) {
              triggerCapsule.processTrigger();
            }
          } catch (InterruptedException ex) {
            logger.info(ex.getMessage());
            Thread.currentThread().interrupt();
          } catch (Throwable throwable) {
            logger.error("unknown throwable happened in process capsule loop", throwable);
          }
        }
      };

  private Runnable filterProcessLoop =
      () -> {
        while (isRunFilterProcessThread) {
          try {
            FilterTriggerCapsule filterCapsule = filterCapsuleQueue.poll(1, TimeUnit.SECONDS);
            if (filterCapsule != null) {
              filterCapsule.processFilterTrigger();
            }
          } catch (InterruptedException e) {
            logger.error("filterProcessLoop get InterruptedException, error is {}", e.getMessage());
            Thread.currentThread().interrupt();
          } catch (Throwable throwable) {
            logger.error("unknown throwable happened in filterProcessLoop: ", throwable);
          }
        }
      };

  private Comparator downComparator = (Comparator<TransactionCapsule>) (o1, o2) -> Long
      .compare(o2.getOrder(), o1.getOrder());

  public WitnessStore getWitnessStore() {
    return chainBaseManager.getWitnessStore();
  }

  public boolean needToUpdateAsset() {
    return getDynamicPropertiesStore().getTokenUpdateDone() == 0L;
  }

  public boolean needToMoveAbi() {
    return getDynamicPropertiesStore().getAbiMoveDone() == 0L;
  }

  private boolean needToLoadEnergyPriceHistory() {
    return getDynamicPropertiesStore().getEnergyPriceHistoryDone() == 0L;
  }

  public boolean needToSetBlackholePermission() {
    return getDynamicPropertiesStore().getSetBlackholeAccountPermission() == 0L;
  }

  private void resetBlackholeAccountPermission() {
    AccountCapsule blackholeAccount = getAccountStore().getBlackhole();

    byte[] zeroAddress = new byte[21];
    zeroAddress[0] = Wallet.getAddressPreFixByte();
    Permission owner = AccountCapsule
        .createDefaultOwnerPermission(ByteString.copyFrom(zeroAddress));
    blackholeAccount.updatePermissions(owner, null, null);
    getAccountStore().put(blackholeAccount.getAddress().toByteArray(), blackholeAccount);

    getDynamicPropertiesStore().saveSetBlackholePermission(1);
  }

  public DynamicPropertiesStore getDynamicPropertiesStore() {
    return chainBaseManager.getDynamicPropertiesStore();
  }

  public DelegationStore getDelegationStore() {
    return chainBaseManager.getDelegationStore();
  }

  public IncrementalMerkleTreeStore getMerkleTreeStore() {
    return chainBaseManager.getMerkleTreeStore();
  }

  public WitnessScheduleStore getWitnessScheduleStore() {
    return chainBaseManager.getWitnessScheduleStore();
  }

  public DelegatedResourceStore getDelegatedResourceStore() {
    return chainBaseManager.getDelegatedResourceStore();
  }

  public DelegatedResourceAccountIndexStore getDelegatedResourceAccountIndexStore() {
    return chainBaseManager.getDelegatedResourceAccountIndexStore();
  }

  public CodeStore getCodeStore() {
    return chainBaseManager.getCodeStore();
  }

  public ContractStore getContractStore() {
    return chainBaseManager.getContractStore();
  }

  public VotesStore getVotesStore() {
    return chainBaseManager.getVotesStore();
  }

  public ProposalStore getProposalStore() {
    return chainBaseManager.getProposalStore();
  }

  public ExchangeStore getExchangeStore() {
    return chainBaseManager.getExchangeStore();
  }

  public ExchangeV2Store getExchangeV2Store() {
    return chainBaseManager.getExchangeV2Store();
  }

  public StorageRowStore getStorageRowStore() {
    return chainBaseManager.getStorageRowStore();
  }

  public BlockIndexStore getBlockIndexStore() {
    return chainBaseManager.getBlockIndexStore();
  }

  public BlockingQueue<TransactionCapsule> getPendingTransactions() {
    return this.pendingTransactions;
  }

  public List<TransactionCapsule> getPoppedTransactions() {
    return this.poppedTransactions;
  }

  public BlockingQueue<TransactionCapsule> getRePushTransactions() {
    return rePushTransactions;
  }

  public void stopRePushThread() {
    isRunRePushThread = false;
  }

  public void stopRePushTriggerThread() {
    isRunTriggerCapsuleProcessThread = false;
  }

  public void stopFilterProcessThread() {
    isRunFilterProcessThread = false;
  }

  @PostConstruct
  public void init() {
    ChainBaseManager.init(chainBaseManager);
    Message.setDynamicPropertiesStore(this.getDynamicPropertiesStore());
    mortgageService
        .initStore(chainBaseManager.getWitnessStore(), chainBaseManager.getDelegationStore(),
            chainBaseManager.getDynamicPropertiesStore(), chainBaseManager.getAccountStore());
    accountStateCallBack.setChainBaseManager(chainBaseManager);
    trieService.setChainBaseManager(chainBaseManager);
    revokingStore.disable();
    revokingStore.check();
    this.setProposalController(ProposalController.createInstance(this));
    this.setMerkleContainer(
        merkleContainer.createInstance(chainBaseManager.getMerkleTreeStore(),
            chainBaseManager.getMerkleTreeIndexStore()));
    if (Args.getInstance().isOpenTransactionSort()) {
      this.pendingTransactions = new PriorityBlockingQueue(2000, downComparator);
      this.rePushTransactions = new PriorityBlockingQueue<>(2000, downComparator);
    } else {
      this.pendingTransactions = new LinkedBlockingQueue<>();
      this.rePushTransactions = new LinkedBlockingQueue<>();
    }
    this.triggerCapsuleQueue = new LinkedBlockingQueue<>();
    this.filterCapsuleQueue = new LinkedBlockingQueue<>();
    chainBaseManager.setMerkleContainer(getMerkleContainer());
    chainBaseManager.setMortgageService(mortgageService);
    this.initGenesis();
    try {
      this.khaosDb.start(chainBaseManager.getBlockById(
          getDynamicPropertiesStore().getLatestBlockHeaderHash()));
    } catch (ItemNotFoundException e) {
      logger.error(
          "Can not find Dynamic highest block from DB! \nnumber={} \nhash={}",
          getDynamicPropertiesStore().getLatestBlockHeaderNumber(),
          getDynamicPropertiesStore().getLatestBlockHeaderHash());
      logger.error(
          "Please delete database directory({}) and restart",
          Args.getInstance().getOutputDirectory());
      System.exit(1);
    } catch (BadItemException e) {
      logger.error("DB data broken! {}", e);
      logger.error(
          "Please delete database directory({}) and restart",
          Args.getInstance().getOutputDirectory());
      System.exit(1);
    }
    getChainBaseManager().getForkController().init(this.chainBaseManager);

    if (Args.getInstance().isNeedToUpdateAsset() && needToUpdateAsset()) {
      new AssetUpdateHelper(chainBaseManager).doWork();
    }

    if (needToMoveAbi()) {
      new MoveAbiHelper(chainBaseManager).doWork();
    }

    if (needToLoadEnergyPriceHistory()) {
      new EnergyPriceHistoryLoader(chainBaseManager).doWork();
    }

    if (needToSetBlackholePermission()) {
      resetBlackholeAccountPermission();
    }

    //for test only
    chainBaseManager.getDynamicPropertiesStore().updateDynamicStoreByConfig();

    long headNum = chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber();
    logger.info("current headNum is: {}", headNum);
    revokingStore.enable();
    validateSignService = Executors
        .newFixedThreadPool(Args.getInstance().getValidateSignThreadNum());
    Thread rePushThread = new Thread(rePushLoop);
    rePushThread.setDaemon(true);
    rePushThread.start();
    // add contract event listener for subscribing
    if (Args.getInstance().isEventSubscribe()) {
      startEventSubscribing();
      Thread triggerCapsuleProcessThread = new Thread(triggerCapsuleProcessLoop);
      triggerCapsuleProcessThread.setDaemon(true);
      triggerCapsuleProcessThread.start();
    }

    // start json rpc filter process
    if (CommonParameter.getInstance().isJsonRpcFilterEnabled()) {
      Thread filterProcessThread = new Thread(filterProcessLoop);
      filterProcessThread.start();
    }

    //initStoreFactory
    prepareStoreFactory();
    //initActuatorCreator
    ActuatorCreator.init();
    TransactionRegister.registerActuator();


    long exitHeight = CommonParameter.getInstance().getShutdownBlockHeight();
    long exitCount = CommonParameter.getInstance().getShutdownBlockCount();

    if (exitCount > 0 && (exitHeight < 0 || exitHeight > headNum + exitCount)) {
      CommonParameter.getInstance().setShutdownBlockHeight(headNum + exitCount);
    }

    if (CommonParameter.getInstance().getShutdownBlockHeight() < headNum) {
      logger.info("ShutDownBlockHeight {} is less than headNum {},ignored.",
          CommonParameter.getInstance().getShutdownBlockHeight(), headNum);
      CommonParameter.getInstance().setShutdownBlockHeight(-1);
    }
    // init
    latestSolidityNumShutDown = CommonParameter.getInstance().getShutdownBlockHeight();
  }

  /**
   * init genesis block.
   */
  public void initGenesis() {
    chainBaseManager.initGenesis();
    BlockCapsule genesisBlock = chainBaseManager.getGenesisBlock();

    if (chainBaseManager.containBlock(genesisBlock.getBlockId())) {
      Args.getInstance().setChainId(genesisBlock.getBlockId().toString());
    } else {
      if (chainBaseManager.hasBlocks()) {
        logger.error(
            "genesis block modify, please delete database directory({}) and restart",
            Args.getInstance().getOutputDirectory());
        System.exit(1);
      } else {
        logger.info("create genesis block");
        Args.getInstance().setChainId(genesisBlock.getBlockId().toString());

        chainBaseManager.getBlockStore().put(genesisBlock.getBlockId().getBytes(), genesisBlock);
        chainBaseManager.getBlockIndexStore().put(genesisBlock.getBlockId());

        logger.info(SAVE_BLOCK + genesisBlock);
        // init Dynamic Properties Store
        chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(0);
        chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderHash(
            genesisBlock.getBlockId().getByteString());
        chainBaseManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(
            genesisBlock.getTimeStamp());
        this.initAccount();
        this.initWitness();
        this.khaosDb.start(genesisBlock);
        this.updateRecentBlock(genesisBlock);
        initAccountHistoryBalance();
      }
    }
  }

  /**
   * save account into database.
   */
  public void initAccount() {
    final CommonParameter parameter = CommonParameter.getInstance();
    final GenesisBlock genesisBlockArg = parameter.getGenesisBlock();
    genesisBlockArg
        .getAssets()
        .forEach(
            account -> {
              account.setAccountType("Normal"); // to be set in conf
              final AccountCapsule accountCapsule =
                  new AccountCapsule(
                      account.getAccountName(),
                      ByteString.copyFrom(account.getAddress()),
                      account.getAccountType(),
                      account.getBalance());
              chainBaseManager.getAccountStore().put(account.getAddress(), accountCapsule);
              chainBaseManager.getAccountIdIndexStore().put(accountCapsule);
              chainBaseManager.getAccountIndexStore().put(accountCapsule);
            });
  }

  public void initAccountHistoryBalance() {
    BlockCapsule genesis = chainBaseManager.getGenesisBlock();
    BlockBalanceTraceCapsule genesisBlockBalanceTraceCapsule =
        new BlockBalanceTraceCapsule(genesis);
    List<TransactionCapsule> transactionCapsules = genesis.getTransactions();
    for (TransactionCapsule transactionCapsule : transactionCapsules) {
      BalanceContract.TransferContract transferContract = transactionCapsule.getTransferContract();
      BalanceContract.TransactionBalanceTrace.Operation operation =
          BalanceContract.TransactionBalanceTrace.Operation.newBuilder()
              .setOperationIdentifier(0)
              .setAddress(transferContract.getToAddress())
              .setAmount(transferContract.getAmount())
              .build();

      BalanceContract.TransactionBalanceTrace transactionBalanceTrace =
          BalanceContract.TransactionBalanceTrace.newBuilder()
              .setTransactionIdentifier(transactionCapsule.getTransactionId().getByteString())
              .setType(TransferContract.name())
              .setStatus(SUCCESS.name())
              .addOperation(operation)
              .build();
      genesisBlockBalanceTraceCapsule.addTransactionBalanceTrace(transactionBalanceTrace);

      chainBaseManager.getAccountTraceStore().recordBalanceWithBlock(
          transferContract.getToAddress().toByteArray(), 0, transferContract.getAmount());
    }

    chainBaseManager.getBalanceTraceStore()
        .put(Longs.toByteArray(0), genesisBlockBalanceTraceCapsule);
  }

  /**
   * save witnesses into database.
   */
  private void initWitness() {
    final CommonParameter commonParameter = Args.getInstance();
    final GenesisBlock genesisBlockArg = commonParameter.getGenesisBlock();
    genesisBlockArg
        .getWitnesses()
        .forEach(
            key -> {
              byte[] keyAddress = key.getAddress();
              ByteString address = ByteString.copyFrom(keyAddress);

              final AccountCapsule accountCapsule;
              if (!chainBaseManager.getAccountStore().has(keyAddress)) {
                accountCapsule = new AccountCapsule(ByteString.EMPTY,
                    address, AccountType.AssetIssue, 0L);
              } else {
                accountCapsule = chainBaseManager.getAccountStore().getUnchecked(keyAddress);
              }
              accountCapsule.setIsWitness(true);
              chainBaseManager.getAccountStore().put(keyAddress, accountCapsule);

              final WitnessCapsule witnessCapsule =
                  new WitnessCapsule(address, key.getVoteCount(), key.getUrl());
              witnessCapsule.setIsJobs(true);
              chainBaseManager.getWitnessStore().put(keyAddress, witnessCapsule);
            });
  }

  public AccountStore getAccountStore() {
    return chainBaseManager.getAccountStore();
  }

  public AccountAssetStore getAccountAssetStore() {
    return chainBaseManager.getAccountAssetStore();
  }

  public AccountIndexStore getAccountIndexStore() {
    return chainBaseManager.getAccountIndexStore();
  }

  void validateTapos(TransactionCapsule transactionCapsule) throws TaposException {
    byte[] refBlockHash = transactionCapsule.getInstance()
        .getRawData().getRefBlockHash().toByteArray();
    byte[] refBlockNumBytes = transactionCapsule.getInstance()
        .getRawData().getRefBlockBytes().toByteArray();
    try {
      byte[] blockHash = chainBaseManager.getRecentBlockStore().get(refBlockNumBytes).getData();
      if (!Arrays.equals(blockHash, refBlockHash)) {
        String str = String.format(
            "Tapos failed, different block hash, %s, %s , recent block %s, "
                + "solid block %s head block %s",
            ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
            Hex.toHexString(blockHash),
            chainBaseManager.getSolidBlockId().getString(),
            chainBaseManager.getHeadBlockId().getString()).toString();
        logger.info(str);
        throw new TaposException(str);
      }
    } catch (ItemNotFoundException e) {
      String str = String
          .format("Tapos failed, block not found, ref block %s, %s , solid block %s head block %s",
              ByteArray.toLong(refBlockNumBytes), Hex.toHexString(refBlockHash),
              chainBaseManager.getSolidBlockId().getString(),
              chainBaseManager.getHeadBlockId().getString()).toString();
      logger.info(str);
      throw new TaposException(str);
    }
  }

  void validateCommon(TransactionCapsule transactionCapsule)
      throws TransactionExpirationException, TooBigTransactionException {
    if (transactionCapsule.getData().length > Constant.TRANSACTION_MAX_BYTE_SIZE) {
      throw new TooBigTransactionException(
          "too big transaction, the size is " + transactionCapsule.getData().length + " bytes");
    }
    long transactionExpiration = transactionCapsule.getExpiration();
    long headBlockTime = chainBaseManager.getHeadBlockTimeStamp();
    if (transactionExpiration <= headBlockTime
        || transactionExpiration > headBlockTime + Constant.MAXIMUM_TIME_UNTIL_EXPIRATION) {
      throw new TransactionExpirationException(
          "transaction expiration, transaction expiration time is " + transactionExpiration
              + ", but headBlockTime is " + headBlockTime);
    }
  }

  void validateDup(TransactionCapsule transactionCapsule) throws DupTransactionException {
    if (containsTransaction(transactionCapsule)) {
      logger.debug(ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes()));
      throw new DupTransactionException("dup trans");
    }
  }

  private boolean containsTransaction(TransactionCapsule transactionCapsule) {
    return containsTransaction(transactionCapsule.getTransactionId().getBytes());
  }


  private boolean containsTransaction(byte[] transactionId) {
    if (transactionCache != null) {
      return transactionCache.has(transactionId);
    }

    return chainBaseManager.getTransactionStore()
        .has(transactionId);
  }

  /**
   * push transaction into pending.
   */
  public boolean pushTransaction(final TransactionCapsule unx)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, DupTransactionException, TaposException,
      TooBigTransactionException, TransactionExpirationException,
      ReceiptCheckErrException, VMIllegalException, TooBigTransactionResultException {

    if (isShieldedTransaction(unx.getInstance()) && !Args.getInstance()
        .isFullNodeAllowShieldedTransactionArgs()) {
      return true;
    }

    pushTransactionQueue.add(unx);
    Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, 1,
        MetricLabels.Gauge.QUEUE_QUEUED);
    try {
      if (!unx.validateSignature(chainBaseManager.getAccountStore(),
          chainBaseManager.getDynamicPropertiesStore())) {
        throw new ValidateSignatureException("trans sig validate failed");
      }

      synchronized (this) {
        if (isShieldedTransaction(unx.getInstance())
            && shieldedTransInPendingCounts.get() >= shieldedTransInPendingMaxCounts) {
          return false;
        }
        if (!session.valid()) {
          session.setValue(revokingStore.buildSession());
        }

        try (ISession tmpSession = revokingStore.buildSession()) {
          processTransaction(unx, null);
          unx.setUnxTrace(null);
          pendingTransactions.add(unx);
          Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, 1,
              MetricLabels.Gauge.QUEUE_PENDING);
          tmpSession.merge();
        }
        if (isShieldedTransaction(unx.getInstance())) {
          shieldedTransInPendingCounts.incrementAndGet();
        }
      }
    } finally {
      if (pushTransactionQueue.remove(unx)) {
        Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, -1,
            MetricLabels.Gauge.QUEUE_QUEUED);
      }
    }
    return true;
  }

  public void consumeMultiSignFee(TransactionCapsule unx, TransactionTrace trace)
      throws AccountResourceInsufficientException {
    if (unx.getInstance().getSignatureCount() > 1) {
      long fee = getDynamicPropertiesStore().getMultiSignFee();

      List<Contract> contracts = unx.getInstance().getRawData().getContractList();
      for (Contract contract : contracts) {
        byte[] address = TransactionCapsule.getOwner(contract);
        AccountCapsule accountCapsule = getAccountStore().get(address);
        try {
          if (accountCapsule != null) {
            adjustBalance(getAccountStore(), accountCapsule, -fee);

            if (getDynamicPropertiesStore().supportBlackHoleOptimization()) {
              getDynamicPropertiesStore().burnUnw(fee);
            } else {
              adjustBalance(getAccountStore(), this.getAccountStore().getBlackhole(), +fee);
            }
          }
        } catch (BalanceInsufficientException e) {
          throw new AccountResourceInsufficientException(
              "Account Insufficient balance[" + fee + "] to MultiSign");
        }
      }

      trace.getReceipt().setMultiSignFee(fee);
    }
  }

  public void consumeBandwidth(TransactionCapsule unx, TransactionTrace trace)
      throws ContractValidateException, AccountResourceInsufficientException,
      TooBigTransactionResultException {
    BandwidthProcessor processor = new BandwidthProcessor(chainBaseManager);
    processor.consume(unx, trace);
  }


  /**
   * when switch fork need erase blocks on fork branch.
   */
  public synchronized void eraseBlock() {
    session.reset();
    try {
      BlockCapsule oldHeadBlock = chainBaseManager.getBlockById(
          getDynamicPropertiesStore().getLatestBlockHeaderHash());
      logger.info("start to erase block:" + oldHeadBlock);
      khaosDb.pop();
      revokingStore.fastPop();
      logger.info("end to erase block:" + oldHeadBlock);
      poppedTransactions.addAll(oldHeadBlock.getTransactions());
      Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, oldHeadBlock.getTransactions().size(),
          MetricLabels.Gauge.QUEUE_POPPED);

    } catch (ItemNotFoundException | BadItemException e) {
      logger.warn(e.getMessage(), e);
    }
  }

  public void pushVerifiedBlock(BlockCapsule block) throws ContractValidateException,
      ContractExeException, ValidateSignatureException, AccountResourceInsufficientException,
      TransactionExpirationException, TooBigTransactionException, DupTransactionException,
      TaposException, ValidateScheduleException, ReceiptCheckErrException,
      VMIllegalException, TooBigTransactionResultException, UnLinkedBlockException,
      NonCommonBlockException, BadNumberBlockException, BadBlockException, ZksnarkException,
      EventBloomException {
    block.generatedByMyself = true;
    long start = System.currentTimeMillis();
    pushBlock(block);
    logger.info("push block cost:{}ms, blockNum:{}, blockHash:{}, unx count:{}",
        System.currentTimeMillis() - start,
        block.getNum(),
        block.getBlockId(),
        block.getTransactions().size());
  }

  private void applyBlock(BlockCapsule block) throws ContractValidateException,
      ContractExeException, ValidateSignatureException, AccountResourceInsufficientException,
      TransactionExpirationException, TooBigTransactionException, DupTransactionException,
      TaposException, ValidateScheduleException, ReceiptCheckErrException,
      VMIllegalException, TooBigTransactionResultException,
      ZksnarkException, BadBlockException, EventBloomException {
    applyBlock(block, block.getTransactions());
  }

  private void applyBlock(BlockCapsule block, List<TransactionCapsule> txs)
      throws ContractValidateException, ContractExeException, ValidateSignatureException,
      AccountResourceInsufficientException, TransactionExpirationException,
      TooBigTransactionException, DupTransactionException, TaposException,
      ValidateScheduleException, ReceiptCheckErrException, VMIllegalException,
      TooBigTransactionResultException, ZksnarkException, BadBlockException, EventBloomException {
    processBlock(block, txs);
    chainBaseManager.getBlockStore().put(block.getBlockId().getBytes(), block);
    chainBaseManager.getBlockIndexStore().put(block.getBlockId());
    if (block.getTransactions().size() != 0) {
      chainBaseManager.getTransactionRetStore()
          .put(ByteArray.fromLong(block.getNum()), block.getResult());
    }

    updateFork(block);
    if (System.currentTimeMillis() - block.getTimeStamp() >= 60_000) {
      revokingStore.setMaxFlushCount(SnapshotManager.DEFAULT_MAX_FLUSH_COUNT);
      if (Args.getInstance().getShutdownBlockTime() != null
          && Args.getInstance().getShutdownBlockTime().getNextValidTimeAfter(
          new Date(block.getTimeStamp() - SnapshotManager.DEFAULT_MAX_FLUSH_COUNT * 1000 * 3))
          .compareTo(new Date(block.getTimeStamp())) <= 0) {
        revokingStore.setMaxFlushCount(SnapshotManager.DEFAULT_MIN_FLUSH_COUNT);
      }
      if (latestSolidityNumShutDown > 0 && latestSolidityNumShutDown - block.getNum()
          <= SnapshotManager.DEFAULT_MAX_FLUSH_COUNT) {
        revokingStore.setMaxFlushCount(SnapshotManager.DEFAULT_MIN_FLUSH_COUNT);
      }
    } else {
      revokingStore.setMaxFlushCount(SnapshotManager.DEFAULT_MIN_FLUSH_COUNT);
    }
  }

  private void switchFork(BlockCapsule newHead)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      ValidateScheduleException, AccountResourceInsufficientException, TaposException,
      TooBigTransactionException, TooBigTransactionResultException, DupTransactionException,
      TransactionExpirationException, NonCommonBlockException, ReceiptCheckErrException,
      VMIllegalException, ZksnarkException, BadBlockException, EventBloomException {

    MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_FORK_COUNT);
    Metrics.counterInc(MetricKeys.Counter.BLOCK_FORK, 1, MetricLabels.ALL);

    Pair<LinkedList<KhaosBlock>, LinkedList<KhaosBlock>> binaryTree;
    try {
      binaryTree =
          khaosDb.getBranch(
              newHead.getBlockId(), getDynamicPropertiesStore().getLatestBlockHeaderHash());
    } catch (NonCommonBlockException e) {
      Metrics.counterInc(MetricKeys.Counter.BLOCK_FORK, 1, MetricLabels.FAIL);
      MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_FAIL_FORK_COUNT);
      logger.info(
          "this is not the most recent common ancestor, "
              + "need to remove all blocks in the fork chain.");
      BlockCapsule tmp = newHead;
      while (tmp != null) {
        khaosDb.removeBlk(tmp.getBlockId());
        tmp = khaosDb.getBlock(tmp.getParentHash());
      }

      throw e;
    }

    if (CollectionUtils.isNotEmpty(binaryTree.getValue())) {
      while (!getDynamicPropertiesStore()
          .getLatestBlockHeaderHash()
          .equals(binaryTree.getValue().peekLast().getParentHash())) {
        reOrgContractTrigger();
        reOrgLogsFilter();
        eraseBlock();
      }
    }

    if (CollectionUtils.isNotEmpty(binaryTree.getKey())) {
      List<KhaosBlock> first = new ArrayList<>(binaryTree.getKey());
      Collections.reverse(first);
      for (KhaosBlock item : first) {
        Exception exception = null;
        // todo  process the exception carefully later
        try (ISession tmpSession = revokingStore.buildSession()) {
          applyBlock(item.getBlk().setSwitch(true));
          tmpSession.commit();
        } catch (AccountResourceInsufficientException
            | ValidateSignatureException
            | ContractValidateException
            | ContractExeException
            | TaposException
            | DupTransactionException
            | TransactionExpirationException
            | ReceiptCheckErrException
            | TooBigTransactionException
            | TooBigTransactionResultException
            | ValidateScheduleException
            | VMIllegalException
            | ZksnarkException
            | BadBlockException e) {
          logger.warn(e.getMessage(), e);
          exception = e;
          throw e;
        } finally {
          if (exception != null) {
            Metrics.counterInc(MetricKeys.Counter.BLOCK_FORK, 1, MetricLabels.FAIL);
            MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_FAIL_FORK_COUNT);
            logger.warn("switch back because exception thrown while switching forks. " + exception
                    .getMessage(),
                exception);
            first.forEach(khaosBlock -> khaosDb.removeBlk(khaosBlock.getBlk().getBlockId()));
            khaosDb.setHead(binaryTree.getValue().peekFirst());

            while (!getDynamicPropertiesStore()
                .getLatestBlockHeaderHash()
                .equals(binaryTree.getValue().peekLast().getParentHash())) {
              eraseBlock();
            }

            List<KhaosBlock> second = new ArrayList<>(binaryTree.getValue());
            Collections.reverse(second);
            for (KhaosBlock khaosBlock : second) {
              // todo  process the exception carefully later
              try (ISession tmpSession = revokingStore.buildSession()) {
                applyBlock(khaosBlock.getBlk().setSwitch(true));
                tmpSession.commit();
              } catch (AccountResourceInsufficientException
                  | ValidateSignatureException
                  | ContractValidateException
                  | ContractExeException
                  | TaposException
                  | DupTransactionException
                  | TransactionExpirationException
                  | TooBigTransactionException
                  | ValidateScheduleException
                  | ZksnarkException e) {
                logger.warn(e.getMessage(), e);
              }
            }
          }
        }
      }
    }

  }

  public List<TransactionCapsule> getVerifyTxs(BlockCapsule block) {

    if (pendingTransactions.size() == 0) {
      return block.getTransactions();
    }

    List<TransactionCapsule> txs = new ArrayList<>();
    Set<String> txIds = new HashSet<>();
    Set<String> multiAddresses = new HashSet<>();

    pendingTransactions.forEach(capsule -> {
      String txId = Hex.toHexString(capsule.getTransactionId().getBytes());
      if (isMultiSignTransaction(capsule.getInstance())) {
        Contract contract = capsule.getInstance().getRawData().getContract(0);
        String address = Hex.toHexString(TransactionCapsule.getOwner(contract));
        multiAddresses.add(address);
      } else {
        txIds.add(txId);
      }
    });

    block.getTransactions().forEach(capsule -> {
      Contract contract = capsule.getInstance().getRawData().getContract(0);
      String address = Hex.toHexString(TransactionCapsule.getOwner(contract));
      String txId = Hex.toHexString(capsule.getTransactionId().getBytes());
      if (multiAddresses.contains(address) || !txIds.contains(txId)) {
        txs.add(capsule);
      } else {
        capsule.setVerified(true);
      }
    });

    return txs;
  }

  /**
   * save a block.
   */
  public synchronized void pushBlock(final BlockCapsule block)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      UnLinkedBlockException, ValidateScheduleException, AccountResourceInsufficientException,
      TaposException, TooBigTransactionException, TooBigTransactionResultException,
      DupTransactionException, TransactionExpirationException,
      BadNumberBlockException, BadBlockException, NonCommonBlockException,
      ReceiptCheckErrException, VMIllegalException, ZksnarkException, EventBloomException {
    Metrics.histogramObserve(blockedTimer.get());
    blockedTimer.remove();
    long headerNumber = getDynamicPropertiesStore().getLatestBlockHeaderNumber();
    if (block.getNum() <= headerNumber && khaosDb.containBlockInMiniStore(block.getBlockId())) {
      logger.info("Block {} is already exist.", block.getBlockId().getString());
      return;
    }
    final Histogram.Timer timer = Metrics.histogramStartTimer(
        MetricKeys.Histogram.BLOCK_PUSH_LATENCY);
    long start = System.currentTimeMillis();
    List<TransactionCapsule> txs = getVerifyTxs(block);
    logger.info("Block num: {}, re-push-size: {}, pending-size: {}, "
            + "block-tx-size: {}, verify-tx-size: {}",
        block.getNum(), rePushTransactions.size(), pendingTransactions.size(),
        block.getTransactions().size(), txs.size());

    if (CommonParameter.getInstance().getShutdownBlockTime() != null
        && CommonParameter.getInstance().getShutdownBlockTime()
        .isSatisfiedBy(new Date(block.getTimeStamp()))) {
      latestSolidityNumShutDown = block.getNum();
    }

    try (PendingManager pm = new PendingManager(this)) {

      if (!block.generatedByMyself) {
        if (!block.calcMerkleRoot().equals(block.getMerkleRoot())) {
          logger.warn(
              "The merkle root doesn't match, Calc result is "
                  + block.calcMerkleRoot()
                  + " , the headers is "
                  + block.getMerkleRoot());
          throw new BadBlockException("The merkle hash is not validated");
        }
        consensus.receiveBlock(block);
      }

      if (block.getTransactions().stream().filter(tran -> isShieldedTransaction(tran.getInstance()))
          .count() > SHIELDED_TRANS_IN_BLOCK_COUNTS) {
        throw new BadBlockException(
            "shielded transaction count > " + SHIELDED_TRANS_IN_BLOCK_COUNTS);
      }

      BlockCapsule newBlock;
      try {
        newBlock = this.khaosDb.push(block);
      } catch (UnLinkedBlockException e) {
        logger.error(
            "latestBlockHeaderHash:{}, latestBlockHeaderNumber:{}, latestSolidifiedBlockNum:{}",
            getDynamicPropertiesStore().getLatestBlockHeaderHash(),
            getDynamicPropertiesStore().getLatestBlockHeaderNumber(),
            getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
        throw e;
      }

      // DB don't need lower block
      if (getDynamicPropertiesStore().getLatestBlockHeaderHash() == null) {
        if (newBlock.getNum() != 0) {
          return;
        }
      } else {
        if (newBlock.getNum() <= headerNumber) {
          return;
        }

        // switch fork
        if (!newBlock
            .getParentHash()
            .equals(getDynamicPropertiesStore().getLatestBlockHeaderHash())) {
          logger.warn(
              "switch fork! new head num = {}, block id = {}",
              newBlock.getNum(),
              newBlock.getBlockId());

          logger.warn(
              "******** before switchFork ******* push block: "
                  + block.toString()
                  + ", new block:"
                  + newBlock.toString()
                  + ", dynamic head num: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
                  + ", dynamic head hash: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderHash()
                  + ", dynamic head timestamp: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp()
                  + ", khaosDb head: "
                  + khaosDb.getHead()
                  + ", khaosDb miniStore size: "
                  + khaosDb.getMiniStore().size()
                  + ", khaosDb unlinkMiniStore size: "
                  + khaosDb.getMiniUnlinkedStore().size());

          switchFork(newBlock);
          logger.info(SAVE_BLOCK + newBlock);

          logger.warn(
              "******** after switchFork ******* push block: "
                  + block.toString()
                  + ", new block:"
                  + newBlock.toString()
                  + ", dynamic head num: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
                  + ", dynamic head hash: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderHash()
                  + ", dynamic head timestamp: "
                  + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderTimestamp()
                  + ", khaosDb head: "
                  + khaosDb.getHead()
                  + ", khaosDb miniStore size: "
                  + khaosDb.getMiniStore().size()
                  + ", khaosDb unlinkMiniStore size: "
                  + khaosDb.getMiniUnlinkedStore().size());

          return;
        }
        try (ISession tmpSession = revokingStore.buildSession()) {

          long oldSolidNum =
              chainBaseManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();

          applyBlock(newBlock, txs);
          tmpSession.commit();
          // if event subscribe is enabled, post block trigger to queue
          postBlockTrigger(newBlock);
          // if event subscribe is enabled, post solidity trigger to queue
          postSolidityTrigger(oldSolidNum,
              getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
        } catch (Throwable throwable) {
          logger.error(throwable.getMessage(), throwable);
          khaosDb.removeBlk(block.getBlockId());
          throw throwable;
        }
      }
      logger.info(SAVE_BLOCK + newBlock);
    }
    //clear ownerAddressSet
    if (CollectionUtils.isNotEmpty(ownerAddressSet)) {
      Set<String> result = new HashSet<>();
      for (TransactionCapsule transactionCapsule : rePushTransactions) {
        filterOwnerAddress(transactionCapsule, result);
      }
      for (TransactionCapsule transactionCapsule : pushTransactionQueue) {
        filterOwnerAddress(transactionCapsule, result);
      }
      ownerAddressSet.clear();
      ownerAddressSet.addAll(result);
    }

    long cost = System.currentTimeMillis() - start;
    MetricsUtil.meterMark(MetricsKey.BLOCKCHAIN_BLOCK_PROCESS_TIME, cost);

    logger.info("pushBlock block number:{}, cost/txs:{}/{} {}",
            block.getNum(), cost, block.getTransactions().size(), cost > 1000);

    Metrics.histogramObserve(timer);
  }

  public void updateDynamicProperties(BlockCapsule block) {

    chainBaseManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderHash(block.getBlockId().getByteString());

    chainBaseManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderNumber(block.getNum());
    chainBaseManager.getDynamicPropertiesStore()
        .saveLatestBlockHeaderTimestamp(block.getTimeStamp());
    revokingStore.setMaxSize((int) (
        chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
            - chainBaseManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum()
            + 1));
    khaosDb.setMaxSize((int)
        (chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
            - chainBaseManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum()
            + 1));
    Metrics.gaugeSet(MetricKeys.Gauge.HEADER_HEIGHT, block.getNum());
    Metrics.gaugeSet(MetricKeys.Gauge.HEADER_TIME, block.getTimeStamp());
  }

  /**
   * Get the fork branch.
   */
  public LinkedList<BlockId> getBlockChainHashesOnFork(final BlockId forkBlockHash)
      throws NonCommonBlockException {
    final Pair<LinkedList<KhaosBlock>, LinkedList<KhaosBlock>> branch =
        this.khaosDb.getBranch(
            getDynamicPropertiesStore().getLatestBlockHeaderHash(), forkBlockHash);

    LinkedList<KhaosBlock> blockCapsules = branch.getValue();

    if (blockCapsules.isEmpty()) {
      logger.info("empty branch {}", forkBlockHash);
      return Lists.newLinkedList();
    }

    LinkedList<BlockId> result = blockCapsules.stream()
        .map(KhaosBlock::getBlk)
        .map(BlockCapsule::getBlockId)
        .collect(Collectors.toCollection(LinkedList::new));

    result.add(blockCapsules.peekLast().getBlk().getParentBlockId());

    return result;
  }

  /**
   * Process transaction.
   */
  public TransactionInfo processTransaction(final TransactionCapsule unxCap, BlockCapsule blockCap)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, TransactionExpirationException,
      TooBigTransactionException, TooBigTransactionResultException,
      DupTransactionException, TaposException, ReceiptCheckErrException, VMIllegalException {
    if (unxCap == null) {
      return null;
    }
    Contract contract = unxCap.getInstance().getRawData().getContract(0);

    final Histogram.Timer requestTimer = Metrics.histogramStartTimer(
        MetricKeys.Histogram.PROCESS_TRANSACTION_LATENCY,
        Objects.nonNull(blockCap) ? MetricLabels.BLOCK : MetricLabels.UNX,
        contract.getType().name());

    long start = System.currentTimeMillis();

    if (Objects.nonNull(blockCap)) {
      chainBaseManager.getBalanceTraceStore().initCurrentTransactionBalanceTrace(unxCap);
    }

    validateTapos(unxCap);
    validateCommon(unxCap);

    if (unxCap.getInstance().getRawData().getContractList().size() != 1) {
      throw new ContractSizeNotEqualToOneException(
          "act size should be exactly 1, this is extend feature");
    }

    validateDup(unxCap);

    if (!unxCap.validateSignature(chainBaseManager.getAccountStore(),
        chainBaseManager.getDynamicPropertiesStore())) {
      throw new ValidateSignatureException("transaction signature validate failed");
    }

    TransactionTrace trace = new TransactionTrace(unxCap, StoreFactory.getInstance(),
        new RuntimeImpl());
    unxCap.setUnxTrace(trace);

    consumeBandwidth(unxCap, trace);
    consumeMultiSignFee(unxCap, trace);

    trace.init(blockCap, eventPluginLoaded);
    trace.checkIsConstant();
    trace.exec();

    if (Objects.nonNull(blockCap)) {
      trace.setResult();
      if (blockCap.hasWitnessSignature()) {
        if (trace.checkNeedRetry()) {
          String txId = Hex.toHexString(unxCap.getTransactionId().getBytes());
          logger.info("Retry for tx id: {}", txId);
          trace.init(blockCap, eventPluginLoaded);
          trace.checkIsConstant();
          trace.exec();
          trace.setResult();
          logger.info("Retry result for tx id: {}, tx resultCode in receipt: {}",
              txId, trace.getReceipt().getResult());
        }
        trace.check();
      }
    }

    trace.finalization();
    if (Objects.nonNull(blockCap) && getDynamicPropertiesStore().supportVM()) {
      unxCap.setResult(trace.getTransactionContext());
    }
    chainBaseManager.getTransactionStore().put(unxCap.getTransactionId().getBytes(), unxCap);

    Optional.ofNullable(transactionCache)
        .ifPresent(t -> t.put(unxCap.getTransactionId().getBytes(),
            new BytesCapsule(ByteArray.fromLong(unxCap.getBlockNum()))));

    TransactionInfoCapsule transactionInfo = TransactionUtil
        .buildTransactionInfoInstance(unxCap, blockCap, trace);

    // if event subscribe is enabled, post contract triggers to queue
    // only trigger when process block
    if (Objects.nonNull(blockCap) && !blockCap.isMerkleRootEmpty()) {
      String blockHash = blockCap.getBlockId().toString();
      postContractTrigger(trace, false, blockHash);
    }


    if (isMultiSignTransaction(unxCap.getInstance())) {
      ownerAddressSet.add(ByteArray.toHexString(TransactionCapsule.getOwner(contract)));
    }

    if (Objects.nonNull(blockCap)) {
      chainBaseManager.getBalanceTraceStore()
          .updateCurrentTransactionStatus(
              trace.getRuntimeResult().getResultCode().name());
      chainBaseManager.getBalanceTraceStore().resetCurrentTransactionTrace();
    }
    //set the sort order
    unxCap.setOrder(transactionInfo.getFee());
    if (!eventPluginLoaded) {
      unxCap.setUnxTrace(null);
    }
    long cost = System.currentTimeMillis() - start;
    if (cost > 100) {
      logger.info("Process transaction {} cost {}.",
             Hex.toHexString(transactionInfo.getId()), cost);
    }
    Metrics.histogramObserve(requestTimer);
    return transactionInfo.getInstance();
  }

  /**
   * Generate a block.
   */
  public synchronized BlockCapsule generateBlock(Miner miner, long blockTime, long timeout) {
    String address =  StringUtil.encode58Check(miner.getWitnessAddress().toByteArray());
    final Histogram.Timer timer = Metrics.histogramStartTimer(
        MetricKeys.Histogram.BLOCK_GENERATE_LATENCY, address);
    Metrics.histogramObserve(MetricKeys.Histogram.MINER_LATENCY,
        (System.currentTimeMillis() - blockTime) / Metrics.MILLISECONDS_PER_SECOND, address);
    long postponedUnwCount = 0;
    logger.info("Generate block {} begin", chainBaseManager.getHeadBlockNum() + 1);

    BlockCapsule blockCapsule = new BlockCapsule(chainBaseManager.getHeadBlockNum() + 1,
        chainBaseManager.getHeadBlockId(),
        blockTime, miner.getWitnessAddress());
    blockCapsule.generatedByMyself = true;
    session.reset();
    session.setValue(revokingStore.buildSession());

    accountStateCallBack.preExecute(blockCapsule);

    if (getDynamicPropertiesStore().getAllowMultiSign() == 1) {
      byte[] privateKeyAddress = miner.getPrivateKeyAddress().toByteArray();
      AccountCapsule witnessAccount = getAccountStore()
          .get(miner.getWitnessAddress().toByteArray());
      if (!Arrays.equals(privateKeyAddress, witnessAccount.getWitnessPermissionAddress())) {
        logger.warn("Witness permission is wrong");
        return null;
      }
    }

    TransactionRetCapsule transactionRetCapsule = new TransactionRetCapsule(blockCapsule);

    Set<String> accountSet = new HashSet<>();
    AtomicInteger shieldedTransCounts = new AtomicInteger(0);
    while (pendingTransactions.size() > 0 || rePushTransactions.size() > 0) {
      boolean fromPending = false;
      TransactionCapsule unx;
      if (pendingTransactions.size() > 0) {
        unx = pendingTransactions.peek();
        if (Args.getInstance().isOpenTransactionSort()) {
          TransactionCapsule unxRepush = rePushTransactions.peek();
          if (unxRepush == null || unx.getOrder() >= unxRepush.getOrder()) {
            fromPending = true;
          } else {
            unx = rePushTransactions.poll();
            Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, -1,
                MetricLabels.Gauge.QUEUE_REPUSH);
          }
        } else {
          fromPending = true;
        }
      } else {
        unx = rePushTransactions.poll();
        Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, -1,
            MetricLabels.Gauge.QUEUE_REPUSH);
      }

      if (unx == null) {
        //  transaction may be removed by rePushLoop.
        logger.warn("Unw is null,fromPending:{},pending:{},repush:{}.",
                fromPending, pendingTransactions.size(), rePushTransactions.size());
        continue;
      }
      if (System.currentTimeMillis() > timeout) {
        logger.warn("Processing transaction time exceeds the producing time.");
        break;
      }

      // check the block size
      if ((blockCapsule.getInstance().getSerializedSize() + unx.getSerializedSize() + 3)
          > ChainConstant.BLOCK_SIZE) {
        postponedUnwCount++;
        continue;
      }
      //shielded transaction
      if (isShieldedTransaction(unx.getInstance())
          && shieldedTransCounts.incrementAndGet() > SHIELDED_TRANS_IN_BLOCK_COUNTS) {
        continue;
      }
      //multi sign transaction
      Contract contract = unx.getInstance().getRawData().getContract(0);
      byte[] owner = TransactionCapsule.getOwner(contract);
      String ownerAddress = ByteArray.toHexString(owner);
      if (accountSet.contains(ownerAddress)) {
        continue;
      } else {
        if (isMultiSignTransaction(unx.getInstance())) {
          accountSet.add(ownerAddress);
        }
      }
      if (ownerAddressSet.contains(ownerAddress)) {
        unx.setVerified(false);
      }
      // apply transaction
      try (ISession tmpSession = revokingStore.buildSession()) {
        accountStateCallBack.preExeTrans();
        TransactionInfo result = processTransaction(unx, blockCapsule);
        accountStateCallBack.exeTransFinish();
        tmpSession.merge();
        blockCapsule.addTransaction(unx);
        if (Objects.nonNull(result)) {
          transactionRetCapsule.addTransactionInfo(result);
        }
        if (fromPending) {
          pendingTransactions.poll();
          Metrics.gaugeInc(MetricKeys.Gauge.MANAGER_QUEUE, -1,
              MetricLabels.Gauge.QUEUE_PENDING);
        }
      } catch (Exception e) {
        logger.error("Process unx {} failed when generating block: {}", unx.getTransactionId(),
            e.getMessage());
      }
    }

    accountStateCallBack.executeGenerateFinish();

    session.reset();

    logger.info("Generate block {} success, unxs:{}, pendingCount: {}, rePushCount: {},"
            + " postponedCount: {}",
        blockCapsule.getNum(), blockCapsule.getTransactions().size(),
        pendingTransactions.size(), rePushTransactions.size(), postponedUnwCount);

    blockCapsule.setMerkleRoot();
    blockCapsule.sign(miner.getPrivateKey());

    BlockCapsule capsule = new BlockCapsule(blockCapsule.getInstance());
    capsule.generatedByMyself = true;
    Metrics.histogramObserve(timer);
    return capsule;
  }

  private void filterOwnerAddress(TransactionCapsule transactionCapsule, Set<String> result) {
    Contract contract = transactionCapsule.getInstance().getRawData().getContract(0);
    byte[] owner = TransactionCapsule.getOwner(contract);
    String ownerAddress = ByteArray.toHexString(owner);
    if (ownerAddressSet.contains(ownerAddress)) {
      result.add(ownerAddress);
    }
  }

  private boolean isMultiSignTransaction(Transaction transaction) {
    Contract contract = transaction.getRawData().getContract(0);
    switch (contract.getType()) {
      case AccountPermissionUpdateContract: {
        return true;
      }
      default:
    }
    return false;
  }

  private boolean isShieldedTransaction(Transaction transaction) {
    Contract contract = transaction.getRawData().getContract(0);
    switch (contract.getType()) {
      case ShieldedTransferContract: {
        return true;
      }
      default:
        return false;
    }
  }

  public TransactionStore getTransactionStore() {
    return chainBaseManager.getTransactionStore();
  }

  public TransactionHistoryStore getTransactionHistoryStore() {
    return chainBaseManager.getTransactionHistoryStore();
  }

  public TransactionRetStore getTransactionRetStore() {
    return chainBaseManager.getTransactionRetStore();
  }

  public BlockStore getBlockStore() {
    return chainBaseManager.getBlockStore();
  }

  /**
   * process block.
   */
  private void processBlock(BlockCapsule block, List<TransactionCapsule> txs)
      throws ValidateSignatureException, ContractValidateException, ContractExeException,
      AccountResourceInsufficientException, TaposException, TooBigTransactionException,
      DupTransactionException, TransactionExpirationException, ValidateScheduleException,
      ReceiptCheckErrException, VMIllegalException, TooBigTransactionResultException,
      ZksnarkException, BadBlockException, EventBloomException {
    // todo set revoking db max size.

    // checkWitness
    if (!consensus.validBlock(block)) {
      throw new ValidateScheduleException("validateWitnessSchedule error");
    }

    chainBaseManager.getBalanceTraceStore().initCurrentBlockBalanceTrace(block);

    //reset BlockEnergyUsage
    chainBaseManager.getDynamicPropertiesStore().saveBlockEnergyUsage(0);
    //parallel check sign
    if (!block.generatedByMyself) {
      try {
        preValidateTransactionSign(txs);
      } catch (InterruptedException e) {
        logger.error("parallel check sign interrupted exception! block info: {}", block, e);
        Thread.currentThread().interrupt();
      }
    }

    TransactionRetCapsule transactionRetCapsule =
        new TransactionRetCapsule(block);
    try {
      merkleContainer.resetCurrentMerkleTree();
      accountStateCallBack.preExecute(block);
      for (TransactionCapsule transactionCapsule : block.getTransactions()) {
        transactionCapsule.setBlockNum(block.getNum());
        if (block.generatedByMyself) {
          transactionCapsule.setVerified(true);
        }
        accountStateCallBack.preExeTrans();
        TransactionInfo result = processTransaction(transactionCapsule, block);
        accountStateCallBack.exeTransFinish();
        if (Objects.nonNull(result)) {
          transactionRetCapsule.addTransactionInfo(result);
        }
      }
      accountStateCallBack.executePushFinish();
    } finally {
      accountStateCallBack.exceptionFinish();
    }
    merkleContainer.saveCurrentMerkleTreeAsBestMerkleTree(block.getNum());
    block.setResult(transactionRetCapsule);
    if (getDynamicPropertiesStore().getAllowAdaptiveEnergy() == 1) {
      EnergyProcessor energyProcessor = new EnergyProcessor(
          chainBaseManager.getDynamicPropertiesStore(), chainBaseManager.getAccountStore());
      energyProcessor.updateTotalEnergyAverageUsage();
      energyProcessor.updateAdaptiveTotalEnergyLimit();
    }

    payReward(block);

    if (chainBaseManager.getDynamicPropertiesStore().getNextMaintenanceTime()
        <= block.getTimeStamp()) {
      proposalController.processProposals();
      chainBaseManager.getForkController().reset();
    }

    if (!consensus.applyBlock(block)) {
      throw new BadBlockException("consensus apply block failed");
    }

    updateTransHashCache(block);
    updateRecentBlock(block);
    updateRecentTransaction(block);
    updateDynamicProperties(block);

    chainBaseManager.getBalanceTraceStore().resetCurrentBlockTrace();

    if (CommonParameter.getInstance().isJsonRpcFilterEnabled()) {
      Bloom blockBloom = chainBaseManager.getSectionBloomStore()
          .initBlockSection(transactionRetCapsule);
      chainBaseManager.getSectionBloomStore().write(block.getNum());
      block.setBloom(blockBloom);
    }
  }

  private void payReward(BlockCapsule block) {
    WitnessCapsule witnessCapsule =
        chainBaseManager.getWitnessStore().getUnchecked(block.getInstance().getBlockHeader()
            .getRawData().getWitnessAddress().toByteArray());
    if (getDynamicPropertiesStore().allowChangeDelegation()) {
      mortgageService.payBlockReward(witnessCapsule.getAddress().toByteArray(),
          getDynamicPropertiesStore().getWitnessPayPerBlock());
      mortgageService.payStandbyWitness();

      if (chainBaseManager.getDynamicPropertiesStore().supportTransactionFeePool()) {
        long transactionFeeReward = Math
            .floorDiv(chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool(),
                Constant.TRANSACTION_FEE_POOL_PERIOD);
        mortgageService.payTransactionFeeReward(witnessCapsule.getAddress().toByteArray(),
            transactionFeeReward);
        chainBaseManager.getDynamicPropertiesStore().saveTransactionFeePool(
            chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool()
                - transactionFeeReward);
      }
    } else {
      byte[] witness = block.getWitnessAddress().toByteArray();
      AccountCapsule account = getAccountStore().get(witness);
      account.setAllowance(account.getAllowance()
          + chainBaseManager.getDynamicPropertiesStore().getWitnessPayPerBlock());

      if (chainBaseManager.getDynamicPropertiesStore().supportTransactionFeePool()) {
        long transactionFeeReward = Math
            .floorDiv(chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool(),
                Constant.TRANSACTION_FEE_POOL_PERIOD);
        account.setAllowance(account.getAllowance() + transactionFeeReward);
        chainBaseManager.getDynamicPropertiesStore().saveTransactionFeePool(
            chainBaseManager.getDynamicPropertiesStore().getTransactionFeePool()
                - transactionFeeReward);
      }

      getAccountStore().put(account.createDbKey(), account);
    }
  }

  private void postSolidityLogContractTrigger(Long blockNum, Long lastSolidityNum) {
    if (blockNum > lastSolidityNum) {
      return;
    }
    BlockingQueue contractLogTriggersQueue = Args.getSolidityContractLogTriggerMap()
        .get(blockNum);
    while (!contractLogTriggersQueue.isEmpty()) {
      ContractLogTrigger triggerCapsule = (ContractLogTrigger) contractLogTriggersQueue.poll();
      if (triggerCapsule == null) {
        break;
      }
      if (containsTransaction(ByteArray.fromHexString(triggerCapsule
          .getTransactionId()))) {
        triggerCapsule.setTriggerName(Trigger.SOLIDITYLOG_TRIGGER_NAME);
        EventPluginLoader.getInstance().postSolidityLogTrigger(triggerCapsule);
      } else {
        logger.error("postSolidityLogContractTrigger txId={} not contains transaction",
            triggerCapsule.getTransactionId());
      }
    }
    Args.getSolidityContractLogTriggerMap().remove(blockNum);
  }

  private void postSolidityEventContractTrigger(Long blockNum, Long lastSolidityNum) {
    if (blockNum > lastSolidityNum) {
      return;
    }
    BlockingQueue contractEventTriggersQueue = Args.getSolidityContractEventTriggerMap()
        .get(blockNum);
    while (!contractEventTriggersQueue.isEmpty()) {
      ContractEventTrigger triggerCapsule = (ContractEventTrigger) contractEventTriggersQueue
          .poll();
      if (triggerCapsule == null) {
        break;
      }
      if (containsTransaction(ByteArray.fromHexString(triggerCapsule
          .getTransactionId()))) {
        triggerCapsule.setTriggerName(Trigger.SOLIDITYEVENT_TRIGGER_NAME);
        EventPluginLoader.getInstance().postSolidityEventTrigger(triggerCapsule);
      }
    }
    Args.getSolidityContractEventTriggerMap().remove(blockNum);
  }

  private void updateTransHashCache(BlockCapsule block) {
    for (TransactionCapsule transactionCapsule : block.getTransactions()) {
      this.transactionIdCache.put(transactionCapsule.getTransactionId(), true);
    }
  }

  public void updateRecentBlock(BlockCapsule block) {
    chainBaseManager.getRecentBlockStore().put(ByteArray.subArray(
        ByteArray.fromLong(block.getNum()), 6, 8),
        new BytesCapsule(ByteArray.subArray(block.getBlockId().getBytes(), 8, 16)));
  }

  public void updateRecentTransaction(BlockCapsule block) {
    List list = new ArrayList<>();
    block.getTransactions().forEach(capsule -> {
      list.add(capsule.getTransactionId().toString());
    });
    RecentTransactionItem item = new RecentTransactionItem(block.getNum(), list);
    chainBaseManager.getRecentTransactionStore().put(
            ByteArray.subArray(ByteArray.fromLong(block.getNum()), 6, 8),
            new BytesCapsule(JsonUtil.obj2Json(item).getBytes()));
  }

  public void updateFork(BlockCapsule block) {
    int blockVersion = block.getInstance().getBlockHeader().getRawData().getVersion();
    if (blockVersion > ChainConstant.BLOCK_VERSION) {
      logger.warn("newer block version found: " + blockVersion + ", YOU MUST UPGRADE unichain-core!");
    }
    chainBaseManager
        .getForkController().update(block);
  }

  public long getSyncBeginNumber() {
    logger.info("headNumber:"
        + chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber());
    logger.info(
        "syncBeginNumber:"
            + (chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
            - revokingStore.size()));
    logger.info("solidBlockNumber:"
        + chainBaseManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
    return chainBaseManager.getDynamicPropertiesStore().getLatestBlockHeaderNumber()
        - revokingStore.size();
  }

  public AssetIssueStore getAssetIssueStore() {
    return chainBaseManager.getAssetIssueStore();
  }


  public AssetIssueV2Store getAssetIssueV2Store() {
    return chainBaseManager.getAssetIssueV2Store();
  }

  public AccountIdIndexStore getAccountIdIndexStore() {
    return chainBaseManager.getAccountIdIndexStore();
  }

  public NullifierStore getNullifierStore() {
    return chainBaseManager.getNullifierStore();
  }

  public void closeAllStore() {
    logger.info("******** begin to close db ********");
    chainBaseManager.closeAllStore();
    logger.info("******** end to close db ********");
  }

  public void closeOneStore(IUnxChainBase database) {
    logger.info("******** begin to close " + database.getName() + " ********");
    try {
      database.close();
    } catch (Exception e) {
      logger.info("failed to close  " + database.getName() + ". " + e);
    } finally {
      logger.info("******** end to close " + database.getName() + " ********");
    }
  }

  public boolean isTooManyPending() {
    return getPendingTransactions().size() + getRePushTransactions().size()
        > maxTransactionPendingSize;
  }

  private void preValidateTransactionSign(List<TransactionCapsule> txs)
      throws InterruptedException, ValidateSignatureException {
    int transSize = txs.size();
    if (transSize <= 0) {
      return;
    }
    Histogram.Timer requestTimer = Metrics.histogramStartTimer(
        MetricKeys.Histogram.VERIFY_SIGN_LATENCY, MetricLabels.UNX);
    try {
      CountDownLatch countDownLatch = new CountDownLatch(transSize);
      List<Future<Boolean>> futures = new ArrayList<>(transSize);

      for (TransactionCapsule transaction : txs) {
        Future<Boolean> future = validateSignService
            .submit(new ValidateSignTask(transaction, countDownLatch, chainBaseManager));
        futures.add(future);
      }
      countDownLatch.await();

      for (Future<Boolean> future : futures) {
        try {
          future.get();
        } catch (ExecutionException e) {
          throw new ValidateSignatureException(e.getCause().getMessage());
        }
      }
    } finally {
      Metrics.histogramObserve(requestTimer);
    }
  }

  public void rePush(TransactionCapsule tx) {
    if (containsTransaction(tx)) {
      return;
    }

    try {
      this.pushTransaction(tx);
    } catch (ValidateSignatureException | ContractValidateException | ContractExeException
        | AccountResourceInsufficientException | VMIllegalException e) {
      logger.debug(e.getMessage(), e);
    } catch (DupTransactionException e) {
      logger.debug("pending manager: dup trans", e);
    } catch (TaposException e) {
      logger.debug("pending manager: tapos exception", e);
    } catch (TooBigTransactionException e) {
      logger.debug("too big transaction");
    } catch (TransactionExpirationException e) {
      logger.debug("expiration transaction");
    } catch (ReceiptCheckErrException e) {
      logger.debug("outOfSlotTime transaction");
    } catch (TooBigTransactionResultException e) {
      logger.debug("too big transaction result");
    }
  }

  public long getHeadBlockNum() {
    return getDynamicPropertiesStore().getLatestBlockHeaderNumber();
  }

  public void setCursor(Chainbase.Cursor cursor) {
    if (cursor == Chainbase.Cursor.PBFT) {
      long headNum = getHeadBlockNum();
      long pbftNum = chainBaseManager.getCommonDataBase().getLatestPbftBlockNum();
      revokingStore.setCursor(cursor, headNum - pbftNum);
    } else {
      revokingStore.setCursor(cursor);
    }
  }

  public void resetCursor() {
    revokingStore.setCursor(Chainbase.Cursor.HEAD, 0L);
  }

  private void startEventSubscribing() {

    try {
      eventPluginLoaded = EventPluginLoader.getInstance()
          .start(Args.getInstance().getEventPluginConfig());

      if (!eventPluginLoaded) {
        logger.error("failed to load eventPlugin");
      }

      FilterQuery eventFilter = Args.getInstance().getEventFilter();
      if (!Objects.isNull(eventFilter)) {
        EventPluginLoader.getInstance().setFilterQuery(eventFilter);
      }

    } catch (Exception e) {
      logger.error("{}", e);
    }
  }

  private void postSolidityFilter(final long oldSolidNum, final long latestSolidifiedBlockNumber) {
    if (oldSolidNum >= latestSolidifiedBlockNumber) {
      logger.warn("post solidity filter failed, oldSolidity: {} >= latestSolidity: {}",
          oldSolidNum, latestSolidifiedBlockNumber);
      return;
    }

    BlockCapsule blockCapsule;
    try {
      blockCapsule = chainBaseManager.getBlockByNum(latestSolidifiedBlockNumber);
    } catch (Exception e) {
      logger.error("postSolidityFilter getBlockByNum={} except, {}",
          latestSolidifiedBlockNumber, e.getMessage());
      return;
    }

    postBlockFilter(blockCapsule, true);
    postLogsFilter(blockCapsule, true, false);
  }

  private void postSolidityTrigger(final long oldSolidNum, final long latestSolidifiedBlockNumber) {
    if (eventPluginLoaded && EventPluginLoader.getInstance().isSolidityLogTriggerEnable()) {
      for (Long i : Args.getSolidityContractLogTriggerMap().keySet()) {
        postSolidityLogContractTrigger(i, latestSolidifiedBlockNumber);
      }
    }

    if (eventPluginLoaded && EventPluginLoader.getInstance().isSolidityEventTriggerEnable()) {
      for (Long i : Args.getSolidityContractEventTriggerMap().keySet()) {
        postSolidityEventContractTrigger(i, latestSolidifiedBlockNumber);
      }
    }

    if (eventPluginLoaded && EventPluginLoader.getInstance().isSolidityTriggerEnable()) {
      SolidityTriggerCapsule solidityTriggerCapsule
          = new SolidityTriggerCapsule(latestSolidifiedBlockNumber);

      BlockCapsule blockCapsule;
      try {
        blockCapsule = chainBaseManager.getBlockByNum(latestSolidifiedBlockNumber);
        solidityTriggerCapsule.setTimeStamp(blockCapsule.getTimeStamp());
      } catch (Exception e) {
        logger.error("postSolidityTrigger getBlockByNum={} except, {}",
            latestSolidifiedBlockNumber, e.getMessage());
      }

      boolean result = triggerCapsuleQueue.offer(solidityTriggerCapsule);
      if (!result) {
        logger.info("too many trigger, lost solidified trigger, "
            + "block number: {}", latestSolidifiedBlockNumber);
      }
    }

    if (CommonParameter.getInstance().isJsonRpcHttpSolidityNodeEnable()) {
      postSolidityFilter(oldSolidNum, latestSolidifiedBlockNumber);
    }
  }

  private void processTransactionTrigger(BlockCapsule newBlock) {
    List<TransactionCapsule> transactionCapsuleList = newBlock.getTransactions();

    // need to set eth compatible data from transactionInfoList
    if (EventPluginLoader.getInstance().isTransactionLogTriggerEthCompatible()) {
      TransactionInfoList transactionInfoList = TransactionInfoList.newBuilder().build();
      TransactionInfoList.Builder transactionInfoListBuilder = TransactionInfoList.newBuilder();

      try {
        TransactionRetCapsule result = chainBaseManager.getTransactionRetStore()
            .getTransactionInfoByBlockNum(ByteArray.fromLong(newBlock.getNum()));

        if (!Objects.isNull(result) && !Objects.isNull(result.getInstance())) {
          result.getInstance().getTransactioninfoList().forEach(
              transactionInfoListBuilder::addTransactionInfo
          );

          transactionInfoList = transactionInfoListBuilder.build();
        }
      } catch (BadItemException e) {
        logger.error("postBlockTrigger getTransactionInfoList blockNum={}, error is {}",
            newBlock.getNum(), e.getMessage());
      }

      if (transactionCapsuleList.size() == transactionInfoList.getTransactionInfoCount()) {
        long cumulativeEnergyUsed = 0;
        long cumulativeLogCount = 0;
        long energyUnitPrice = chainBaseManager.getDynamicPropertiesStore().getEnergyFee();

        for (int i = 0; i < transactionCapsuleList.size(); i++) {
          TransactionInfo transactionInfo = transactionInfoList.getTransactionInfo(i);
          TransactionCapsule transactionCapsule = transactionCapsuleList.get(i);
          // reset block num to ignore value is -1
          transactionCapsule.setBlockNum(newBlock.getNum());

          cumulativeEnergyUsed += postTransactionTrigger(transactionCapsule, newBlock, i,
              cumulativeEnergyUsed, cumulativeLogCount, transactionInfo, energyUnitPrice);

          cumulativeLogCount += transactionInfo.getLogCount();
        }
      } else {
        logger.error("postBlockTrigger blockNum={} has no transactions or "
                + "the sizes of transactionInfoList and transactionCapsuleList are not equal",
            newBlock.getNum());
        for (TransactionCapsule e : newBlock.getTransactions()) {
          postTransactionTrigger(e, newBlock);
        }
      }
    } else {
      for (TransactionCapsule e : newBlock.getTransactions()) {
        postTransactionTrigger(e, newBlock);
      }
    }
  }

  private void reOrgLogsFilter() {
    if (CommonParameter.getInstance().isJsonRpcHttpFullNodeEnable()) {
      logger.info("switch fork occurred, post reOrgLogsFilter");

      try {
        BlockCapsule oldHeadBlock = chainBaseManager.getBlockById(
            getDynamicPropertiesStore().getLatestBlockHeaderHash());
        postLogsFilter(oldHeadBlock, false, true);
      } catch (BadItemException | ItemNotFoundException e) {
        logger.error("block header hash does not exist or is bad: {}",
            getDynamicPropertiesStore().getLatestBlockHeaderHash());
      }
    }
  }

  private void postBlockFilter(final BlockCapsule blockCapsule, boolean solidified) {
    BlockFilterCapsule blockFilterCapsule = new BlockFilterCapsule(blockCapsule, solidified);
    if (!filterCapsuleQueue.offer(blockFilterCapsule)) {
      logger.info("too many filters, block filter lost: {}", blockCapsule.getBlockId());
    }
  }

  private void postLogsFilter(final BlockCapsule blockCapsule, boolean solidified,
      boolean removed) {
    if (!blockCapsule.getTransactions().isEmpty()) {
      long blockNumber = blockCapsule.getNum();
      List<TransactionInfo> transactionInfoList = new ArrayList<>();

      try {
        TransactionRetCapsule result = chainBaseManager.getTransactionRetStore()
            .getTransactionInfoByBlockNum(ByteArray.fromLong(blockNumber));

        if (!Objects.isNull(result) && !Objects.isNull(result.getInstance())) {
          transactionInfoList.addAll(result.getInstance().getTransactioninfoList());
        }
      } catch (BadItemException e) {
        logger.error("processLogsFilter getTransactionInfoList blockNum={}, error is {}",
            blockNumber, e.getMessage());
        return;
      }

      LogsFilterCapsule logsFilterCapsule = new LogsFilterCapsule(blockNumber,
          blockCapsule.getBlockId().toString(), blockCapsule.getBloom(), transactionInfoList,
          solidified, removed);

      if (!filterCapsuleQueue.offer(logsFilterCapsule)) {
        logger.info("too many filters, logs filter lost: {}", blockNumber);
      }
    }
  }

  private void postBlockTrigger(final BlockCapsule blockCapsule) {
    BlockCapsule newBlock = blockCapsule;

    // post block and logs for jsonrpc
    if (CommonParameter.getInstance().isJsonRpcHttpFullNodeEnable()) {
      postBlockFilter(blockCapsule, false);
      postLogsFilter(blockCapsule, false, false);
    }

    // process block trigger
    if (eventPluginLoaded && EventPluginLoader.getInstance().isBlockLogTriggerEnable()) {
      if (EventPluginLoader.getInstance().isBlockLogTriggerSolidified()) {
        long solidityBlkNum = getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
        try {
          newBlock = chainBaseManager
              .getBlockByNum(solidityBlkNum);
        } catch (Exception e) {
          logger.error("postBlockTrigger getBlockByNum blkNum={} except, error is {}",
              solidityBlkNum, e.getMessage());
        }
      }

      BlockLogTriggerCapsule blockLogTriggerCapsule = new BlockLogTriggerCapsule(newBlock);
      blockLogTriggerCapsule.setLatestSolidifiedBlockNumber(getDynamicPropertiesStore()
          .getLatestSolidifiedBlockNum());
      if (!triggerCapsuleQueue.offer(blockLogTriggerCapsule)) {
        logger.info("too many triggers, block trigger lost: {}", newBlock.getBlockId());
      }
    }

    // process transaction trigger
    if (eventPluginLoaded && EventPluginLoader.getInstance().isTransactionLogTriggerEnable()) {
      // set newBlock
      if (EventPluginLoader.getInstance().isTransactionLogTriggerSolidified()) {
        long solidityBlkNum = getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
        try {
          newBlock = chainBaseManager.getBlockByNum(solidityBlkNum);
        } catch (Exception e) {
          logger.error("postBlockTrigger getBlockByNum blkNum={} except, error is {}",
              solidityBlkNum, e.getMessage());
        }
      } else {
        // need to reset block
        newBlock = blockCapsule;
      }

      processTransactionTrigger(newBlock);
    }
  }

  // return energyUsageTotal of the current transaction
  // cumulativeEnergyUsed is the total of energy used before the current transaction
  private long postTransactionTrigger(final TransactionCapsule unxCap,
      final BlockCapsule blockCap, int index, long preCumulativeEnergyUsed,
      long cumulativeLogCount, final TransactionInfo transactionInfo, long energyUnitPrice) {
    TransactionLogTriggerCapsule unx = new TransactionLogTriggerCapsule(unxCap, blockCap,
        index, preCumulativeEnergyUsed, cumulativeLogCount, transactionInfo, energyUnitPrice);
    unx.setLatestSolidifiedBlockNumber(getDynamicPropertiesStore()
        .getLatestSolidifiedBlockNum());
    if (!triggerCapsuleQueue.offer(unx)) {
      logger.info("too many triggers, transaction trigger lost: {}", unxCap.getTransactionId());
    }

    return unx.getTransactionLogTrigger().getEnergyUsageTotal();
  }


  private void postTransactionTrigger(final TransactionCapsule unxCap,
      final BlockCapsule blockCap) {
    TransactionLogTriggerCapsule unx = new TransactionLogTriggerCapsule(unxCap, blockCap);
    unx.setLatestSolidifiedBlockNumber(getDynamicPropertiesStore()
        .getLatestSolidifiedBlockNum());
    if (!triggerCapsuleQueue.offer(unx)) {
      logger.info("too many triggers, transaction trigger lost: {}", unxCap.getTransactionId());
    }
  }

  private void reOrgContractTrigger() {
    if (eventPluginLoaded
        && (EventPluginLoader.getInstance().isContractEventTriggerEnable()
        || EventPluginLoader.getInstance().isContractLogTriggerEnable())) {
      logger.info("switchfork occurred, post reOrgContractTrigger");
      try {
        BlockCapsule oldHeadBlock = chainBaseManager.getBlockById(
            getDynamicPropertiesStore().getLatestBlockHeaderHash());
        for (TransactionCapsule unx : oldHeadBlock.getTransactions()) {
          postContractTrigger(unx.getUnxTrace(), true, oldHeadBlock.getBlockId().toString());
        }
      } catch (BadItemException | ItemNotFoundException e) {
        logger.error("block header hash does not exist or is bad: {}",
            getDynamicPropertiesStore().getLatestBlockHeaderHash());
      }
    }
  }

  private void postContractTrigger(final TransactionTrace trace, boolean remove, String blockHash) {
    boolean isContractTriggerEnable = EventPluginLoader.getInstance()
        .isContractEventTriggerEnable() || EventPluginLoader
        .getInstance().isContractLogTriggerEnable();
    boolean isSolidityContractTriggerEnable = EventPluginLoader.getInstance()
        .isSolidityEventTriggerEnable() || EventPluginLoader
        .getInstance().isSolidityLogTriggerEnable();
    if (eventPluginLoaded
        && (isContractTriggerEnable || isSolidityContractTriggerEnable)) {
      // be careful, trace.getRuntimeResult().getTriggerList() should never return null
      for (ContractTrigger trigger : trace.getRuntimeResult().getTriggerList()) {
        ContractTriggerCapsule contractTriggerCapsule = new ContractTriggerCapsule(trigger);
        contractTriggerCapsule.getContractTrigger().setRemoved(remove);
        contractTriggerCapsule.setLatestSolidifiedBlockNumber(getDynamicPropertiesStore()
            .getLatestSolidifiedBlockNum());
        contractTriggerCapsule.setBlockHash(blockHash);

        if (!triggerCapsuleQueue.offer(contractTriggerCapsule)) {
          logger.info("too many triggers, contract log trigger lost: {}",
              trigger.getTransactionId());
        }
      }
    }
  }

  private void prepareStoreFactory() {
    StoreFactory.init();
    StoreFactory.getInstance().setChainBaseManager(chainBaseManager);
  }

  public TransactionCapsule getTxFromPending(String txId) {
    AtomicReference<TransactionCapsule> transactionCapsule = new AtomicReference<>();
    Sha256Hash txHash = Sha256Hash.wrap(ByteArray.fromHexString(txId));
    pendingTransactions.forEach(tx -> {
      if (tx.getTransactionId().equals(txHash)) {
        transactionCapsule.set(tx);
        return;
      }
    });
    if (transactionCapsule.get() != null) {
      return transactionCapsule.get();
    }
    rePushTransactions.forEach(tx -> {
      if (tx.getTransactionId().equals(txHash)) {
        transactionCapsule.set(tx);
        return;
      }
    });
    return transactionCapsule.get();
  }

  public Collection<String> getTxListFromPending() {
    Set<String> result = new HashSet<>();
    pendingTransactions.forEach(tx -> {
      result.add(tx.getTransactionId().toString());
    });
    rePushTransactions.forEach(tx -> {
      result.add(tx.getTransactionId().toString());
    });
    return result;
  }

  public long getPendingSize() {
    long value = getPendingTransactions().size() + getRePushTransactions().size()
        + getPoppedTransactions().size();
    return value;
  }

  private static class ValidateSignTask implements Callable<Boolean> {

    private TransactionCapsule unx;
    private CountDownLatch countDownLatch;
    private ChainBaseManager manager;

    ValidateSignTask(TransactionCapsule unx, CountDownLatch countDownLatch,
        ChainBaseManager manager) {
      this.unx = unx;
      this.countDownLatch = countDownLatch;
      this.manager = manager;
    }

    @Override
    public Boolean call() throws ValidateSignatureException {
      try {
        unx.validateSignature(manager.getAccountStore(), manager.getDynamicPropertiesStore());
      } catch (ValidateSignatureException e) {
        throw e;
      } finally {
        countDownLatch.countDown();
      }
      return true;
    }
  }
}
