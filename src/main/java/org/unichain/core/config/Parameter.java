package org.unichain.core.config;

import lombok.Getter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Parameter {
  /**
   *  all supported version add here
   */
  //todo review version 6 before release Urc20 and NFT
  public static final Set<Integer> BLOCK_VERSION_SUPPORTED = new HashSet<>(Arrays.asList(
          ChainConstant.BLOCK_VERSION_1,
          ChainConstant.BLOCK_VERSION_2,
          ChainConstant.BLOCK_VERSION_3,
          ChainConstant.BLOCK_VERSION_4,
          ChainConstant.BLOCK_VERSION_5,
          ChainConstant.BLOCK_VERSION_6,
          ChainConstant.BLOCK_VERSION_7));

  public class ChainConstant {
    /**
     * token fees
     */
    public static final long TOKEN_TRANSFER_FEE = 0; // free
    public static final long TOKEN_MAX_TRANSFER_FEE = 10000;
    public static final long TOKEN_MAX_TRANSFER_FEE_RATE = 30; // percent
    public static final long TOKEN_MAX_CREATE_ACC_FEE = 10000000L;
    public static final long TOKEN_DEFAULT_CREATE_ACC_FEE = 1000L; //default number of token will be charged if create new account

    public static final long TRANSFER_FEE = 0; // free
    public static final int WITNESS_STANDBY_LENGTH = 55;
    public static final int SOLIDIFIED_THRESHOLD = 70; // 70%
    public static final int PRIVATE_KEY_LENGTH = 64;
    public static final int MAX_ACTIVE_WITNESS_NUM = 33; //33 witness
    public static final int BLOCK_SIZE = 2_000_000;
    public static final int BLOCK_PRODUCED_INTERVAL = 3000; //ms,produce block period, must be divisible by 60. millisecond
    public static final long CLOCK_MAX_DELAY = 3600000; // 3600 * 1000 ms
    public static final int BLOCK_PRODUCED_TIME_OUT = 50; // 50%
    public static final long PRECISION = 1_000_000;
    public static final long WINDOW_SIZE_MS = 24 * 3600 * 1000L;
    public static final long MS_PER_YEAR = 365 * 24 * 3600 * 1000L;
    public static final long MAINTENANCE_SKIP_SLOTS = 2;
    public static final int SINGLE_REPEAT = 1;
    public static final int BLOCK_FILLED_SLOTS_NUMBER = 56; 
    public static final int MAX_VOTE_NUMBER = 30;
    public static final int MAX_FROZEN_TIME_BY_DAY = 365 * 10; // 10 years

    /**
     * critical: all available block version must declare here
     */
    public static final int BLOCK_VERSION_0 = 0;
    public static final int BLOCK_VERSION_1 = 1;
    public static final int BLOCK_VERSION_2 = 2;
    public static final int BLOCK_VERSION_3 = 3;
    public static final int BLOCK_VERSION_4 = 4;
    public static final int BLOCK_VERSION_5 = 5;
    public static final int BLOCK_VERSION_6 = 6;
    public static final int BLOCK_VERSION_7 = 7;
    public static final int BLOCK_VERSION_8 = 8;
    public static final int BLOCK_VERSION_9 = 9;
    public static final int BLOCK_VERSION_10 = 10;

    /**
     * max unw/token transfer time range
     */
    public static final long UNW_MAX_FUTURE_TRANSFER_TIME_RANGE = 10*31536000000L;//10 years
    public static final long UNW_MAX_FUTURE_TRANSFER_TIME_RANGE_V3 = 50*31536000000L;//50 years
    public static final long UNW_MAX_FUTURE_TRANSFER_TIME_RANGE_UPPER_BOUND = 50*31536000000L;//50 years
    public static final long URC30_MAX_FUTURE_TRANSFER_TIME_RANGE = 10*31536000000L;//10 years
    public static final long URC30_MAX_FUTURE_TRANSFER_TIME_RANGE_V3 = 50*31536000000L;//50 years
    public static final long URC30_MAX_FUTURE_TRANSFER_TIME_RANGE_UPPER_BOUND = 50*31536000000L;//50 years
    public static final long URC30_MAX_AGE = 50*31536000000L;//50 years
    public static final long URC30_DEFAULT_AGE = 20*31536000000L;//20 years
    public static final long URC30_MAX_ACTIVE = 50*31536000000L;//50 years
    public static final long URC30_DEFAULT_AGE_V3 = 50*31536000000L;//50 years
    public static final long URC30_MAX_AGE_V3 = 200*31536000000L;//200 years
    public static final long URC30_MIN_POOL_FEE = 10*1000000L;//10 UNW
    public static final long URC30_CRITICAL_UPDATE_TIME_GUARD = 180*1000L;//3 minutes
  }

  public class NodeConstant {
    public static final long SYNC_RETURN_BATCH_NUM = 1000;
    public static final long SYNC_FETCH_BATCH_NUM = 2000;
    public static final long MAX_BLOCKS_IN_PROCESS = 400;
    public static final long MAX_BLOCKS_ALREADY_FETCHED = 800;
    public static final long MAX_BLOCKS_SYNC_FROM_ONE_PEER = 1000;
    public static final long SYNC_CHAIN_LIMIT_NUM = 500;
    public static final int MAX_TRANSACTION_PENDING = 2000;
    public static final int MAX_HTTP_CONNECT_NUMBER = 50;
  }

  public class NetConstants {
    public static final long GRPC_IDLE_TIME_OUT = 60000L;
    public static final long ADV_TIME_OUT = 20000L;
    public static final long SYNC_TIME_OUT = 5000L;
    public static final long HEAD_NUM_MAX_DELTA = 1000L;
    public static final long HEAD_NUM_CHECK_TIME = 60000L;
    public static final int MAX_INVENTORY_SIZE_IN_MINUTES = 2;
    public static final long NET_MAX_UNW_PER_SECOND = 700L;
    public static final long MAX_UNW_PER_PEER = 200L;
    public static final int NET_MAX_INV_SIZE_IN_MINUTES = 2;
    public static final int MSG_CACHE_DURATION_IN_BLOCKS = 5;
    public static final int MAX_BLOCK_FETCH_PER_PEER = 100;
    public static final int MAX_UNW_FETCH_PER_PEER = 1000;
  }

  public class DatabaseConstants {
    public static final int TRANSACTIONS_COUNT_LIMIT_MAX = 1000;
    public static final int ASSET_ISSUE_COUNT_LIMIT_MAX = 1000;
    public static final int TOKEN_ISSUE_COUNT_LIMIT_MAX = 1000;
    public static final int PROPOSAL_COUNT_LIMIT_MAX = 1000;
    public static final int EXCHANGE_COUNT_LIMIT_MAX = 1000;
  }

  public class AdaptiveResourceLimitConstants {
    public static final int CONTRACT_RATE_NUMERATOR = 99;
    public static final int CONTRACT_RATE_DENOMINATOR = 100;
    public static final int EXPAND_RATE_NUMERATOR = 1000;
    public static final int EXPAND_RATE_DENOMINATOR = 999;
    public static final int PERIODS_MS = 60_000;
    public static final int LIMIT_MULTIPLIER = 1000; //s
  }

  public enum ForkBlockVersionEnum {
    VERSION_1_0(1),
    VERSION_2_0(2);

    @Getter
    private int value;

    ForkBlockVersionEnum(int value) {
      this.value = value;
    }
  }
}
