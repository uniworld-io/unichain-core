package org.unx.common.net.udp.message.discover;

import static org.unx.common.net.udp.message.UdpMessageTypeEnum.DISCOVER_NEIGHBORS;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import org.unx.common.net.udp.message.Message;
import org.unx.common.overlay.discover.node.Node;
import org.unx.common.utils.ByteArray;
import org.unx.protos.Discover;
import org.unx.protos.Discover.Endpoint;
import org.unx.protos.Discover.Neighbours;
import org.unx.protos.Discover.Neighbours.Builder;

public class NeighborsMessage extends Message {

  private Discover.Neighbours neighbours;

  public NeighborsMessage(byte[] data) throws Exception {
    super(DISCOVER_NEIGHBORS, data);
    this.neighbours = Discover.Neighbours.parseFrom(data);
  }

  public NeighborsMessage(Node from, List<Node> neighbours, long sequence) {
    super(DISCOVER_NEIGHBORS, null);
    Builder builder = Neighbours.newBuilder()
        .setTimestamp(sequence);

    neighbours.forEach(neighbour -> {
      Endpoint endpoint = Endpoint.newBuilder()
          .setAddress(ByteString.copyFrom(ByteArray.fromString(neighbour.getHost())))
          .setPort(neighbour.getPort())
          .setNodeId(ByteString.copyFrom(neighbour.getId()))
          .build();

      builder.addNeighbours(endpoint);
    });

    Endpoint fromEndpoint = Endpoint.newBuilder()
        .setAddress(ByteString.copyFrom(ByteArray.fromString(from.getHost())))
        .setPort(from.getPort())
        .setNodeId(ByteString.copyFrom(from.getId()))
        .build();

    builder.setFrom(fromEndpoint);

    this.neighbours = builder.build();

    this.data = this.neighbours.toByteArray();
  }

  public List<Node> getNodes() {
    List<Node> nodes = new ArrayList<>();
    neighbours.getNeighboursList().forEach(neighbour -> nodes.add(
        new Node(neighbour.getNodeId().toByteArray(),
            ByteArray.toStr(neighbour.getAddress().toByteArray()),
            neighbour.getPort())));
    return nodes;
  }

  @Override
  public long getTimestamp() {
    return this.neighbours.getTimestamp();
  }

  @Override
  public Node getFrom() {
    return Message.getNode(neighbours.getFrom());
  }

  @Override
  public String toString() {
    return "[neighbours: " + neighbours;
  }

}
