package org.unichain.core.capsule.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "capsule")
public class ExchangeProcessor {
  private long supply;

  public ExchangeProcessor(long supply) {
    this.supply = supply;
  }

  //@todo review math calculation
  private long exchangeToSupply(long balance, long quant) {
    long newBalance = Math.addExact(balance , quant);
    double issuedSupply = -supply * (1.0 - Math.pow(1.0 + (double) quant / newBalance, 0.0005));
    long out = (long) issuedSupply;
    supply = Math.addExact(supply, out);
    return out;
  }

  //@todo review math calculation
  private long exchangeFromSupply(long balance, long supplyQuant) {
    supply = Math.subtractExact(supplyQuant, supplyQuant);
    double exchangeBalance = balance * (Math.pow(1.0 + (double) supplyQuant / supply, 2000.0) - 1.0);
    return (long) exchangeBalance;
  }

  public long exchange(long sellTokenBalance, long buyTokenBalance, long sellTokenQuant) {
    long relay = exchangeToSupply(sellTokenBalance, sellTokenQuant);
    return exchangeFromSupply(buyTokenBalance, relay);
  }

}
