package org.unx.common.overlay.message;

import com.google.protobuf.ByteString;
import lombok.Getter;
import org.unx.common.overlay.discover.node.Node;
import org.unx.common.utils.ByteArray;
import org.unx.core.ChainBaseManager;
import org.unx.core.Constant;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.config.args.Args;
import org.unx.core.db.CommonStore;
import org.unx.core.net.message.MessageTypes;
import org.unx.protos.Discover.Endpoint;
import org.unx.protos.Protocol;
import org.unx.protos.Protocol.HelloMessage.Builder;

public class HelloMessage extends P2pMessage {

  @Getter
  private Protocol.HelloMessage helloMessage;

  public HelloMessage(byte type, byte[] rawData) throws Exception {
    super(type, rawData);
    this.helloMessage = Protocol.HelloMessage.parseFrom(rawData);
  }

  public HelloMessage(Node from, long timestamp, ChainBaseManager chainBaseManager) {

    Endpoint fromEndpoint = Endpoint.newBuilder()
        .setNodeId(ByteString.copyFrom(from.getId()))
        .setPort(from.getPort())
        .setAddress(ByteString.copyFrom(ByteArray.fromString(from.getHost())))
        .build();

    BlockCapsule.BlockId gid = chainBaseManager.getGenesisBlockId();
    Protocol.HelloMessage.BlockId gBlockId = Protocol.HelloMessage.BlockId.newBuilder()
        .setHash(gid.getByteString())
        .setNumber(gid.getNum())
        .build();

    BlockCapsule.BlockId sid = chainBaseManager.getSolidBlockId();
    Protocol.HelloMessage.BlockId sBlockId = Protocol.HelloMessage.BlockId.newBuilder()
        .setHash(sid.getByteString())
        .setNumber(sid.getNum())
        .build();

    BlockCapsule.BlockId hid = chainBaseManager.getHeadBlockId();
    Protocol.HelloMessage.BlockId hBlockId = Protocol.HelloMessage.BlockId.newBuilder()
        .setHash(hid.getByteString())
        .setNumber(hid.getNum())
        .build();

    CommonStore commonStore = chainBaseManager.getCommonStore();
    long lowestBlockNum = 0;
    int nodeType = commonStore.getNodeType();
    if (nodeType == Constant.NODE_TYPE_LIGHT_NODE) {
      lowestBlockNum = commonStore.getLowestBlockNum();
    }

    Builder builder = Protocol.HelloMessage.newBuilder();

    builder.setFrom(fromEndpoint);
    builder.setVersion(Args.getInstance().getNodeP2pVersion());
    builder.setTimestamp(timestamp);
    builder.setGenesisBlockId(gBlockId);
    builder.setSolidBlockId(sBlockId);
    builder.setHeadBlockId(hBlockId);
    builder.setNodeType(nodeType);
    builder.setLowestBlockNum(lowestBlockNum);

    this.helloMessage = builder.build();
    this.type = MessageTypes.P2P_HELLO.asByte();
    this.data = this.helloMessage.toByteArray();
  }

  public void setHelloMessage(Protocol.HelloMessage helloMessage) {
    this.helloMessage = helloMessage;
    this.data = this.helloMessage.toByteArray();
  }

  public int getVersion() {
    return this.helloMessage.getVersion();
  }

  public int getNodeType() {
    return this.helloMessage.getNodeType();
  }

  public long getLowestBlockNum() {
    return this.helloMessage.getLowestBlockNum();
  }

  public long getTimestamp() {
    return this.helloMessage.getTimestamp();
  }

  public Node getFrom() {
    Endpoint from = this.helloMessage.getFrom();
    return new Node(from.getNodeId().toByteArray(),
        ByteArray.toStr(from.getAddress().toByteArray()), from.getPort());
  }

  public BlockCapsule.BlockId getGenesisBlockId() {
    return new BlockCapsule.BlockId(this.helloMessage.getGenesisBlockId().getHash(),
        this.helloMessage.getGenesisBlockId().getNumber());
  }

  public BlockCapsule.BlockId getSolidBlockId() {
    return new BlockCapsule.BlockId(this.helloMessage.getSolidBlockId().getHash(),
        this.helloMessage.getSolidBlockId().getNumber());
  }

  public BlockCapsule.BlockId getHeadBlockId() {
    return new BlockCapsule.BlockId(this.helloMessage.getHeadBlockId().getHash(),
        this.helloMessage.getHeadBlockId().getNumber());
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

  @Override
  public String toString() {
    return new StringBuilder().append(super.toString()).append(helloMessage.toString()).toString();
  }

  public Protocol.HelloMessage getInstance() {
    return this.helloMessage;
  }

  public boolean valid() {
    byte[] genesisBlockByte = this.helloMessage.getGenesisBlockId().getHash().toByteArray();
    if (genesisBlockByte.length == 0) {
      return false;
    }

    byte[] solidBlockId = this.helloMessage.getSolidBlockId().getHash().toByteArray();
    if (solidBlockId.length == 0) {
      return false;
    }

    byte[] headBlockId = this.helloMessage.getHeadBlockId().getHash().toByteArray();
    if (headBlockId.length == 0) {
      return false;
    }

    return true;
  }
}
