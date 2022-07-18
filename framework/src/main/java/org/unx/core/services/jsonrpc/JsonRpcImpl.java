package org.unx.core.services.jsonrpc;

import static org.unx.core.services.http.Util.setTransactionExtraData;
import static org.unx.core.services.http.Util.setTransactionPermissionId;
import static org.unx.core.services.jsonrpc.JsonRpcApiUtil.getEnergyUsageTotal;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.unx.api.GrpcAPI.BytesMessage;
import org.unx.api.GrpcAPI.Return;
import org.unx.api.GrpcAPI.Return.response_code;
import org.unx.api.GrpcAPI.TransactionExtention;
import org.unx.common.crypto.Hash;
import org.unx.common.logsfilter.capsule.BlockFilterCapsule;
import org.unx.common.logsfilter.capsule.LogsFilterCapsule;
import org.unx.common.runtime.vm.DataWord;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.ByteUtil;
import org.unx.core.Wallet;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.core.db.Manager;
import org.unx.core.db2.core.Chainbase;
import org.unx.core.exception.BadItemException;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.HeaderNotFound;
import org.unx.core.exception.ItemNotFoundException;
import org.unx.core.exception.JsonRpcInternalException;
import org.unx.core.exception.JsonRpcInvalidParamsException;
import org.unx.core.exception.JsonRpcInvalidRequestException;
import org.unx.core.exception.JsonRpcMethodNotFoundException;
import org.unx.core.exception.JsonRpcTooManyResultException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.services.NodeInfoService;
import org.unx.core.services.http.JsonFormat;
import org.unx.core.services.http.Util;
import org.unx.core.services.jsonrpc.filters.BlockFilterAndResult;
import org.unx.core.services.jsonrpc.filters.LogBlockQuery;
import org.unx.core.services.jsonrpc.filters.LogFilter;
import org.unx.core.services.jsonrpc.filters.LogFilterAndResult;
import org.unx.core.services.jsonrpc.filters.LogFilterWrapper;
import org.unx.core.services.jsonrpc.filters.LogMatch;
import org.unx.core.services.jsonrpc.types.BlockResult;
import org.unx.core.services.jsonrpc.types.BuildArguments;
import org.unx.core.services.jsonrpc.types.CallArguments;
import org.unx.core.services.jsonrpc.types.TransactionReceipt;
import org.unx.core.services.jsonrpc.types.TransactionResult;
import org.unx.core.store.StorageRowStore;
import org.unx.core.vm.program.Storage;
import org.unx.program.Version;
import org.unx.protos.Protocol.Account;
import org.unx.protos.Protocol.Block;
import org.unx.protos.Protocol.Transaction;
import org.unx.protos.Protocol.Transaction.Contract.ContractType;
import org.unx.protos.Protocol.TransactionInfo;
import org.unx.protos.contract.AssetIssueContractOuterClass.TransferAssetContract;
import org.unx.protos.contract.BalanceContract.TransferContract;
import org.unx.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract;
import org.unx.protos.contract.SmartContractOuterClass.SmartContract.ABI;
import org.unx.protos.contract.SmartContractOuterClass.SmartContractDataWrapper;
import org.unx.protos.contract.SmartContractOuterClass.TriggerSmartContract;

@Slf4j(topic = "API")
public class JsonRpcImpl implements JsonRpc {

  public enum RequestSource {
    FULLNODE,
    SOLIDITY,
    PBFT
  }

  private static final String FILTER_NOT_FOUND = "filter not found";
  public static final int EXPIRE_SECONDS = 5 * 60;
  /**
   * for log filter in Full Json-RPC
   */
  @Getter
  private static final Map<String, LogFilterAndResult> eventFilter2ResultFull =
      new ConcurrentHashMap<>();
  /**
   * for block in Full Json-RPC
   */
  @Getter
  private static final Map<String, BlockFilterAndResult> blockFilter2ResultFull =
      new ConcurrentHashMap<>();
  /**
   * for log filter in solidity Json-RPC
   */
  @Getter
  private static final Map<String, LogFilterAndResult> eventFilter2ResultSolidity =
      new ConcurrentHashMap<>();
  /**
   * for block in solidity Json-RPC
   */
  @Getter
  private static final Map<String, BlockFilterAndResult> blockFilter2ResultSolidity =
      new ConcurrentHashMap<>();

  public static final String HASH_REGEX = "(0x)?[a-zA-Z0-9]{64}$";

  public static final String EARLIEST_STR = "earliest";
  public static final String PENDING_STR = "pending";
  public static final String LATEST_STR = "latest";

  private static final String JSON_ERROR = "invalid json request";
  private static final String BLOCK_NUM_ERROR = "invalid block number";
  private static final String TAG_NOT_SUPPORT_ERROR = "TAG [earliest | pending] not supported";
  private static final String QUANTITY_NOT_SUPPORT_ERROR =
      "QUANTITY not supported, just support TAG as latest";

  /**
   * thread pool of query section bloom store
   */
  private final ExecutorService sectionExecutor;
  private final NodeInfoService nodeInfoService;
  private final Wallet wallet;
  private final Manager manager;

  public JsonRpcImpl(NodeInfoService nodeInfoService, Wallet wallet, Manager manager) {
    this.nodeInfoService = nodeInfoService;
    this.wallet = wallet;
    this.manager = manager;
    this.sectionExecutor = Executors.newFixedThreadPool(5);
  }

