package org.unichain.core.services.interfaceOnSolidity;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.unichain.api.DatabaseGrpc.DatabaseImplBase;
import org.unichain.api.GrpcAPI.*;
import org.unichain.api.GrpcAPI.Return.response_code;
import org.unichain.api.WalletSolidityGrpc.WalletSolidityImplBase;
import org.unichain.common.application.Service;
import org.unichain.common.crypto.ECKey;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.common.utils.Utils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.services.RpcApiService;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.AssetIssueContract;
import org.unichain.protos.Protocol.*;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "API")
public class RpcApiServiceOnSolidity implements Service {

  private int port = Args.getInstance().getRpcOnSolidityPort();
  private Server apiServer;

  @Autowired
  private WalletOnSolidity walletOnSolidity;

  @Autowired
  private RpcApiService rpcApiService;

  @Override
  public void init() {
  }

  @Override
  public void init(Args args) {
  }

  @Override
  public void start() {
    try {
      NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(port).addService(new DatabaseApi());

      Args args = Args.getInstance();

      if (args.getRpcThreadNum() > 0) {
        serverBuilder = serverBuilder.executor(Executors.newFixedThreadPool(args.getRpcThreadNum()));
      }

      serverBuilder = serverBuilder.addService(new WalletSolidityApi());

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

    logger.info("RpcApiServiceOnSolidity started, listening on " + port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.err.println("*** shutting down gRPC server on solidity since JVM is shutting down");
      //server.this.stop();
      System.err.println("*** server on solidity shut down");
    }));
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
   */
  private class DatabaseApi extends DatabaseImplBase {

    @Override
    public void getBlockReference(EmptyMessage request, StreamObserver<BlockReference> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getDatabaseApi().getBlockReference(request, responseObserver));
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getDatabaseApi().getNowBlock(request, responseObserver));
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getDatabaseApi().getBlockByNum(request, responseObserver)
      );
    }

    @Override
    public void getDynamicProperties(EmptyMessage request, StreamObserver<DynamicProperties> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getDatabaseApi().getDynamicProperties(request, responseObserver)
      );
    }
  }

  /**
   * WalletSolidityApi.
   */
  private class WalletSolidityApi extends WalletSolidityImplBase {

    @Override
    public void getTokenPool(TokenPoolQuery request, io.grpc.stub.StreamObserver<Contract.TokenPage> responseObserver){
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getTokenPool(request, responseObserver)
      );
    }

    @Override
    public void getAccount(Account request, StreamObserver<Account> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getAccount(request, responseObserver)
      );
    }

    @Override
    public void getAccountById(Account request, StreamObserver<Account> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getAccountById(request, responseObserver)
      );
    }

    @Override
    public void listWitnesses(EmptyMessage request, StreamObserver<WitnessList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().listWitnesses(request, responseObserver)
      );
    }

    @Override
    public void getAssetIssueById(BytesMessage request, StreamObserver<AssetIssueContract> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getAssetIssueById(request, responseObserver)
      );
    }

    @Override
    public void getAssetIssueByName(BytesMessage request, StreamObserver<AssetIssueContract> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getAssetIssueByName(request, responseObserver)
      );
    }

    @Override
    public void getAssetIssueList(EmptyMessage request, StreamObserver<AssetIssueList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getAssetIssueList(request, responseObserver));
    }

    @Override
    public void getAssetIssueListByName(BytesMessage request, StreamObserver<AssetIssueList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getAssetIssueListByName(request, responseObserver));
    }

    @Override
    public void getPaginatedAssetIssueList(PaginatedMessage request, StreamObserver<AssetIssueList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getPaginatedAssetIssueList(request, responseObserver));
    }

    @Override
    public void getExchangeById(BytesMessage request, StreamObserver<Exchange> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getExchangeById(request, responseObserver));
    }

    @Override
    public void getNowBlock(EmptyMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getNowBlock(request, responseObserver));
    }

    @Override
    public void getNowBlock2(EmptyMessage request, StreamObserver<BlockExtention> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getNowBlock2(request, responseObserver));
    }

    @Override
    public void getBlockByNum(NumberMessage request, StreamObserver<Block> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getBlockByNum(request, responseObserver));
    }

    @Override
    public void getBlockByNum2(NumberMessage request, StreamObserver<BlockExtention> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getBlockByNum2(request, responseObserver));
    }

    @Override
    public void getDelegatedResource(DelegatedResourceMessage request, StreamObserver<DelegatedResourceList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getDelegatedResource(request, responseObserver));
    }

    @Override
    public void getDelegatedResourceAccountIndex(BytesMessage request, StreamObserver<org.unichain.protos.Protocol.DelegatedResourceAccountIndex> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getDelegatedResourceAccountIndex(request, responseObserver));
    }

    @Override
    public void getTransactionCountByBlockNum(NumberMessage request, StreamObserver<NumberMessage> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getTransactionCountByBlockNum(request, responseObserver));
    }

    @Override
    public void getTransactionById(BytesMessage request, StreamObserver<Transaction> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getTransactionById(request, responseObserver));
    }

    @Override
    public void getTransactionInfoById(BytesMessage request, StreamObserver<TransactionInfo> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getTransactionInfoById(request, responseObserver));
    }

    @Override
    public void listExchanges(EmptyMessage request, StreamObserver<ExchangeList> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().listExchanges(request, responseObserver));
    }

    @Override
    public void triggerConstantContract(Contract.TriggerSmartContract request, StreamObserver<TransactionExtention> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().triggerConstantContract(request, responseObserver));
    }


    @Override
    public void generateAddress(EmptyMessage request, StreamObserver<AddressPrKeyPairMessage> responseObserver) {
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
    public void getRewardInfo(BytesMessage request, StreamObserver<NumberMessage> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getRewardInfo(request, responseObserver));
    }

    @Override
    public void getBrokerageInfo(BytesMessage request, StreamObserver<NumberMessage> responseObserver) {
      walletOnSolidity.futureGet(() -> rpcApiService.getWalletSolidityApi().getBrokerageInfo(request, responseObserver));
    }
  }

  @Override
  public void stop() {
    if (apiServer != null) {
      apiServer.shutdown();
    }
  }
}
