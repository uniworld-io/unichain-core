package org.unx.common.prometheus;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "metrics")
public class MetricKeys {

  private MetricKeys() {
    throw new IllegalStateException("MetricsKey");
  }

  // Counter
  public static class Counter {
    public static final String TXS = "unx:txs";
    public static final String MINER = "unx:miner";
    public static final String BLOCK_FORK = "unx:block_fork";
    public static final String P2P_ERROR = "unx:p2p_error";
    public static final String P2P_DISCONNECT = "unx:p2p_disconnect";
    public static final String INTERNAL_SERVICE_FAIL = "unx:internal_service_fail";

    private Counter() {
      throw new IllegalStateException("Counter");
    }

  }

  // Gauge
  public static class Gauge {
    public static final String HEADER_HEIGHT = "unx:header_height";
    public static final String HEADER_TIME = "unx:header_time";
    public static final String PEERS = "unx:peers";
    public static final String DB_SIZE_BYTES = "unx:db_size_bytes";
    public static final String DB_SST_LEVEL = "unx:db_sst_level";
    public static final String MANAGER_QUEUE = "unx:manager_queue_size";

    private Gauge() {
      throw new IllegalStateException("Gauge");
    }

  }

  // Histogram
  public static class Histogram {
    public static final String HTTP_SERVICE_LATENCY = "unx:http_service_latency_seconds";
    public static final String GRPC_SERVICE_LATENCY = "unx:grpc_service_latency_seconds";
    public static final String MINER_LATENCY = "unx:miner_latency_seconds";
    public static final String PING_PONG_LATENCY = "unx:ping_pong_latency_seconds";
    public static final String VERIFY_SIGN_LATENCY = "unx:verify_sign_latency_seconds";
    public static final String LOCK_ACQUIRE_LATENCY = "unx:lock_acquire_latency_seconds";
    public static final String BLOCK_PROCESS_LATENCY = "unx:block_process_latency_seconds";
    public static final String BLOCK_PUSH_LATENCY = "unx:block_push_latency_seconds";
    public static final String BLOCK_GENERATE_LATENCY = "unx:block_generate_latency_seconds";
    public static final String PROCESS_TRANSACTION_LATENCY =
        "unx:process_transaction_latency_seconds";
    public static final String MINER_DELAY = "unx:miner_delay_seconds";
    public static final String UDP_BYTES = "unx:udp_bytes";
    public static final String TCP_BYTES = "unx:tcp_bytes";
    public static final String HTTP_BYTES = "unx:http_bytes";
    public static final String INTERNAL_SERVICE_LATENCY = "unx:internal_service_latency_seconds";

    private Histogram() {
      throw new IllegalStateException("Histogram");
    }

  }

}