  public static void handleBLockFilter(BlockFilterCapsule blockFilterCapsule) {
    Iterator<Entry<String, BlockFilterAndResult>> it;

    if (blockFilterCapsule.isSolidified()) {
      it = getBlockFilter2ResultSolidity().entrySet().iterator();
    } else {
      it = getBlockFilter2ResultFull().entrySet().iterator();
    }

    while (it.hasNext()) {
      Entry<String, BlockFilterAndResult> entry = it.next();
      if (entry.getValue().isExpire()) {
        it.remove();
        continue;
      }
      entry.getValue().getResult().add(ByteArray.toJsonHex(blockFilterCapsule.getBlockHash()));
    }
  }

  public static void handleLogsFilter(LogsFilterCapsule logsFilterCapsule) {
    Iterator<Entry<String, LogFilterAndResult>> it;

    if (logsFilterCapsule.isSolidified()) {
      it = getEventFilter2ResultSolidity().entrySet().iterator();
    } else {
      it = getEventFilter2ResultFull().entrySet().iterator();
    }

    while (it.hasNext()) {
      Entry<String, LogFilterAndResult> entry = it.next();
      if (entry.getValue().isExpire()) {
        it.remove();
        continue;
      }

      LogFilterAndResult logFilterAndResult = entry.getValue();
      long fromBlock = logFilterAndResult.getLogFilterWrapper().getFromBlock();
      long toBlock = logFilterAndResult.getLogFilterWrapper().getToBlock();
      if (!(fromBlock <= logsFilterCapsule.getBlockNumber()
          && logsFilterCapsule.getBlockNumber() <= toBlock)) {
        continue;
      }

      if (logsFilterCapsule.getBloom() != null
          && !logFilterAndResult.getLogFilterWrapper().getLogFilter()
          .matchBloom(logsFilterCapsule.getBloom())) {
        continue;
      }

      LogFilter logFilter = logFilterAndResult.getLogFilterWrapper().getLogFilter();
      List<LogFilterElement> elements =
          LogMatch.matchBlock(logFilter, logsFilterCapsule.getBlockNumber(),
              logsFilterCapsule.getBlockHash(), logsFilterCapsule.getTxInfoList(),
              logsFilterCapsule.isRemoved());
      if (CollectionUtils.isNotEmpty(elements)) {
        logFilterAndResult.getResult().addAll(elements);
      }
    }
  }

  @Override
  public String web3ClientVersion() {
    Pattern shortVersion = Pattern.compile("(\\d\\.\\d).*");
    Matcher matcher = shortVersion.matcher(System.getProperty("java.version"));
    matcher.matches();

    return String.join("/", Arrays.asList(
        "UNX", "v" + Version.getVersion(),
        System.getProperty("os.name"),
        "Java" + matcher.group(1),
        Version.VERSION_NAME));
  }

  @Override
  public String web3Sha3(String data) throws JsonRpcInvalidParamsException {
    byte[] input;
    try {
      input = ByteArray.fromHexString(data);
    } catch (Exception e) {
      throw new JsonRpcInvalidParamsException("invalid input value");
    }

    byte[] result = Hash.sha3(input);
    return ByteArray.toJsonHex(result);
  }

  @Override
  public String ethGetBlockTransactionCountByHash(String blockHash)
      throws JsonRpcInvalidParamsException {
    Block b = getBlockByJsonHash(blockHash);
    if (b == null) {
      return null;
    }

    long n = b.getTransactionsList().size();
    return ByteArray.toJsonHex(n);
  }

  @Override
  public String ethGetBlockTransactionCountByNumber(String blockNumOrTag)
      throws JsonRpcInvalidParamsException {
    List<Transaction> list = wallet.getTransactionsByJsonBlockId(blockNumOrTag);
    if (list == null) {
      return null;
    }

    long n = list.size();
    return ByteArray.toJsonHex(n);
  }

  @Override
  public BlockResult ethGetBlockByHash(String blockHash, Boolean fullTransactionObjects)
      throws JsonRpcInvalidParamsException {
    final Block b = getBlockByJsonHash(blockHash);
    return getBlockResult(b, fullTransactionObjects);
  }

  @Override
  public BlockResult ethGetBlockByNumber(String blockNumOrTag, Boolean fullTransactionObjects)
      throws JsonRpcInvalidParamsException {
    final Block b = wallet.getByJsonBlockId(blockNumOrTag);
    return (b == null ? null : getBlockResult(b, fullTransactionObjects));
  }

  private byte[] hashToByteArray(String hash) throws JsonRpcInvalidParamsException {
    if (!Pattern.matches(HASH_REGEX, hash)) {
      throw new JsonRpcInvalidParamsException("invalid hash value");
    }

    byte[] bHash;
    try {
      bHash = ByteArray.fromHexString(hash);
    } catch (Exception e) {
      throw new JsonRpcInvalidParamsException(e.getMessage());
    }
    return bHash;
  }

  private Block getBlockByJsonHash(String blockHash) throws JsonRpcInvalidParamsException {
    byte[] bHash = hashToByteArray(blockHash);
    return wallet.getBlockById(ByteString.copyFrom(bHash));
  }

  private BlockResult getBlockResult(Block block, boolean fullTx) {
    if (block == null) {
      return null;
    }

    return new BlockResult(block, fullTx, wallet);
  }

  @Override
  public String getNetVersion() throws JsonRpcInternalException {
    return ethChainId();
  }

  @Override
  public String ethChainId() throws JsonRpcInternalException {
    // return hash of genesis block
    try {
      byte[] chainId = wallet.getBlockCapsuleByNum(0).getBlockId().getBytes();
      return ByteArray.toJsonHex(Arrays.copyOfRange(chainId, chainId.length - 4, chainId.length));
    } catch (Exception e) {
      throw new JsonRpcInternalException(e.getMessage());
    }
  }

  @Override
  public boolean isListening() {
    int activeConnectCount = nodeInfoService.getNodeInfo().getActiveConnectCount();
    return activeConnectCount >= 1;
  }

