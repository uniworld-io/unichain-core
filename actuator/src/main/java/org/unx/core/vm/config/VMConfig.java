package org.unx.core.vm.config;

import lombok.Setter;
import org.unx.common.parameter.CommonParameter;

/**
 * For developer only
 */
public class VMConfig {

  private static boolean vmTraceCompressed = false;

  @Setter
  private static boolean vmTrace = false;

  private static boolean ALLOW_UVM_TRANSFER_URC10 = false;

  private static boolean ALLOW_UVM_CONSTANTINOPLE = false;

  private static boolean ALLOW_MULTI_SIGN = false;

  private static boolean ALLOW_UVM_SOLIDITY_059 = false;

  private static boolean ALLOW_SHIELDED_URC20_TRANSACTION = false;

  private static boolean ALLOW_UVM_ISTANBUL = false;

  private static boolean ALLOW_UVM_FREEZE = false;

  private static boolean ALLOW_UVM_VOTE = false;

  private static boolean ALLOW_UVM_LONDON = false;

  private static boolean ALLOW_UVM_COMPATIBLE_EVM = false;

  private static boolean ALLOW_HIGHER_LIMIT_FOR_MAX_CPU_TIME_OF_ONE_TX = false;

  private VMConfig() {
  }

  public static boolean vmTrace() {
    return vmTrace;
  }

  public static boolean vmTraceCompressed() {
    return vmTraceCompressed;
  }

  public static void initVmHardFork(boolean pass) {
    CommonParameter.ENERGY_LIMIT_HARD_FORK = pass;
  }

  public static void initAllowMultiSign(long allow) {
    ALLOW_MULTI_SIGN = allow == 1;
  }

  public static void initAllowUvmTransferUrc10(long allow) {
    ALLOW_UVM_TRANSFER_URC10 = allow == 1;
  }

  public static void initAllowUvmConstantinople(long allow) {
    ALLOW_UVM_CONSTANTINOPLE = allow == 1;
  }

  public static void initAllowUvmSolidity059(long allow) {
    ALLOW_UVM_SOLIDITY_059 = allow == 1;
  }

  public static void initAllowShieldedURC20Transaction(long allow) {
    ALLOW_SHIELDED_URC20_TRANSACTION = allow == 1;
  }

  public static void initAllowUvmIstanbul(long allow) {
    ALLOW_UVM_ISTANBUL = allow == 1;
  }

  public static void initAllowUvmFreeze(long allow) {
    ALLOW_UVM_FREEZE = allow == 1;
  }

  public static void initAllowUvmVote(long allow) {
    ALLOW_UVM_VOTE = allow == 1;
  }

  public static void initAllowUvmLondon(long allow) {
    ALLOW_UVM_LONDON = allow == 1;
  }

  public static void initAllowUvmCompatibleEvm(long allow) {
    ALLOW_UVM_COMPATIBLE_EVM = allow == 1;
  }

  public static void initAllowHigherLimitForMaxCpuTimeOfOneTx(long allow) {
    ALLOW_HIGHER_LIMIT_FOR_MAX_CPU_TIME_OF_ONE_TX = allow == 1;
  }

  public static boolean getEnergyLimitHardFork() {
    return CommonParameter.ENERGY_LIMIT_HARD_FORK;
  }

  public static boolean allowUvmTransferUrc10() {
    return ALLOW_UVM_TRANSFER_URC10;
  }

  public static boolean allowUvmConstantinople() {
    return ALLOW_UVM_CONSTANTINOPLE;
  }

  public static boolean allowMultiSign() {
    return ALLOW_MULTI_SIGN;
  }

  public static boolean allowUvmSolidity059() {
    return ALLOW_UVM_SOLIDITY_059;
  }

  public static boolean allowShieldedURC20Transaction() {
    return ALLOW_SHIELDED_URC20_TRANSACTION;
  }

  public static boolean allowUvmIstanbul() {
    return ALLOW_UVM_ISTANBUL;
  }

  public static boolean allowUvmFreeze() {
    return ALLOW_UVM_FREEZE;
  }

  public static boolean allowUvmVote() {
    return ALLOW_UVM_VOTE;
  }

  public static boolean allowUvmLondon() {
    return ALLOW_UVM_LONDON;
  }

  public static boolean allowUvmCompatibleEvm() {
    return ALLOW_UVM_COMPATIBLE_EVM;
  }

  public static boolean allowHigherLimitForMaxCpuTimeOfOneTx() {
    return ALLOW_HIGHER_LIMIT_FOR_MAX_CPU_TIME_OF_ONE_TX;
  }
}
