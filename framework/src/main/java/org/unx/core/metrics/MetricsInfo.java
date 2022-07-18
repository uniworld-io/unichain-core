package org.unx.core.metrics;

import lombok.Data;
import org.unx.core.metrics.blockchain.BlockChainInfo;
import org.unx.core.metrics.net.NetInfo;
import org.unx.core.metrics.node.NodeInfo;

@Data
public class MetricsInfo {
  private long interval;
  private NodeInfo node;
  private BlockChainInfo blockchain;
  private NetInfo net;
}