  @Override
  public String getProtocolVersion() {
    return ByteArray.toJsonHex(wallet.getNowBlock().getBlockHeader().getRawData().getVersion());
  }

  @Override
  public String getLatestBlockNum() {
    return ByteArray.toJsonHex(wallet.getNowBlock().getBlockHeader().getRawData().getNumber());
  }

  @Override
  public String getUnwBalance(String address, String blockNumOrTag)
      throws JsonRpcInvalidParamsException {
    if (EARLIEST_STR.equalsIgnoreCase(blockNumOrTag)
        || PENDING_STR.equalsIgnoreCase(blockNumOrTag)) {
      throw new JsonRpcInvalidParamsException(TAG_NOT_SUPPORT_ERROR);
    } else if (LATEST_STR.equalsIgnoreCase(blockNumOrTag)) {
      byte[] addressData = JsonRpcApiUtil.addressCompatibleToByteArray(address);

      Account account = Account.newBuilder().setAddress(ByteString.copyFrom(addressData)).build();
      Account reply = wallet.getAccount(account);
      long balance = 0;

      if (reply != null) {
        balance = reply.getBalance();
      }
      return ByteArray.toJsonHex(balance);
    } else {
      try {
        ByteArray.hexToBigInteger(blockNumOrTag);
      } catch (Exception e) {
        throw new JsonRpcInvalidParamsException(BLOCK_NUM_ERROR);
      }

      throw new JsonRpcInvalidParamsException(QUANTITY_NOT_SUPPORT_ERROR);
    }
  }

  private void callTriggerConstantContract(byte[] ownerAddressByte, byte[] contractAddressByte,
      long value, byte[] data, TransactionExtention.Builder unxExtBuilder,
      Return.Builder retBuilder)
      throws ContractValidateException, ContractExeException, HeaderNotFound, VMIllegalException {

    TriggerSmartContract triggerContract = JsonRpcApiUtil.triggerCallContract(
        ownerAddressByte,
        contractAddressByte,
        value,
        data,
        0,
        null
    );

    TransactionCapsule unxCap = wallet.createTransactionCapsule(triggerContract,
        ContractType.TriggerSmartContract);
    Transaction unx =
        wallet.triggerConstantContract(triggerContract, unxCap, unxExtBuilder, retBuilder);

    unxExtBuilder.setTransaction(unx);
    unxExtBuilder.setTxid(unxCap.getTransactionId().getByteString());
    unxExtBuilder.setResult(retBuilder);
    retBuilder.setResult(true).setCode(response_code.SUCCESS);
  }

