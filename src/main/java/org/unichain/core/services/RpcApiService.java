package org.unichain.core.services;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unichain.api.DatabaseGrpc.DatabaseImplBase;
import org.unichain.api.GrpcAPI;
import org.unichain.api.GrpcAPI.*;
import org.unichain.api.GrpcAPI.Return.response_code;
import org.unichain.api.WalletExtensionGrpc;
import org.unichain.api.WalletGrpc.WalletImplBase;
import org.unichain.api.WalletSolidityGrpc.WalletSolidityImplBase;
import org.unichain.common.application.Service;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.overlay.discover.node.NodeHandler;
import org.unichain.common.overlay.discover.node.NodeManager;
import org.unichain.common.utils.*;
import org.unichain.core.Wallet;
import org.unichain.core.WalletSolidity;
import org.unichain.core.actuator.urc20.ext.Urc20;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.capsule.WitnessCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.core.exception.NonUniqueObjectException;
import org.unichain.core.exception.StoreException;
import org.unichain.core.exception.VMIllegalException;
import org.unichain.core.actuator.urc721.ext.Urc721;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.*;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.*;
import org.unichain.protos.Protocol.FutureTokenQuery;
import org.unichain.protos.Protocol.Transaction.Contract.ContractType;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j(topic = "API")
public class RpcApiService implements Service {

  public static final String CONTRACT_VALIDATE_EXCEPTION = "ContractValidateException: {}";
  public static final String CONTRACT_VALIDATE_ERROR = "contract validate error : ";
  private int port = Args.getInstance().getRpcPort();
  private Server apiServer;

  @Autowired
  private Manager dbManager;
  @Autowired
  private NodeManager nodeManager;
  @Autowired
  private WalletSolidity walletSolidity;
  @Autowired
  private Wallet wallet;
  @Autowired
  private NodeInfoService nodeInfoService;
  @Getter
  private DatabaseApi databaseApi = new DatabaseApi();

  private WalletApi walletApi = new WalletApi();

  @Autowired
  private Urc721 urc721;
  @Autowired
  private Urc20 urc20;

  @Getter
  private WalletSolidityApi walletSolidityApi = new WalletSolidityApi();

  private static final long BLOCK_LIMIT_NUM = 100;
  private static final long TRANSACTION_LIMIT_NUM = 1000;

  @Override
  public void init() {
  }

  @Override
  public void init(Args args) {
  }

  @Override
  public void start() {
    try {
      NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port).addService(databaseApi);

      Args args = Args.getInstance();

      if (args.getRpcThreadNum() > 0) {
        serverBuilder = serverBuilder.executor(Executors.newFixedThreadPool(args.getRpcThreadNum()));
      }

      if (args.isSolidityNode()) {
        serverBuilder = serverBuilder.addService(walletSolidityApi);
        if (args.isWalletExtensionApi()) {
          serverBuilder = serverBuilder.addService(new WalletExtensionApi());
        }
      } else {
        serverBuilder = serverBuilder.addService(walletApi);
      }

      // Set configs from config.conf or default value
      serverBuilder
          .maxConcurrentCallsPerConnection(args.getMaxConcurrentCallsPerConnection())
          .flowControlWindow(args.getFlowControlWindow())
          .maxConnectionIdle(args.getMaxConnectionIdleInMillis(), TimeUnit.MILLISECONDS)
          .maxConnectionAge(args.getMaxConnectionAgeInMillis(), TimeUnit.MILLISECONDS)
          .maxMessageSize(args.getMaxMessageSize())
          .maxHeaderListSize(args.getMaxHeaderListSize());

      apiServer = serverBuilder.build().start();
    } catch (IOException e) {
      logger.debug(e.getMessage(), e);
    }

