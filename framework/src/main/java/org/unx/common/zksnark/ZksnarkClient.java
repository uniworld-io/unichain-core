package org.unx.common.zksnark;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import org.unx.api.UnxZksnarkGrpc;
import org.unx.api.ZksnarkGrpcAPI.ZksnarkRequest;
import org.unx.api.ZksnarkGrpcAPI.ZksnarkResponse.Code;
import org.unx.core.capsule.TransactionCapsule;
import org.unx.protos.Protocol.Transaction;

public class ZksnarkClient {

  public static final ZksnarkClient instance = new ZksnarkClient();

  private UnxZksnarkGrpc.UnxZksnarkBlockingStub blockingStub;

  public ZksnarkClient() {
    blockingStub = UnxZksnarkGrpc.newBlockingStub(ManagedChannelBuilder
        .forTarget("127.0.0.1:60051")
        .usePlaintext()
        .build());
  }

  public static ZksnarkClient getInstance() {
    return instance;
  }

  public boolean checkZksnarkProof(Transaction transaction, byte[] sighash, long valueBalance) {
    String txId = new TransactionCapsule(transaction).getTransactionId().toString();
    ZksnarkRequest request = ZksnarkRequest.newBuilder()
        .setTransaction(transaction)
        .setTxId(txId)
        .setSighash(ByteString.copyFrom(sighash))
        .setValueBalance(valueBalance)
        .build();
    return blockingStub.checkZksnarkProof(request).getCode() == Code.SUCCESS;
  }
}