  /**
   * @param data Hash of the method signature and encoded parameters. for example:
   * getMethodSign(methodName(uint256,uint256)) || data1 || data2
   */
  private String call(byte[] ownerAddressByte, byte[] contractAddressByte, long value,
      byte[] data) {

    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    TransactionExtention unxExt;

    try {
      callTriggerConstantContract(ownerAddressByte, contractAddressByte, value, data,
          unxExtBuilder, retBuilder);

    } catch (ContractValidateException | VMIllegalException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
          .setMessage(ByteString.copyFromUtf8(Wallet.CONTRACT_VALIDATE_ERROR + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn(Wallet.CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
    } catch (RuntimeException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn("When run constant call in VM, have RuntimeException: " + e.getMessage());
    } catch (Exception e) {
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
          .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.warn("Unknown exception caught: " + e.getMessage(), e);
    } finally {
      unxExt = unxExtBuilder.build();
    }

    String result = "0x";
    String code = unxExt.getResult().getCode().toString();
    if ("SUCCESS".equals(code)) {
      List<ByteString> list = unxExt.getConstantResultList();
      byte[] listBytes = new byte[0];
      for (ByteString bs : list) {
        listBytes = ByteUtil.merge(listBytes, bs.toByteArray());
      }
      result = ByteArray.toJsonHex(listBytes);
    } else {
      logger.error("trigger contract failed.");
    }

    return result;
  }

  @Override
  public String getStorageAt(String address, String storageIdx, String blockNumOrTag)
      throws JsonRpcInvalidParamsException {
    if (EARLIEST_STR.equalsIgnoreCase(blockNumOrTag)
        || PENDING_STR.equalsIgnoreCase(blockNumOrTag)) {
      throw new JsonRpcInvalidParamsException(TAG_NOT_SUPPORT_ERROR);
    } else if (LATEST_STR.equalsIgnoreCase(blockNumOrTag)) {
      byte[] addressByte = JsonRpcApiUtil.addressCompatibleToByteArray(address);

      // get contract from contractStore
      BytesMessage.Builder build = BytesMessage.newBuilder();
      BytesMessage bytesMessage = build.setValue(ByteString.copyFrom(addressByte)).build();
      SmartContract smartContract = wallet.getContract(bytesMessage);
      if (smartContract == null) {
        return ByteArray.toJsonHex(new byte[32]);
      }

      StorageRowStore store = manager.getStorageRowStore();
      Storage storage = new Storage(addressByte, store);
      storage.setContractVersion(smartContract.getVersion());

      DataWord value = storage.getValue(new DataWord(ByteArray.fromHexString(storageIdx)));
      return ByteArray.toJsonHex(value == null ? new byte[32] : value.getData());
    } else {
      try {
        ByteArray.hexToBigInteger(blockNumOrTag);
      } catch (Exception e) {
        throw new JsonRpcInvalidParamsException(BLOCK_NUM_ERROR);
      }

      throw new JsonRpcInvalidParamsException(QUANTITY_NOT_SUPPORT_ERROR);
    }
  }

  @Override
  public String getABIOfSmartContract(String contractAddress, String blockNumOrTag)
      throws JsonRpcInvalidParamsException {
    if (EARLIEST_STR.equalsIgnoreCase(blockNumOrTag)
        || PENDING_STR.equalsIgnoreCase(blockNumOrTag)) {
      throw new JsonRpcInvalidParamsException(TAG_NOT_SUPPORT_ERROR);
    } else if (LATEST_STR.equalsIgnoreCase(blockNumOrTag)) {
      byte[] addressData = JsonRpcApiUtil.addressCompatibleToByteArray(contractAddress);

      BytesMessage.Builder build = BytesMessage.newBuilder();
      BytesMessage bytesMessage = build.setValue(ByteString.copyFrom(addressData)).build();
      SmartContractDataWrapper contractDataWrapper = wallet.getContractInfo(bytesMessage);

      if (contractDataWrapper != null) {
        return ByteArray.toJsonHex(contractDataWrapper.getRuntimecode().toByteArray());
      } else {
        return "0x";
      }

    } else {
      try {
        ByteArray.hexToBigInteger(blockNumOrTag);
      } catch (Exception e) {
        throw new JsonRpcInvalidParamsException(BLOCK_NUM_ERROR);
      }

      throw new JsonRpcInvalidParamsException(QUANTITY_NOT_SUPPORT_ERROR);
    }
  }

  @Override
  public String getCoinbase() throws JsonRpcInternalException {
    String address = wallet.getCoinbase();

    if (StringUtils.isEmpty(address)) {
      throw new JsonRpcInternalException("etherbase must be explicitly specified");
    }

    return address;
  }

  // return energy fee
  @Override
  public String gasPrice() {
    return ByteArray.toJsonHex(wallet.getEnergyFee());
  }

  @Override
  public String estimateGas(CallArguments args) throws JsonRpcInvalidRequestException,
      JsonRpcInvalidParamsException, JsonRpcInternalException {
    byte[] ownerAddress = JsonRpcApiUtil.addressCompatibleToByteArray(args.getFrom());

    ContractType contractType = args.getContractType(wallet);
    if (contractType == ContractType.TransferContract) {
      buildTransferContractTransaction(ownerAddress, new BuildArguments(args));
      return "0x0";
    }

    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();

    try {
      byte[] contractAddress;

      if (contractType == ContractType.TriggerSmartContract) {
        contractAddress = JsonRpcApiUtil.addressCompatibleToByteArray(args.getTo());
      } else {
        contractAddress = new byte[0];
      }

      callTriggerConstantContract(ownerAddress,
          contractAddress,
          args.parseValue(),
          ByteArray.fromHexString(args.getData()),
          unxExtBuilder,
          retBuilder);

      return ByteArray.toJsonHex(unxExtBuilder.getEnergyUsed());
    } catch (ContractValidateException e) {
      String errString = "invalid contract";
      if (e.getMessage() != null) {
        errString = e.getMessage();
      }

      throw new JsonRpcInvalidRequestException(errString);
    } catch (Exception e) {
      String errString = JSON_ERROR;
      if (e.getMessage() != null) {
        errString = e.getMessage().replaceAll("[\"]", "'");
      }

      throw new JsonRpcInternalException(errString);
    }
  }

  @Override
  public TransactionResult getTransactionByHash(String txId) throws JsonRpcInvalidParamsException {
    ByteString transactionId = ByteString.copyFrom(hashToByteArray(txId));

    TransactionInfo transactionInfo = wallet.getTransactionInfoById(transactionId);
    if (transactionInfo == null) {
      TransactionCapsule transactionCapsule = wallet.getTransactionCapsuleById(transactionId);
      if (transactionCapsule == null) {
        return null;
      }

      BlockCapsule blockCapsule = wallet.getBlockCapsuleByNum(transactionCapsule.getBlockNum());
      if (blockCapsule == null) {
        return new TransactionResult(transactionCapsule.getInstance(), wallet);
      } else {
        int transactionIndex = JsonRpcApiUtil.getTransactionIndex(
            ByteArray.toHexString(transactionCapsule.getTransactionId().getBytes()),
            blockCapsule.getInstance().getTransactionsList());

        if (transactionIndex == -1) {
          return null;
        }

        long energyUsageTotal = 0;
        return new TransactionResult(blockCapsule, transactionIndex,
            transactionCapsule.getInstance(), energyUsageTotal,
            wallet.getEnergyFee(blockCapsule.getTimeStamp()), wallet);
      }
    } else {
      Block block = wallet.getBlockByNum(transactionInfo.getBlockNumber());
      if (block == null) {
        return null;
      }

      return formatTransactionResult(transactionInfo, block);
    }
  }

  private TransactionResult formatTransactionResult(TransactionInfo transactioninfo, Block block) {
    String txId = ByteArray.toHexString(transactioninfo.getId().toByteArray());

    Transaction transaction = null;
    int transactionIndex = -1;

    List<Transaction> txList = block.getTransactionsList();
    for (int index = 0; index < txList.size(); index++) {
      transaction = txList.get(index);
      if (JsonRpcApiUtil.getTxID(transaction).equals(txId)) {
        transactionIndex = index;
        break;
      }
    }

    if (transactionIndex == -1) {
      return null;
    }

    long energyUsageTotal = transactioninfo.getReceipt().getEnergyUsageTotal();
    BlockCapsule blockCapsule = new BlockCapsule(block);
    return new TransactionResult(blockCapsule, transactionIndex, transaction,
        energyUsageTotal, wallet.getEnergyFee(blockCapsule.getTimeStamp()), wallet);
  }

  private TransactionResult getTransactionByBlockAndIndex(Block block, String index)
      throws JsonRpcInvalidParamsException {
    int txIndex;
    try {
      txIndex = ByteArray.jsonHexToInt(index);
    } catch (Exception e) {
      throw new JsonRpcInvalidParamsException("invalid index value");
    }

    if (txIndex >= block.getTransactionsCount()) {
      return null;
    }

    Transaction transaction = block.getTransactions(txIndex);
    long energyUsageTotal = JsonRpcApiUtil.getEnergyUsageTotal(transaction, wallet);
    BlockCapsule blockCapsule = new BlockCapsule(block);

    return new TransactionResult(blockCapsule, txIndex, transaction, energyUsageTotal,
        wallet.getEnergyFee(blockCapsule.getTimeStamp()), wallet);
  }

  @Override
  public TransactionResult getTransactionByBlockHashAndIndex(String blockHash, String index)
      throws JsonRpcInvalidParamsException {
    final Block block = getBlockByJsonHash(blockHash);

    if (block == null) {
      return null;
    }

    return getTransactionByBlockAndIndex(block, index);
  }

  @Override
  public TransactionResult getTransactionByBlockNumberAndIndex(String blockNumOrTag, String index)
      throws JsonRpcInvalidParamsException {
    Block block = wallet.getByJsonBlockId(blockNumOrTag);
    if (block == null) {
      return null;
    }

    return getTransactionByBlockAndIndex(block, index);
  }

  @Override
  public TransactionReceipt getTransactionReceipt(String txId)
      throws JsonRpcInvalidParamsException {
    TransactionInfo transactionInfo =
        wallet.getTransactionInfoById(ByteString.copyFrom(hashToByteArray(txId)));
    if (transactionInfo == null) {
      return null;
    }

    Block block = wallet.getBlockByNum(transactionInfo.getBlockNumber());
    if (block == null) {
      return null;
    }

    return new TransactionReceipt(block, transactionInfo, wallet);
  }

  @Override
  public String getCall(CallArguments transactionCall, String blockNumOrTag)
      throws JsonRpcInvalidParamsException {
    if (EARLIEST_STR.equalsIgnoreCase(blockNumOrTag)
        || PENDING_STR.equalsIgnoreCase(blockNumOrTag)) {
      throw new JsonRpcInvalidParamsException(TAG_NOT_SUPPORT_ERROR);
    } else if (LATEST_STR.equalsIgnoreCase(blockNumOrTag)) {
      byte[] addressData = JsonRpcApiUtil.addressCompatibleToByteArray(transactionCall.getFrom());
      byte[] contractAddressData = JsonRpcApiUtil.addressCompatibleToByteArray(transactionCall.getTo());

      return call(addressData, contractAddressData, transactionCall.parseValue(),
          ByteArray.fromHexString(transactionCall.getData()));
    } else {
      try {
        ByteArray.hexToBigInteger(blockNumOrTag).longValue();
      } catch (Exception e) {
        throw new JsonRpcInvalidParamsException(BLOCK_NUM_ERROR);
      }

      throw new JsonRpcInvalidParamsException(QUANTITY_NOT_SUPPORT_ERROR);
    }
  }

  @Override
  public String getPeerCount() {
    // return the peer list count
    return ByteArray.toJsonHex(nodeInfoService.getNodeInfo().getPeerList().size());
  }

  @Override
  public Object getSyncingStatus() {
    if (nodeInfoService.getNodeInfo().getPeerList().isEmpty()) {
      return false;
    }

    long startingBlockNum = nodeInfoService.getNodeInfo().getBeginSyncNum();
    Block nowBlock = wallet.getNowBlock();
    long currentBlockNum = nowBlock.getBlockHeader().getRawData().getNumber();
    long diff = (System.currentTimeMillis()
        - nowBlock.getBlockHeader().getRawData().getTimestamp()) / 3000;
    diff = diff > 0 ? diff : 0;
    long highestBlockNum = currentBlockNum + diff; // estimated the highest block number

    return new SyncingResult(ByteArray.toJsonHex(startingBlockNum),
        ByteArray.toJsonHex(currentBlockNum),
        ByteArray.toJsonHex(highestBlockNum)
    );
  }

  @Override
  public BlockResult getUncleByBlockHashAndIndex(String blockHash, String index) {
    return null;
  }

  @Override
  public BlockResult getUncleByBlockNumberAndIndex(String blockNumOrTag, String index) {
    return null;
  }

  @Override
  public String getUncleCountByBlockHash(String blockHash) {
    return "0x0";
  }

  @Override
  public String getUncleCountByBlockNumber(String blockNumOrTag) {
    return "0x0";
  }

  @Override
  public List<Object> ethGetWork() {
    Block block = wallet.getNowBlock();
    String blockHash = null;

    if (block != null) {
      blockHash = ByteArray.toJsonHex(new BlockCapsule(block).getBlockId().getBytes());
    }

    return Arrays.asList(
        blockHash,
        null,
        null
    );
  }

  @Override
  public String getHashRate() {
    return "0x0";
  }

  @Override
  public boolean isMining() {
    return wallet.isMining();
  }

  @Override
  public String[] getAccounts() {
    return new String[0];
  }

  private TransactionJson buildCreateSmartContractTransaction(byte[] ownerAddress,
      BuildArguments args) throws JsonRpcInvalidParamsException, JsonRpcInvalidRequestException,
      JsonRpcInternalException {
    try {
      CreateSmartContract.Builder build = CreateSmartContract.newBuilder();

      build.setOwnerAddress(ByteString.copyFrom(ownerAddress));

      build.setCallTokenValue(args.getTokenValue())
          .setTokenId(args.getTokenId());

      ABI.Builder abiBuilder = ABI.newBuilder();
      if (StringUtils.isNotEmpty(args.getAbi())) {
        String abiStr = "{" + "\"entrys\":" + args.getAbi() + "}";
        JsonFormat.merge(abiStr, abiBuilder, args.isVisible());
      }

      SmartContract.Builder smartBuilder = SmartContract.newBuilder();
      smartBuilder
          .setAbi(abiBuilder)
          .setCallValue(args.parseValue())
          .setConsumeUserResourcePercent(args.getConsumeUserResourcePercent())
          .setOriginEnergyLimit(args.getOriginEnergyLimit());

      smartBuilder.setOriginAddress(ByteString.copyFrom(ownerAddress));

      // bytecode + parameter
      smartBuilder.setBytecode(ByteString.copyFrom(ByteArray.fromHexString(args.getData())));

      if (StringUtils.isNotEmpty(args.getName())) {
        smartBuilder.setName(args.getName());
      }

      build.setNewContract(smartBuilder);

      Transaction tx = wallet
          .createTransactionCapsule(build.build(), ContractType.CreateSmartContract).getInstance();
      Transaction.Builder txBuilder = tx.toBuilder();
      Transaction.raw.Builder rawBuilder = tx.getRawData().toBuilder();
      rawBuilder.setFeeLimit(args.parseGas() * wallet.getEnergyFee());

      txBuilder.setRawData(rawBuilder);
      tx = setTransactionPermissionId(args.getPermissionId(), txBuilder.build());

      TransactionJson transactionJson = new TransactionJson();
      transactionJson.setTransaction(JSON.parseObject(Util.printCreateTransaction(tx, false)));

      return transactionJson;
    } catch (JsonRpcInvalidParamsException e) {
      throw new JsonRpcInvalidParamsException(e.getMessage());
    } catch (ContractValidateException e) {
      throw new JsonRpcInvalidRequestException(e.getMessage());
    } catch (Exception e) {
      throw new JsonRpcInternalException(e.getMessage());
    }
  }

  // from and to should not be null
  private TransactionJson buildTriggerSmartContractTransaction(byte[] ownerAddress,
      BuildArguments args) throws JsonRpcInvalidParamsException, JsonRpcInvalidRequestException,
      JsonRpcInternalException {
    byte[] contractAddress = JsonRpcApiUtil.addressCompatibleToByteArray(args.getTo());

    TriggerSmartContract.Builder build = TriggerSmartContract.newBuilder();
    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();

    try {

      build.setOwnerAddress(ByteString.copyFrom(ownerAddress))
          .setContractAddress(ByteString.copyFrom(contractAddress));

      if (StringUtils.isNotEmpty(args.getData())) {
        build.setData(ByteString.copyFrom(ByteArray.fromHexString(args.getData())));
      } else {
        build.setData(ByteString.copyFrom(new byte[0]));
      }

      build.setCallTokenValue(args.getTokenValue())
          .setTokenId(args.getTokenId())
          .setCallValue(args.parseValue());

      Transaction tx = wallet
          .createTransactionCapsule(build.build(), ContractType.TriggerSmartContract).getInstance();

      Transaction.Builder txBuilder = tx.toBuilder();
      Transaction.raw.Builder rawBuilder = tx.getRawData().toBuilder();
      rawBuilder.setFeeLimit(args.parseGas() * wallet.getEnergyFee());
      txBuilder.setRawData(rawBuilder);

      Transaction unx = wallet
          .triggerContract(build.build(), new TransactionCapsule(txBuilder.build()), unxExtBuilder,
              retBuilder);
      unx = setTransactionPermissionId(args.getPermissionId(), unx);
      unxExtBuilder.setTransaction(unx);
    } catch (JsonRpcInvalidParamsException e) {
      throw new JsonRpcInvalidParamsException(e.getMessage());
    } catch (ContractValidateException e) {
      throw new JsonRpcInvalidRequestException(e.getMessage());
    } catch (Exception e) {
      String errString = JSON_ERROR;
      if (e.getMessage() != null) {
        errString = e.getMessage().replaceAll("[\"]", "'");
      }

      throw new JsonRpcInternalException(errString);
    }

    String jsonString = Util.printTransaction(unxExtBuilder.build().getTransaction(),
        args.isVisible());
    TransactionJson transactionJson = new TransactionJson();
    transactionJson.setTransaction(JSON.parseObject(jsonString));

    return transactionJson;
  }

  private TransactionJson createTransactionJson(GeneratedMessageV3.Builder<?> build,
      ContractType contractTyp, BuildArguments args)
      throws JsonRpcInvalidRequestException, JsonRpcInternalException {
    try {
      Transaction tx = wallet
          .createTransactionCapsule(build.build(), contractTyp)
          .getInstance();
      tx = setTransactionPermissionId(args.getPermissionId(), tx);
      tx = setTransactionExtraData(args.getExtraData(), tx, args.isVisible());

      TransactionJson transactionJson = new TransactionJson();
      transactionJson
          .setTransaction(JSON.parseObject(Util.printCreateTransaction(tx, args.isVisible())));

      return transactionJson;
    } catch (ContractValidateException e) {
      throw new JsonRpcInvalidRequestException(e.getMessage());
    } catch (Exception e) {
      throw new JsonRpcInternalException(e.getMessage());
    }
  }

  private TransactionJson buildTransferContractTransaction(byte[] ownerAddress,
      BuildArguments args) throws JsonRpcInvalidParamsException, JsonRpcInvalidRequestException,
      JsonRpcInternalException {
    long amount = args.parseValue();

    TransferContract.Builder build = TransferContract.newBuilder();
    build.setOwnerAddress(ByteString.copyFrom(ownerAddress))
        .setToAddress(ByteString.copyFrom(JsonRpcApiUtil.addressCompatibleToByteArray(args.getTo())))
        .setAmount(amount);

    return createTransactionJson(build, ContractType.TransferContract, args);
  }

  // tokenId and tokenValue should not be null
  private TransactionJson buildTransferAssetContractTransaction(byte[] ownerAddress,
      BuildArguments args) throws JsonRpcInvalidParamsException, JsonRpcInvalidRequestException,
      JsonRpcInternalException {
    byte[] tokenIdArr = ByteArray.fromString(String.valueOf(args.getTokenId()));
    if (tokenIdArr == null) {
      throw new JsonRpcInvalidParamsException("invalid param value: invalid tokenId");
    }

    TransferAssetContract.Builder build = TransferAssetContract.newBuilder();
    build.setOwnerAddress(ByteString.copyFrom(ownerAddress))
        .setToAddress(ByteString.copyFrom(JsonRpcApiUtil.addressCompatibleToByteArray(args.getTo())))
        .setAssetName(ByteString.copyFrom(tokenIdArr))
        .setAmount(args.getTokenValue());

    return createTransactionJson(build, ContractType.TransferAssetContract, args);
  }

  public RequestSource getSource() {
    Chainbase.Cursor cursor = wallet.getCursor();
    switch (cursor) {
      case SOLIDITY:
        return RequestSource.SOLIDITY;
      case PBFT:
        return RequestSource.PBFT;
      default:
        return RequestSource.FULLNODE;
    }
  }

  public void disableInPBFT(String method) throws JsonRpcMethodNotFoundException {
    if (getSource() == RequestSource.PBFT) {
      String msg = String.format("the method %s does not exist/is not available in PBFT", method);
      throw new JsonRpcMethodNotFoundException(msg);
    }
  }

  @Override
  public TransactionJson buildTransaction(BuildArguments args)
      throws JsonRpcInvalidParamsException, JsonRpcInvalidRequestException,
      JsonRpcInternalException, JsonRpcMethodNotFoundException {

    if (getSource() != RequestSource.FULLNODE) {
      String msg = String
          .format("the method buildTransaction does not exist/is not available in %s",
              getSource().toString());
      throw new JsonRpcMethodNotFoundException(msg);
    }

    byte[] fromAddressData;
    try {
      fromAddressData = JsonRpcApiUtil.addressCompatibleToByteArray(args.getFrom());
    } catch (JsonRpcInvalidParamsException e) {
      throw new JsonRpcInvalidRequestException(JSON_ERROR);
    }

    // check possible ContractType
    ContractType contractType = args.getContractType(wallet);
    switch (contractType.getNumber()) {
      case ContractType.CreateSmartContract_VALUE:
        return buildCreateSmartContractTransaction(fromAddressData, args);
      case ContractType.TriggerSmartContract_VALUE:
        return buildTriggerSmartContractTransaction(fromAddressData, args);
      case ContractType.TransferContract_VALUE:
        return buildTransferContractTransaction(fromAddressData, args);
      case ContractType.TransferAssetContract_VALUE:
        return buildTransferAssetContractTransaction(fromAddressData, args);
      default:
        break;
    }

    return null;
  }

  @Override
  public boolean ethSubmitWork(String nonceHex, String headerHex, String digestHex)
      throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_submitWork does not exist/is not available");
  }

  @Override
  public String ethSendRawTransaction(String rawData) throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_sendRawTransaction does not exist/is not available");
  }

  @Override
  public String ethSendTransaction(CallArguments args) throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_sendTransaction does not exist/is not available");
  }