    logger.info("RpcApiService started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println("*** shutting down gRPC server since JVM is shutting down");
      //server.this.stop();
      System.err.println("*** server shut down");
    }));
  }


  private void callContract(Contract.TriggerSmartContract request, StreamObserver<TransactionExtention> responseObserver, boolean isConstant) {
    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    try {
      TransactionCapsule unxCap = createTransactionCapsule(request, ContractType.TriggerSmartContract);
      Transaction unx;
      if (isConstant) {
        unx = wallet.triggerConstantContract(request, unxCap, unxExtBuilder, retBuilder);
      } else {
        unx = wallet.triggerContract(request, unxCap, unxExtBuilder, retBuilder);
      }
      unxExtBuilder.setTransaction(unx);
      unxExtBuilder.setTxid(unxCap.getTransactionId().getByteString());
      retBuilder.setResult(true).setCode(response_code.SUCCESS);
      unxExtBuilder.setResult(retBuilder);
    } catch (ContractValidateException | VMIllegalException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR).setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.error(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
    } catch (RuntimeException e) {
      retBuilder.setResult(false).setCode(response_code.CONTRACT_EXE_ERROR).setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.error("When run constant call in VM, have RuntimeException: " + e.getMessage());
    } catch (Exception e) {
      retBuilder.setResult(false).setCode(response_code.OTHER_ERROR).setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
      unxExtBuilder.setResult(retBuilder);
      logger.error("unknown exception caught: " + e.getMessage(), e);
    } finally {
      responseObserver.onNext(unxExtBuilder.build());
      responseObserver.onCompleted();
    }
  }

  private TransactionCapsule createTransactionCapsule(com.google.protobuf.Message message, ContractType contractType) throws ContractValidateException {
    return wallet.createTransactionCapsule(message, contractType);
  }

  private TransactionExtention transaction2Extention(Transaction transaction) {
    if (transaction == null) {
      return null;
    }
    TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
    Return.Builder retBuilder = Return.newBuilder();
    unxExtBuilder.setTransaction(transaction);
    unxExtBuilder.setTxid(Sha256Hash.of(transaction.getRawData().toByteArray()).getByteString());
    retBuilder.setResult(true).setCode(response_code.SUCCESS);
    unxExtBuilder.setResult(retBuilder);
    return unxExtBuilder.build();
  }

  private BlockExtention block2Extention(Block block) {
    if (block == null) {
      return null;
    }
    BlockExtention.Builder builder = BlockExtention.newBuilder();
    BlockCapsule blockCapsule = new BlockCapsule(block);
    builder.setBlockHeader(block.getBlockHeader());
    builder.setBlockid(ByteString.copyFrom(blockCapsule.getBlockId().getBytes()));
    for (int i = 0; i < block.getTransactionsCount(); i++) {
      Transaction transaction = block.getTransactions(i);
      builder.addTransactions(transaction2Extention(transaction));
    }
    return builder.build();
  }

  /**
   * DatabaseApi.
   * @addon add db api
   */
  public class DatabaseApi extends DatabaseImplBase {

    @Override
    public void getBlockReference(org.unichain.api.GrpcAPI.EmptyMessage request,
        io.grpc.stub.StreamObserver<org.unichain.api.GrpcAPI.BlockReference> responseObserver) {
      long headBlockNum = dbManager.getDynamicPropertiesStore()
          .getLatestBlockHeaderNumber();
      byte[] blockHeaderHash = dbManager.getDynamicPropertiesStore()
          .getLatestBlockHeaderHash().getBytes();
      BlockReference ref = BlockReference.newBuilder()
          .setBlockHash(ByteString.copyFrom(blockHeaderHash))
          .setBlockNum(headBlockNum)
          .build();
      responseObserver.onNext(ref);
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      Block block = null;
      try {
        block = dbManager.getHead().getInstance();
      } catch (StoreException e) {
        logger.error(e.getMessage());
      }
      responseObserver.onNext(block);
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      Block block = null;
      try {
        block = dbManager.getBlockByNum(request.getNum()).getInstance();
      } catch (StoreException e) {
        logger.error(e.getMessage());
      }
      responseObserver.onNext(block);
      responseObserver.onCompleted();
    }

    @Override
    public void getDynamicProperties(EmptyMessage request, StreamObserver<DynamicProperties> responseObserver) {
      DynamicProperties.Builder builder = DynamicProperties.newBuilder();
      builder.setLastSolidityBlockNum(dbManager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum());
      DynamicProperties dynamicProperties = builder.build();
      responseObserver.onNext(dynamicProperties);
      responseObserver.onCompleted();
    }
  }

  /**
   * WalletSolidityApi.
   * @addon add solidity api
   */
  public class WalletSolidityApi extends WalletSolidityImplBase {
    @Override
    public void getFutureTransfer(FutureQuery request, StreamObserver<FuturePack> responseObserver) {
      if (request.getOwnerAddress() != null) {
        FuturePack reply = wallet.getFuture(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getFutureToken(FutureTokenQuery request, StreamObserver<FutureTokenPack> responseObserver) {
      if (!(request.getTokenName() == null || request.getOwnerAddress() == null)) {
        FutureTokenPack reply = wallet.getFutureToken(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTokenPool(TokenPoolQuery request, io.grpc.stub.StreamObserver<TokenPage> responseObserver) {
      TokenPage reply = wallet.getTokenPool(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721ListContract(Urc721ContractQuery request, io.grpc.stub.StreamObserver<Urc721ContractPage> responseObserver) {
      Urc721ContractPage reply = urc721.listContract(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721ListToken(Urc721TokenListQuery request, io.grpc.stub.StreamObserver<Urc721TokenPage> responseObserver) {
      Urc721TokenPage reply = urc721.listToken(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetContract(AddressMessage request, io.grpc.stub.StreamObserver<Urc721Contract> responseObserver) {
      Urc721Contract reply = urc721.getContract(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetToken(Urc721TokenQuery request, io.grpc.stub.StreamObserver<Urc721Token> responseObserver) {
      var reply = urc721.getToken(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721IsApprovedForAll(Urc721IsApprovedForAllQuery request, io.grpc.stub.StreamObserver<BoolMessage> responseObserver) {
      var reply = urc721.isApprovedForAll(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetBalanceOf(Urc721BalanceOfQuery request, io.grpc.stub.StreamObserver<GrpcAPI.NumberMessage> responseObserver) {
      var reply = urc721.balanceOf(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetName(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc721.name(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetSymbol(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc721.symbol(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetTotalSupply(AddressMessage request, io.grpc.stub.StreamObserver<NumberMessage> responseObserver) {
      var reply = urc721.totalSupply(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetTokenUri(Urc721TokenQuery request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc721.tokenUri(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetOwnerOf(Urc721TokenQuery request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc721.ownerOf(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetApproved(Urc721TokenQuery request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc721.getApproved(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetApprovedForAll(Urc721ApprovedForAllQuery request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc721.getApprovedForAll(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    /**
     * urc20
     */
    @Override
    public void urc20FutureGet(Urc20FutureTokenQuery request, StreamObserver<Urc20FutureTokenPack> responseObserver) {
      var reply = urc20.futureGet(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20ContractList(Urc20ContractQuery request, io.grpc.stub.StreamObserver<Urc20ContractPage> responseObserver) {
      var reply = urc20.contractList(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Name(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.name(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Symbol(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.symbol(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Decimals(AddressMessage request, io.grpc.stub.StreamObserver<NumberMessage> responseObserver) {
      var reply = urc20.decimals(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20TotalSupply(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.totalSupply(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20BalanceOf(Urc20BalanceOfQuery request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.balanceOf(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20GetOwner(AddressMessage request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc20.getOwner(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Allowance(Urc20AllowanceQuery request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.allowance(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void getAccount(Account request, StreamObserver<Account> responseObserver) {
      ByteString addressBs = request.getAddress();
      if (addressBs != null) {
        Account reply = wallet.getAccount(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    /**
     * POSBridge
     */
    @Override
    public void getPosBridgeConfig(EmptyMessage req, StreamObserver<PosBridgeConfig> responseObserver) {
      var reply = wallet.getPosBridgeConfig();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void getPosBridgeTokenMap(EmptyMessage req, StreamObserver<PosBridgeTokenMappingPage> responseObserver) {
      var reply = wallet.getPosBridgeTokenMap();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountById(Account request, StreamObserver<Account> responseObserver) {
      ByteString id = request.getAccountId();
      if (id != null) {
        Account reply = wallet.getAccountById(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void listWitnesses(EmptyMessage request, StreamObserver<WitnessList> responseObserver) {
      responseObserver.onNext(wallet.getWitnessList());
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueList(EmptyMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList());
      responseObserver.onCompleted();
    }

    @Override
    public void getPaginatedAssetIssueList(PaginatedMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueByName(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetName = request.getValue();
      if (assetName != null) {
        try {
          responseObserver.onNext(wallet.getAssetIssueByName(assetName));
        } catch (NonUniqueObjectException e) {
          responseObserver.onNext(null);
          logger.error("Solidity NonUniqueObjectException: {}", e.getMessage());
        }
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString assetName = request.getValue();

      if (assetName != null) {
        responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueById(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetId = request.getValue();

      if (assetId != null) {
        responseObserver.onNext(wallet.getAssetIssueById(assetId.toStringUtf8()));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getNowBlock());
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock2(EmptyMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      responseObserver.onNext(block2Extention(wallet.getNowBlock()));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      long num = request.getNum();
      if (num >= 0) {
        Block reply = wallet.getBlockByNum(num);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum2(NumberMessage request,
        StreamObserver<BlockExtention> responseObserver) {
      long num = request.getNum();
      if (num >= 0) {
        Block reply = wallet.getBlockByNum(num);
        responseObserver.onNext(block2Extention(reply));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }


    @Override
    public void getDelegatedResource(DelegatedResourceMessage request,
        StreamObserver<DelegatedResourceList> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
      responseObserver.onCompleted();
    }

    @Override
    public void getDelegatedResourceAccountIndex(BytesMessage request,
        StreamObserver<org.unichain.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
      responseObserver.onCompleted();
    }

    @Override
    public void getExchangeById(BytesMessage request,
        StreamObserver<Exchange> responseObserver) {
      ByteString exchangeId = request.getValue();

      if (Objects.nonNull(exchangeId)) {
        responseObserver.onNext(wallet.getExchangeById(exchangeId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void listExchanges(EmptyMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver.onNext(wallet.getExchangeList());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      try {
        Block block = dbManager.getBlockByNum(request.getNum()).getInstance();
        builder.setNum(block.getTransactionsCount());
      } catch (StoreException e) {
        logger.error(e.getMessage());
        builder.setNum(-1);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionById(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        Transaction reply = wallet.getTransactionById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionInfoById(BytesMessage request,
        StreamObserver<TransactionInfo> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        TransactionInfo reply = wallet.getTransactionInfoById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void generateAddress(EmptyMessage request,
        StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
      ECKey ecKey = new ECKey(Utils.getRandom());
      byte[] priKey = ecKey.getPrivKeyBytes();
      byte[] address = ecKey.getAddress();
      String addressStr = Wallet.encode58Check(address);
      String priKeyStr = Hex.encodeHexString(priKey);
      AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
      builder.setAddress(addressStr);
      builder.setPrivateKey(priKeyStr);
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getRewardInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      try {
        long value = dbManager.getDelegationService().queryReward(request.getValue().toByteArray());
        NumberMessage.Builder builder = NumberMessage.newBuilder();
        builder.setNum(value);
        responseObserver.onNext(builder.build());
      } catch (Exception e) {
        responseObserver.onError(e);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBrokerageInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      try {
        long cycle = dbManager.getDynamicPropertiesStore().getCurrentCycleNumber();
        long value = dbManager.getDelegationStore()
            .getBrokerage(cycle, request.getValue().toByteArray());
        NumberMessage.Builder builder = NumberMessage.newBuilder();
        builder.setNum(value);
        responseObserver.onNext(builder.build());
      } catch (Exception e) {
        responseObserver.onError(e);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void triggerConstantContract(Contract.TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, true);
    }

  }

  /**
   * WalletExtensionApi.
   */
  public class WalletExtensionApi extends WalletExtensionGrpc.WalletExtensionImplBase {

    private TransactionListExtention transactionList2Extention(TransactionList transactionList) {
      if (transactionList == null) {
        return null;
      }
      TransactionListExtention.Builder builder = TransactionListExtention.newBuilder();
      for (Transaction transaction : transactionList.getTransactionList()) {
        builder.addTransaction(transaction2Extention(transaction));
      }
      return builder.build();
    }

    @Override
    public void getTransactionsFromThis(AccountPaginated request,
        StreamObserver<TransactionList> responseObserver) {
      ByteString thisAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != thisAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsFromThis(thisAddress, offset, limit);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionsFromThis2(AccountPaginated request,
        StreamObserver<TransactionListExtention> responseObserver) {
      ByteString thisAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != thisAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsFromThis(thisAddress, offset, limit);
        responseObserver.onNext(transactionList2Extention(reply));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionsToThis(AccountPaginated request,
        StreamObserver<TransactionList> responseObserver) {
      ByteString toAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != toAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsToThis(toAddress, offset, limit);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionsToThis2(AccountPaginated request,
        StreamObserver<TransactionListExtention> responseObserver) {
      ByteString toAddress = request.getAccount().getAddress();
      long offset = request.getOffset();
      long limit = request.getLimit();
      if (null != toAddress && offset >= 0 && limit >= 0) {
        TransactionList reply = walletSolidity
            .getTransactionsToThis(toAddress, offset, limit);
        responseObserver.onNext(transactionList2Extention(reply));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }
  }

  /**
   * WalletApi.
   * @addon add fullnode api
   */
  public class WalletApi extends WalletImplBase {

    private BlockListExtention blocklist2Extention(BlockList blockList) {
      if (blockList == null) {
        return null;
      }
      BlockListExtention.Builder builder = BlockListExtention.newBuilder();
      for (Block block : blockList.getBlockList()) {
        builder.addBlock(block2Extention(block));
      }
      return builder.build();
    }

    @Override
    public void getFutureToken(FutureTokenQuery request, StreamObserver<FutureTokenPack> responseObserver) {
      if (!(request.getTokenName() == null || request.getOwnerAddress() == null)) {
        FutureTokenPack reply = wallet.getFutureToken(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getFutureTransfer(FutureQuery request, StreamObserver<FuturePack> responseObserver) {
      if (request.getOwnerAddress() != null) {
        FuturePack reply = wallet.getFuture(request);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTokenPool(TokenPoolQuery request, io.grpc.stub.StreamObserver<TokenPage> responseObserver) {
      TokenPage reply = wallet.getTokenPool(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721ListContract(Urc721ContractQuery request, io.grpc.stub.StreamObserver<Urc721ContractPage> responseObserver) {
      Urc721ContractPage reply = urc721.listContract(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721ListToken(Urc721TokenListQuery request, io.grpc.stub.StreamObserver<Urc721TokenPage> responseObserver) {
      Urc721TokenPage reply = urc721.listToken(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetContract(AddressMessage request, io.grpc.stub.StreamObserver<Urc721Contract> responseObserver) {
      Urc721Contract reply = urc721.getContract(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetToken(Urc721TokenQuery request, io.grpc.stub.StreamObserver<Urc721Token> responseObserver) {
      Urc721Token reply = urc721.getToken(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetBalanceOf(Urc721BalanceOfQuery request, io.grpc.stub.StreamObserver<GrpcAPI.NumberMessage> responseObserver) {
      var reply = urc721.balanceOf(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetName(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc721.name(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetSymbol(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc721.symbol(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetTotalSupply(AddressMessage request, io.grpc.stub.StreamObserver<NumberMessage> responseObserver) {
      var reply = urc721.totalSupply(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetTokenUri(Urc721TokenQuery request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc721.tokenUri(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetOwnerOf(Urc721TokenQuery request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc721.ownerOf(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetApproved(Urc721TokenQuery request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc721.getApproved(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721GetApprovedForAll(Urc721ApprovedForAllQuery request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc721.getApprovedForAll(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc721IsApprovedForAll(Urc721IsApprovedForAllQuery request, io.grpc.stub.StreamObserver<BoolMessage> responseObserver) {
      var reply = urc721.isApprovedForAll(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void getAccount(Account req, StreamObserver<Account> responseObserver) {
      ByteString addressBs = req.getAddress();
      if (addressBs != null) {
        Account reply = wallet.getAccount(req);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20FutureGet(Urc20FutureTokenQuery request, StreamObserver<Urc20FutureTokenPack> responseObserver) {
      var reply = urc20.futureGet(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20ContractList(Urc20ContractQuery request, io.grpc.stub.StreamObserver<Urc20ContractPage> responseObserver) {
      var reply = urc20.contractList(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Name(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.name(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Symbol(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.symbol(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Decimals(AddressMessage request, io.grpc.stub.StreamObserver<NumberMessage> responseObserver) {
      var reply = urc20.decimals(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20TotalSupply(AddressMessage request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.totalSupply(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20BalanceOf(Urc20BalanceOfQuery request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.balanceOf(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20GetOwner(AddressMessage request, io.grpc.stub.StreamObserver<AddressMessage> responseObserver) {
      var reply = urc20.getOwner(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Allowance(Urc20AllowanceQuery request, io.grpc.stub.StreamObserver<StringMessage> responseObserver) {
      var reply = urc20.allowance(request);
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    //POSBridge
    @Override
    public void getPosBridgeConfig(EmptyMessage req, StreamObserver<PosBridgeConfig> responseObserver) {
      var reply = wallet.getPosBridgeConfig();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void getPosBridgeTokenMap(EmptyMessage req, StreamObserver<PosBridgeTokenMappingPage> responseObserver) {
      var reply = wallet.getPosBridgeTokenMap();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountById(Account req, StreamObserver<Account> responseObserver) {
      ByteString accountId = req.getAccountId();
      if (accountId != null) {
        Account reply = wallet.getAccountById(req);
        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createTransaction(TransferContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.TransferContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createTransaction2(TransferContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.TransferContract, responseObserver);
    }

    private void createTransactionExtention(Message request, ContractType contractType, StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule unx = createTransactionCapsule(request, contractType);
        unxExtBuilder.setTransaction(unx.getInstance());
        unxExtBuilder.setTxid(unx.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (ContractValidateException e) {
        retBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(CONTRACT_VALIDATE_ERROR + e.getMessage()));
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught" + e.getMessage());
      }
      unxExtBuilder.setResult(retBuilder);
      responseObserver.onNext(unxExtBuilder.build());
      responseObserver.onCompleted();
    }


    @Override
    public void getTransactionSign(TransactionSign req, StreamObserver<Transaction> responseObserver) {
      TransactionCapsule retur = wallet.getTransactionSign(req);
      responseObserver.onNext(retur.getInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionSign2(TransactionSign req, StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule unx = wallet.getTransactionSign(req);
        unxExtBuilder.setTransaction(unx.getInstance());
        unxExtBuilder.setTxid(unx.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR).setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught" + e.getMessage());
      }
      unxExtBuilder.setResult(retBuilder);
      responseObserver.onNext(unxExtBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void addSign(TransactionSign req, StreamObserver<TransactionExtention> responseObserver) {
      TransactionExtention.Builder unxExtBuilder = TransactionExtention.newBuilder();
      Return.Builder retBuilder = Return.newBuilder();
      try {
        TransactionCapsule unx = wallet.addSign(req);
        unxExtBuilder.setTransaction(unx.getInstance());
        unxExtBuilder.setTxid(unx.getTransactionId().getByteString());
        retBuilder.setResult(true).setCode(response_code.SUCCESS);
      } catch (Exception e) {
        retBuilder.setResult(false).setCode(response_code.OTHER_ERROR).setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        logger.info("exception caught" + e.getMessage());
      }
      unxExtBuilder.setResult(retBuilder);
      responseObserver.onNext(unxExtBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionSignWeight(Transaction req, StreamObserver<TransactionSignWeight> responseObserver) {
      TransactionSignWeight tsw = wallet.getTransactionSignWeight(req);
      responseObserver.onNext(tsw);
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionApprovedList(Transaction req, StreamObserver<TransactionApprovedList> responseObserver) {
      TransactionApprovedList tal = wallet.getTransactionApprovedList(req);
      responseObserver.onNext(tal);
      responseObserver.onCompleted();
    }

    @Override
    public void createAddress(BytesMessage req, StreamObserver<BytesMessage> responseObserver) {
      byte[] address = wallet.createAdresss(req.getValue().toByteArray());
      BytesMessage.Builder builder = BytesMessage.newBuilder();
      builder.setValue(ByteString.copyFrom(address));
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    private EasyTransferResponse easyTransfer(byte[] privateKey, ByteString toAddress, long amount) {
      TransactionCapsule transactionCapsule;
      GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
      EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
      try {
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        byte[] owner = ecKey.getAddress();
        TransferContract.Builder builder = TransferContract.newBuilder();
        builder.setOwnerAddress(ByteString.copyFrom(owner));
        builder.setToAddress(toAddress);
        builder.setAmount(amount);
        transactionCapsule = createTransactionCapsule(builder.build(), ContractType.TransferContract);
        transactionCapsule.sign(privateKey);
        GrpcAPI.Return result = wallet.broadcastTransaction(transactionCapsule.getInstance());
        responseBuild.setTransaction(transactionCapsule.getInstance());
        responseBuild.setTxid(transactionCapsule.getTransactionId().getByteString());
        responseBuild.setResult(result);
      } catch (ContractValidateException e) {
        returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      } catch (Exception e) {
        returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      }

      return responseBuild.build();
    }

    private EasyTransferResponse easyTransferAsset(byte[] privateKey, ByteString toAddress, String assetId, long amount) {
      TransactionCapsule transactionCapsule;
      GrpcAPI.Return.Builder returnBuilder = GrpcAPI.Return.newBuilder();
      EasyTransferResponse.Builder responseBuild = EasyTransferResponse.newBuilder();
      try {
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        byte[] owner = ecKey.getAddress();
        TransferAssetContract.Builder builder = TransferAssetContract.newBuilder();
        builder.setOwnerAddress(ByteString.copyFrom(owner));
        builder.setToAddress(toAddress);
        builder.setAssetName(ByteString.copyFrom(assetId.getBytes()));
        builder.setAmount(amount);
        transactionCapsule = createTransactionCapsule(builder.build(),
            ContractType.TransferAssetContract);
        transactionCapsule.sign(privateKey);
        GrpcAPI.Return retur = wallet.broadcastTransaction(transactionCapsule.getInstance());
        responseBuild.setTransaction(transactionCapsule.getInstance());
        responseBuild.setTxid(transactionCapsule.getTransactionId().getByteString());
        responseBuild.setResult(retur);
      } catch (ContractValidateException e) {
        returnBuilder.setResult(false).setCode(response_code.CONTRACT_VALIDATE_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      } catch (Exception e) {
        returnBuilder.setResult(false).setCode(response_code.OTHER_ERROR)
            .setMessage(ByteString.copyFromUtf8(e.getClass() + " : " + e.getMessage()));
        responseBuild.setResult(returnBuilder.build());
      }

      return responseBuild.build();
    }

    @Override
    public void easyTransfer(EasyTransferMessage req, StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
      EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferAsset(EasyTransferAssetMessage req, StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = wallet.pass2Key(req.getPassPhrase().toByteArray());
      EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(), req.getAssetId(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferByPrivate(EasyTransferByPrivateMessage req, StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = req.getPrivateKey().toByteArray();
      EasyTransferResponse response = easyTransfer(privateKey, req.getToAddress(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void easyTransferAssetByPrivate(EasyTransferAssetByPrivateMessage req, StreamObserver<EasyTransferResponse> responseObserver) {
      byte[] privateKey = req.getPrivateKey().toByteArray();
      EasyTransferResponse response = easyTransferAsset(privateKey, req.getToAddress(), req.getAssetId(), req.getAmount());
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void broadcastTransaction(Transaction req, StreamObserver<GrpcAPI.Return> responseObserver) {
      GrpcAPI.Return ret = wallet.broadcastTransaction(req);
      responseObserver.onNext(ret);
      responseObserver.onCompleted();
    }

    @Override
    public void createAssetIssue(AssetIssueContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.AssetIssueContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createAssetIssue2(AssetIssueContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AssetIssueContract, responseObserver);
    }

    @Override
    public void unfreezeAsset(UnfreezeAssetContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.UnfreezeAssetContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void unfreezeAsset2(UnfreezeAssetContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UnfreezeAssetContract, responseObserver);
    }

    //refactor、test later
    private void checkVoteWitnessAccount(VoteWitnessContract req) {
      //send back to cli
      ByteString ownerAddress = req.getOwnerAddress();
      Preconditions.checkNotNull(ownerAddress, "OwnerAddress is null");

      AccountCapsule account = dbManager.getAccountStore().get(ownerAddress.toByteArray());
      Preconditions.checkNotNull(account, "OwnerAddress[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      int votesCount = req.getVotesCount();
      Preconditions.checkArgument(votesCount <= 0, "VotesCount[" + votesCount + "] <= 0");
      Preconditions.checkArgument(account.getUnichainPower() < votesCount,
          "unichain power[" + account.getUnichainPower() + "] <  VotesCount[" + votesCount + "]");

      req.getVotesList().forEach(vote -> {
        ByteString voteAddress = vote.getVoteAddress();
        WitnessCapsule witness = dbManager.getWitnessStore()
            .get(voteAddress.toByteArray());
        String readableWitnessAddress = StringUtil.createReadableString(voteAddress);

        Preconditions.checkNotNull(witness, "witness[" + readableWitnessAddress + "] not exists");
        Preconditions.checkArgument(vote.getVoteCount() <= 0,
            "VoteAddress[" + readableWitnessAddress + "],VotesCount[" + vote
                .getVoteCount() + "] <= 0");
      });
    }

    @Override
    public void voteWitnessAccount(VoteWitnessContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.VoteWitnessContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void voteWitnessAccount2(VoteWitnessContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.VoteWitnessContract, responseObserver);
    }

    @Override
    public void updateSetting(UpdateSettingContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateSettingContract,
          responseObserver);
    }

    @Override
    public void updateEnergyLimit(UpdateEnergyLimitContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateEnergyLimitContract,
          responseObserver);
    }

    @Override
    public void clearContractABI(ClearABIContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ClearABIContract,
          responseObserver);
    }

    @Override
    public void createWitness(WitnessCreateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WitnessCreateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createWitness2(WitnessCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WitnessCreateContract, responseObserver);
    }

    @Override
    public void createAccount(AccountCreateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AccountCreateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void createAccount2(AccountCreateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountCreateContract, responseObserver);
    }

    @Override
    public void updateWitness(Contract.WitnessUpdateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.WitnessUpdateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateWitness2(Contract.WitnessUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WitnessUpdateContract, responseObserver);
    }

    @Override
    public void updateAccount(Contract.AccountUpdateContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.AccountUpdateContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void setAccountId(Contract.SetAccountIdContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.SetAccountIdContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateAccount2(Contract.AccountUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountUpdateContract, responseObserver);
    }

    @Override
    public void updateAsset(Contract.UpdateAssetContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.UpdateAssetContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug("ContractValidateException", e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateAsset2(Contract.UpdateAssetContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateAssetContract, responseObserver);
    }

    @Override
    public void freezeBalance(Contract.FreezeBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.FreezeBalanceContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void freezeBalance2(Contract.FreezeBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.FreezeBalanceContract, responseObserver);
    }

    @Override
    public void unfreezeBalance(Contract.UnfreezeBalanceContract request,
        StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(
            createTransactionCapsule(request, ContractType.UnfreezeBalanceContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void unfreezeBalance2(Contract.UnfreezeBalanceContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UnfreezeBalanceContract, responseObserver);
    }

    @Override
    public void withdrawBalance(Contract.WithdrawBalanceContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.WithdrawBalanceContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void createUrc721Contract(Contract.Urc721CreateContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.createContract(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721Mint(Contract.Urc721MintContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.mint(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721RemoveMinter(Contract.Urc721RemoveMinterContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.removeMinter(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721AddMinter(Contract.Urc721AddMinterContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.addMinter(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721RenounceMinter(Contract.Urc721RenounceMinterContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.renounceMinter(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721Burn(Contract.Urc721BurnContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.burn(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721Approve(Contract.Urc721ApproveContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.approve(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721SetApprovalForAll(Contract.Urc721SetApprovalForAllContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.setApprovalForAll(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void urc721TransferFrom(Contract.Urc721TransferFromContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc721.transferFrom(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     * POSBridge
     */
    @Override
    public void posBridgeSetup(Contract.PosBridgeSetupContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.PosBridgeSetupContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }
    /**
     */
    @Override
    public void posBridgeMapToken(Contract.PosBridgeMapTokenContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.PosBridgeMapTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }
    /**
     */
    @Override
    public void posBridgeCleanMapToken(Contract.PosBridgeCleanMapTokenContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.PosBridgeCleanMapTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }
    /**
     */
    @Override
    public void posBridgeDeposit(Contract.PosBridgeDepositContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.PosBridgeDepositContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }
    /**
     */
    @Override
    public void posBridgeDepositExec(Contract.PosBridgeDepositExecContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.PosBridgeDepositExecContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }
    /**
     */
    @Override
    public void posBridgeWithdraw(Contract.PosBridgeWithdrawContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.PosBridgeWithdrawContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }
    /**
     */
    @Override
    public void posBridgeWithdrawExec(Contract.PosBridgeWithdrawExecContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.PosBridgeWithdrawExecContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void createToken(Contract.CreateTokenContract request, StreamObserver<Transaction> responseObserver) {
      try {
        //generate token address
        request = request.toBuilder()
                .setAddress(ByteString.copyFrom(AddressUtil.generateRandomAddress()))
                .build();
        responseObserver.onNext(createTransactionCapsule(request, ContractType.CreateTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void transferTokenOwner(Contract.TransferTokenOwnerContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.TransferTokenOwnerContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void exchangeToken(Contract.ExchangeTokenContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.ExchangeTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void contributeTokenFee(Contract.ContributeTokenPoolFeeContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.ContributeTokenPoolFeeContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void updateTokenParams(Contract.UpdateTokenParamsContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.UpdateTokenParamsContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void mineToken(Contract.MineTokenContract request,
                          StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.MineTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void burnToken(Contract.BurnTokenContract request,
                          StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.BurnTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void transferToken(Contract.TransferTokenContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.TransferTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void withdrawTokenFuture(Contract.WithdrawFutureTokenContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.WithdrawFutureTokenContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void createFutureTransferTransaction(org.unichain.protos.Contract.FutureTransferContract request, io.grpc.stub.StreamObserver<org.unichain.protos.Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.FutureTransferContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void createFutureDealTransferTransaction(Contract.FutureDealTransferContract request, io.grpc.stub.StreamObserver<org.unichain.protos.Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.FutureDealTransferContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     */
    @Override
    public void withdrawFutureTransaction(org.unichain.protos.Contract.FutureWithdrawContract request, io.grpc.stub.StreamObserver<org.unichain.protos.Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(createTransactionCapsule(request, ContractType.FutureWithdrawContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    /**
     * Urc20
     */
    @Override
    public void urc20ContractCreate(Contract.Urc20CreateContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.createContract(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20ContributePoolFee(Contract.Urc20ContributePoolFeeContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.contributePoolFee(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20UpdateParams(Contract.Urc20UpdateParamsContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.updateParams(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Mint(Contract.Urc20MintContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.mint(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Burn(Contract.Urc20BurnContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.burn(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20WithdrawFuture(Contract.Urc20WithdrawFutureContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.withdrawFuture(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20TransferOwner(Contract.Urc20TransferOwnerContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.transferOwner(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Exchange(Contract.Urc20ExchangeContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.exchange(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Approve(Contract.Urc20ApproveContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.approve(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20TransferFrom(Contract.Urc20TransferFromContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.transferFrom(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void urc20Transfer(Contract.Urc20TransferContract request, StreamObserver<Protocol.Transaction> responseObserver) {
      try {
        responseObserver.onNext(urc20.transfer(request));
      } catch (ContractValidateException e) {
        responseObserver.onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void withdrawBalance2(Contract.WithdrawBalanceContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.WithdrawBalanceContract, responseObserver);
    }

    @Override
    public void proposalCreate(Contract.ProposalCreateContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalCreateContract, responseObserver);
    }


    @Override
    public void proposalApprove(Contract.ProposalApproveContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalApproveContract, responseObserver);
    }

    @Override
    public void proposalDelete(Contract.ProposalDeleteContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ProposalDeleteContract, responseObserver);
    }

    @Override
    public void exchangeCreate(Contract.ExchangeCreateContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeCreateContract, responseObserver);
    }

    @Override
    public void exchangeInject(Contract.ExchangeInjectContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeInjectContract, responseObserver);
    }

    @Override
    public void exchangeWithdraw(Contract.ExchangeWithdrawContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeWithdrawContract, responseObserver);
    }

    @Override
    public void exchangeTransaction(Contract.ExchangeTransactionContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ExchangeTransactionContract, responseObserver);
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getNowBlock());
      responseObserver.onCompleted();
    }

    @Override
    public void getNowBlock2(EmptyMessage request, StreamObserver<BlockExtention> responseObserver) {
      Block block = wallet.getNowBlock();
      responseObserver.onNext(block2Extention(block));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      responseObserver.onNext(wallet.getBlockByNum(request.getNum()));
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByNum2(NumberMessage request, StreamObserver<BlockExtention> responseObserver) {
      Block block = wallet.getBlockByNum(request.getNum());
      responseObserver.onNext(block2Extention(block));
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request, StreamObserver<NumberMessage> responseObserver) {
      NumberMessage.Builder builder = NumberMessage.newBuilder();
      try {
        Block block = dbManager.getBlockByNum(request.getNum()).getInstance();
        builder.setNum(block.getTransactionsCount());
      } catch (StoreException e) {
        logger.error(e.getMessage());
        builder.setNum(-1);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void listNodes(EmptyMessage request, StreamObserver<NodeList> responseObserver) {
      List<NodeHandler> handlerList = nodeManager.dumpActiveNodes();

      Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();
      for (NodeHandler handler : handlerList) {
        String key = handler.getNode().getHexId() + handler.getNode().getHost();
        nodeHandlerMap.put(key, handler);
      }

      NodeList.Builder nodeListBuilder = NodeList.newBuilder();

      nodeHandlerMap.entrySet().stream()
          .forEach(v -> {
            org.unichain.common.overlay.discover.node.Node node = v.getValue().getNode();
            nodeListBuilder.addNodes(Node.newBuilder().setAddress(
                Address.newBuilder()
                    .setHost(ByteString.copyFrom(ByteArray.fromString(node.getHost())))
                    .setPort(node.getPort())));
          });

      responseObserver.onNext(nodeListBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void transferAsset(TransferAssetContract request, StreamObserver<Transaction> responseObserver) {
      try {
          responseObserver.onNext(createTransactionCapsule(request, ContractType.TransferAssetContract).getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void transferAsset2(TransferAssetContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.TransferAssetContract, responseObserver);
    }

    @Override
    public void participateAssetIssue(ParticipateAssetIssueContract request, StreamObserver<Transaction> responseObserver) {
      try {
        responseObserver
            .onNext(createTransactionCapsule(request, ContractType.ParticipateAssetIssueContract)
                .getInstance());
      } catch (ContractValidateException e) {
        responseObserver
            .onNext(null);
        logger.debug(CONTRACT_VALIDATE_EXCEPTION, e.getMessage());
      }
      responseObserver.onCompleted();
    }

    @Override
    public void participateAssetIssue2(ParticipateAssetIssueContract request, StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.ParticipateAssetIssueContract,
          responseObserver);
    }

    @Override
    public void getAssetIssueByAccount(Account request, StreamObserver<AssetIssueList> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAssetIssueByAccount(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountNet(Account request,
        StreamObserver<AccountNetMessage> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAccountNet(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAccountResource(Account request,
        StreamObserver<AccountResourceMessage> responseObserver) {
      ByteString fromBs = request.getAddress();

      if (fromBs != null) {
        responseObserver.onNext(wallet.getAccountResource(fromBs));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueByName(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetName = request.getValue();
      if (assetName != null) {
        try {
          responseObserver.onNext(wallet.getAssetIssueByName(assetName));
        } catch (NonUniqueObjectException e) {
          responseObserver.onNext(null);
          logger.debug("FullNode NonUniqueObjectException: {}", e.getMessage());
        }
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      ByteString assetName = request.getValue();

      if (assetName != null) {
        responseObserver.onNext(wallet.getAssetIssueListByName(assetName));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueById(BytesMessage request,
        StreamObserver<AssetIssueContract> responseObserver) {
      ByteString assetId = request.getValue();

      if (assetId != null) {
        responseObserver.onNext(wallet.getAssetIssueById(assetId.toStringUtf8()));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockById(BytesMessage request, StreamObserver<Block> responseObserver) {
      ByteString blockId = request.getValue();

      if (Objects.nonNull(blockId)) {
        responseObserver.onNext(wallet.getBlockById(blockId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getProposalById(BytesMessage request,
        StreamObserver<Proposal> responseObserver) {
      ByteString proposalId = request.getValue();

      if (Objects.nonNull(proposalId)) {
        responseObserver.onNext(wallet.getProposalById(proposalId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getExchangeById(BytesMessage request,
        StreamObserver<Exchange> responseObserver) {
      ByteString exchangeId = request.getValue();

      if (Objects.nonNull(exchangeId)) {
        responseObserver.onNext(wallet.getExchangeById(exchangeId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLimitNext(BlockLimit request,
        StreamObserver<BlockList> responseObserver) {
      long startNum = request.getStartNum();
      long endNum = request.getEndNum();

      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        responseObserver.onNext(wallet.getBlocksByLimitNext(startNum, endNum - startNum));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLimitNext2(BlockLimit request,
        StreamObserver<BlockListExtention> responseObserver) {
      long startNum = request.getStartNum();
      long endNum = request.getEndNum();

      if (endNum > 0 && endNum > startNum && endNum - startNum <= BLOCK_LIMIT_NUM) {
        responseObserver
            .onNext(blocklist2Extention(wallet.getBlocksByLimitNext(startNum, endNum - startNum)));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLatestNum(NumberMessage request,
        StreamObserver<BlockList> responseObserver) {
      long getNum = request.getNum();

      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        responseObserver.onNext(wallet.getBlockByLatestNum(getNum));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBlockByLatestNum2(NumberMessage request,
        StreamObserver<BlockListExtention> responseObserver) {
      long getNum = request.getNum();

      if (getNum > 0 && getNum < BLOCK_LIMIT_NUM) {
        responseObserver.onNext(blocklist2Extention(wallet.getBlockByLatestNum(getNum)));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionById(BytesMessage request,
        StreamObserver<Transaction> responseObserver) {
      ByteString transactionId = request.getValue();

      if (Objects.nonNull(transactionId)) {
        responseObserver.onNext(wallet.getTransactionById(transactionId));
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void deployContract(org.unichain.protos.Contract.CreateSmartContract request,
        io.grpc.stub.StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.CreateSmartContract, responseObserver);
    }

    public void totalTransaction(EmptyMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      responseObserver.onNext(wallet.totalTransaction());
      responseObserver.onCompleted();
    }

    @Override
    public void getNextMaintenanceTime(EmptyMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      responseObserver.onNext(wallet.getNextMaintenanceTime());
      responseObserver.onCompleted();
    }

    @Override
    public void getAssetIssueList(EmptyMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList());
      responseObserver.onCompleted();
    }

    @Override
    public void triggerContract(Contract.TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, false);
    }

    @Override
    public void triggerConstantContract(Contract.TriggerSmartContract request,
        StreamObserver<TransactionExtention> responseObserver) {

      callContract(request, responseObserver, true);
    }

    public void getPaginatedAssetIssueList(PaginatedMessage request,
        StreamObserver<AssetIssueList> responseObserver) {
      responseObserver.onNext(wallet.getAssetIssueList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();
    }

    @Override
    public void getContract(BytesMessage request,
        StreamObserver<Protocol.SmartContract> responseObserver) {
      Protocol.SmartContract contract = wallet.getContract(request);
      responseObserver.onNext(contract);
      responseObserver.onCompleted();
    }

    public void listWitnesses(EmptyMessage request,
        StreamObserver<WitnessList> responseObserver) {
      responseObserver.onNext(wallet.getWitnessList());
      responseObserver.onCompleted();
    }

    @Override
    public void listProposals(EmptyMessage request,
        StreamObserver<ProposalList> responseObserver) {
      responseObserver.onNext(wallet.getProposalList());
      responseObserver.onCompleted();
    }


    @Override
    public void getDelegatedResource(DelegatedResourceMessage request,
        StreamObserver<DelegatedResourceList> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResource(request.getFromAddress(), request.getToAddress()));
      responseObserver.onCompleted();
    }

    public void getDelegatedResourceAccountIndex(BytesMessage request,
        StreamObserver<org.unichain.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      responseObserver
          .onNext(wallet.getDelegatedResourceAccountIndex(request.getValue()));
      responseObserver.onCompleted();
    }

    @Override
    public void getPaginatedProposalList(PaginatedMessage request,
        StreamObserver<ProposalList> responseObserver) {
      responseObserver
          .onNext(wallet.getPaginatedProposalList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();

    }

    @Override
    public void getPaginatedExchangeList(PaginatedMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver
          .onNext(wallet.getPaginatedExchangeList(request.getOffset(), request.getLimit()));
      responseObserver.onCompleted();

    }

    @Override
    public void listExchanges(EmptyMessage request,
        StreamObserver<ExchangeList> responseObserver) {
      responseObserver.onNext(wallet.getExchangeList());
      responseObserver.onCompleted();
    }

    @Override
    public void getChainParameters(EmptyMessage request,
        StreamObserver<Protocol.ChainParameters> responseObserver) {
      responseObserver.onNext(wallet.getChainParameters());
      responseObserver.onCompleted();
    }

    @Override
    public void generateAddress(EmptyMessage request,
        StreamObserver<GrpcAPI.AddressPrKeyPairMessage> responseObserver) {
      ECKey ecKey = new ECKey(Utils.getRandom());
      byte[] priKey = ecKey.getPrivKeyBytes();
      byte[] address = ecKey.getAddress();
      String addressStr = Wallet.encode58Check(address);
      String priKeyStr = Hex.encodeHexString(priKey);
      AddressPrKeyPairMessage.Builder builder = AddressPrKeyPairMessage.newBuilder();
      builder.setAddress(addressStr);
      builder.setPrivateKey(priKeyStr);
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getTransactionInfoById(BytesMessage request,
        StreamObserver<TransactionInfo> responseObserver) {
      ByteString id = request.getValue();
      if (null != id) {
        TransactionInfo reply = wallet.getTransactionInfoById(id);

        responseObserver.onNext(reply);
      } else {
        responseObserver.onNext(null);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getNodeInfo(EmptyMessage request, StreamObserver<NodeInfo> responseObserver) {
      try {
        responseObserver.onNext(nodeInfoService.getNodeInfo().transferToProtoEntity());
      } catch (Exception e) {
        responseObserver.onError(e);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void accountPermissionUpdate(AccountPermissionUpdateContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.AccountPermissionUpdateContract,
          responseObserver);
    }

    @Override
    public void getRewardInfo(BytesMessage request,
        StreamObserver<NumberMessage> responseObserver) {
      try {
        long value = dbManager.getDelegationService().queryReward(request.getValue().toByteArray());
        NumberMessage.Builder builder = NumberMessage.newBuilder();
        builder.setNum(value);
        responseObserver.onNext(builder.build());
      } catch (Exception e) {
        responseObserver.onError(e);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void getBrokerageInfo(BytesMessage request, StreamObserver<NumberMessage> responseObserver) {
      try {
        long cycle = dbManager.getDynamicPropertiesStore().getCurrentCycleNumber();
        long value = dbManager.getDelegationStore().getBrokerage(cycle, request.getValue().toByteArray());
        NumberMessage.Builder builder = NumberMessage.newBuilder();
        builder.setNum(value);
        responseObserver.onNext(builder.build());
      } catch (Exception e) {
        responseObserver.onError(e);
      }
      responseObserver.onCompleted();
    }

    @Override
    public void updateBrokerage(UpdateBrokerageContract request,
        StreamObserver<TransactionExtention> responseObserver) {
      createTransactionExtention(request, ContractType.UpdateBrokerageContract,
          responseObserver);
    }
  }

  @Override
  public void stop() {
    if (apiServer != null) {
      apiServer.shutdown();
    }
  }

  /**
   * ...
   */
  public void blockUntilShutdown() {
    if (apiServer != null) {
      try {
        apiServer.awaitTermination();
      } catch (InterruptedException e) {
        logger.warn("{}", e);
        Thread.currentThread().interrupt();
      }
    }
  }
}