  @Override
  public String ethSign(String address, String msg) throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_sign does not exist/is not available");
  }

  @Override
  public String ethSignTransaction(CallArguments transactionArgs)
      throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_signTransaction does not exist/is not available");
  }

  @Override
  public String parityNextNonce(String address) throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method parity_nextNonce does not exist/is not available");
  }

  @Override
  public String getSendTransactionCountOfAddress(String address, String blockNumOrTag)
      throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_getTransactionCount does not exist/is not available");
  }

  @Override
  public String[] getCompilers() throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_getCompilers does not exist/is not available");
  }

  @Override
  public CompilationResult ethCompileSolidity(String contract)
      throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_compileSolidity does not exist/is not available");
  }

  @Override
  public CompilationResult ethCompileLLL(String contract) throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_compileLLL does not exist/is not available");
  }

  @Override
  public CompilationResult ethCompileSerpent(String contract)
      throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_compileSerpent does not exist/is not available");
  }

  @Override
  public CompilationResult ethSubmitHashrate(String hashrate, String id)
      throws JsonRpcMethodNotFoundException {
    throw new JsonRpcMethodNotFoundException(
        "the method eth_submitHashrate does not exist/is not available");
  }

  @Override
  public String newFilter(FilterRequest fr) throws JsonRpcInvalidParamsException,
      JsonRpcMethodNotFoundException {
    disableInPBFT("eth_newFilter");

    Map<String, LogFilterAndResult> eventFilter2Result;
    if (getSource() == RequestSource.FULLNODE) {
      eventFilter2Result = eventFilter2ResultFull;
    } else {
      eventFilter2Result = eventFilter2ResultSolidity;
    }

    long currentMaxFullNum = wallet.getNowBlock().getBlockHeader().getRawData().getNumber();
    LogFilterAndResult logFilterAndResult = new LogFilterAndResult(fr, currentMaxFullNum, wallet);
    String filterID = JsonRpcApiUtil.generateFilterId();
    eventFilter2Result.put(filterID, logFilterAndResult);
    return ByteArray.toJsonHex(filterID);
  }

  @Override
  public String newBlockFilter() throws JsonRpcMethodNotFoundException {
    disableInPBFT("eth_newBlockFilter");

    Map<String, BlockFilterAndResult> blockFilter2Result;
    if (getSource() == RequestSource.FULLNODE) {
      blockFilter2Result = blockFilter2ResultFull;
    } else {
      blockFilter2Result = blockFilter2ResultSolidity;
    }

    BlockFilterAndResult filterAndResult = new BlockFilterAndResult();
    String filterID = JsonRpcApiUtil.generateFilterId();
    blockFilter2Result.put(filterID, filterAndResult);
    return ByteArray.toJsonHex(filterID);
  }

  @Override
  public boolean uninstallFilter(String filterId) throws ItemNotFoundException,
      JsonRpcMethodNotFoundException {
    disableInPBFT("eth_uninstallFilter");

    Map<String, BlockFilterAndResult> blockFilter2Result;
    Map<String, LogFilterAndResult> eventFilter2Result;
    if (getSource() == RequestSource.FULLNODE) {
      blockFilter2Result = blockFilter2ResultFull;
      eventFilter2Result = eventFilter2ResultFull;
    } else {
      blockFilter2Result = blockFilter2ResultSolidity;
      eventFilter2Result = eventFilter2ResultSolidity;
    }

    filterId = ByteArray.fromHex(filterId);
    if (eventFilter2Result.containsKey(filterId)) {
      eventFilter2Result.remove(filterId);
    } else if (blockFilter2Result.containsKey(filterId)) {
      blockFilter2Result.remove(filterId);
    } else {
      throw new ItemNotFoundException(FILTER_NOT_FOUND);
    }

    return true;
  }

  @Override
  public Object[] getFilterChanges(String filterId) throws ItemNotFoundException,
      JsonRpcMethodNotFoundException {
    disableInPBFT("eth_getFilterChanges");

    Map<String, BlockFilterAndResult> blockFilter2Result;
    Map<String, LogFilterAndResult> eventFilter2Result;
    if (getSource() == RequestSource.FULLNODE) {
      blockFilter2Result = blockFilter2ResultFull;
      eventFilter2Result = eventFilter2ResultFull;
    } else {
      blockFilter2Result = blockFilter2ResultSolidity;
      eventFilter2Result = eventFilter2ResultSolidity;
    }

    filterId = ByteArray.fromHex(filterId);

    return getFilterResult(filterId, blockFilter2Result, eventFilter2Result);
  }

  @Override
  public LogFilterElement[] getLogs(FilterRequest fr) throws JsonRpcInvalidParamsException,
      ExecutionException, InterruptedException, BadItemException, ItemNotFoundException,
      JsonRpcMethodNotFoundException, JsonRpcTooManyResultException {
    disableInPBFT("eth_getLogs");

    long currentMaxBlockNum = wallet.getNowBlock().getBlockHeader().getRawData().getNumber();
    //convert FilterRequest to LogFilterWrapper
    LogFilterWrapper logFilterWrapper = new LogFilterWrapper(fr, currentMaxBlockNum, wallet);

    return getLogsByLogFilterWrapper(logFilterWrapper, currentMaxBlockNum);
  }

  @Override
  public LogFilterElement[] getFilterLogs(String filterId) throws ExecutionException,
      InterruptedException, BadItemException, ItemNotFoundException,
      JsonRpcMethodNotFoundException, JsonRpcTooManyResultException {
    disableInPBFT("eth_getFilterLogs");

    Map<String, LogFilterAndResult> eventFilter2Result;
    if (getSource() == RequestSource.FULLNODE) {
      eventFilter2Result = eventFilter2ResultFull;
    } else {
      eventFilter2Result = eventFilter2ResultSolidity;
    }

    filterId = ByteArray.fromHex(filterId);
    if (!eventFilter2Result.containsKey(filterId)) {
      throw new ItemNotFoundException(FILTER_NOT_FOUND);
    }

    LogFilterWrapper logFilterWrapper = eventFilter2Result.get(filterId).getLogFilterWrapper();
    long currentMaxBlockNum = wallet.getNowBlock().getBlockHeader().getRawData().getNumber();

    return getLogsByLogFilterWrapper(logFilterWrapper, currentMaxBlockNum);
  }

  private LogFilterElement[] getLogsByLogFilterWrapper(LogFilterWrapper logFilterWrapper,
      long currentMaxBlockNum) throws JsonRpcTooManyResultException, ExecutionException,
      InterruptedException, BadItemException, ItemNotFoundException {
    //query possible block
    LogBlockQuery logBlockQuery = new LogBlockQuery(logFilterWrapper, manager.getChainBaseManager()
        .getSectionBloomStore(), currentMaxBlockNum, sectionExecutor);
    List<Long> possibleBlockList = logBlockQuery.getPossibleBlock();

    //match event from block one by one exactly
    LogMatch logMatch =
        new LogMatch(logFilterWrapper, possibleBlockList, manager);
    return logMatch.matchBlockOneByOne();
  }

  public static Object[] getFilterResult(String filterId, Map<String, BlockFilterAndResult>
      blockFilter2Result, Map<String, LogFilterAndResult> eventFilter2Result)
      throws ItemNotFoundException {
    Object[] result;

    if (blockFilter2Result.containsKey(filterId)) {
      List<String> blockHashList = blockFilter2Result.get(filterId).popAll();
      result = blockHashList.toArray(new String[blockHashList.size()]);
      blockFilter2Result.get(filterId).updateExpireTime();

    } else if (eventFilter2Result.containsKey(filterId)) {
      List<LogFilterElement> logElementList = eventFilter2Result.get(filterId).popAll();
      result = logElementList.toArray(new LogFilterElement[0]);
      eventFilter2Result.get(filterId).updateExpireTime();

    } else {
      throw new ItemNotFoundException(FILTER_NOT_FOUND);
    }

    return result;
  }

}
