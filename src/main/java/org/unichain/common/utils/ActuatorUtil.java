package org.unichain.common.utils;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.core.Wallet;
import org.springframework.util.Assert;
import org.unichain.core.capsule.FutureTransferCapsule;
import org.unichain.core.capsule.urc20.Urc20FutureTokenCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

public class ActuatorUtil {

  public static void addFutureDeal(final Manager dbManager, byte[] address, long amount, long availableTime) {
    var tickDay = Util.makeDayTick(availableTime);
    var dealKey = Util.makeFutureTransferIndexKey(address, tickDay);

    var futureStore = dbManager.getFutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var toAcc = accountStore.get(address);
    var summary = toAcc.getFutureSummary();

    //tick exist: the fasted way!
    if(futureStore.has(dealKey)){
      //save tick
      var tick = futureStore.get(dealKey);
      tick.addBalance(amount);
      futureStore.put(dealKey, tick);

      //save summary
      summary = summary.toBuilder()
              .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(address, toAcc);
      return;
    }

    //first tick ever: add new tick, add summary
    if(Objects.isNull(summary)){
      //save new tick
      var tick = Protocol.Future.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .clearPrevTick()
              .build();
      futureStore.put(dealKey, new FutureTransferCapsule(tick));

      //save summary
      summary = Protocol.FutureSummary.newBuilder()
              .setTotalBalance(amount)
              .setTotalDeal(1L)
              .setUpperTime(tickDay)
              .setLowerTime(tickDay)
              .setLowerTick(ByteString.copyFrom(dealKey))
              .setUpperTick(ByteString.copyFrom(dealKey))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(address, toAcc);
      return;
    }

    //other ticks exist
    var headKey = summary.getLowerTick().toByteArray();
    var head = futureStore.get(headKey);
    var headTime = head.getExpireTime();

    //if new tick is head
    if(tickDay < headTime){
      //save old head
      head.setPrevTick(ByteString.copyFrom(dealKey));
      futureStore.put(headKey, head);

      //save new head
      var newHead = Protocol.Future.newBuilder()
              .setExpireTime(tickDay)
              .setFutureBalance(amount)
              .setNextTick(summary.getLowerTick())
              .clearPrevTick()
              .build();
      futureStore.put(dealKey, new FutureTransferCapsule(newHead));

      //save summary
      summary = summary.toBuilder()
              .setLowerTime(tickDay)
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
              .setLowerTick(ByteString.copyFrom(dealKey))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(address, toAcc);
      return;
    }

    //if new tick is tail
    if(tickDay > headTime){
      //save new tail
      var oldTailKeyBs = summary.getUpperTick();
      var newTail = Protocol.Future.newBuilder()
              .setFutureBalance(amount)
              .setExpireTime(tickDay)
              .clearNextTick()
              .setPrevTick(oldTailKeyBs)
              .build();
      futureStore.put(dealKey, new FutureTransferCapsule(newTail));

      //save old tail
      var oldTail = futureStore.get(oldTailKeyBs.toByteArray());
      oldTail.setNextTick(ByteString.copyFrom(dealKey));
      futureStore.put(oldTailKeyBs.toByteArray(), oldTail);

      //save summary
      summary = summary.toBuilder()
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
              .setUpperTick(ByteString.copyFrom(dealKey))
              .setUpperTime(tickDay)
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(address, toAcc);
      return;
    }

    //otherwise: lookup slot to insert
    var searchKeyBs = summary.getUpperTick();
    while (true){
      var searchTick = futureStore.get(searchKeyBs.toByteArray());
      if(searchTick.getExpireTime() < tickDay)
      {
        var oldNextTickKey = searchTick.getNextTick();

        //save new tick
        var newTick = Protocol.Future.newBuilder()
                .setExpireTime(tickDay)
                .setFutureBalance(amount)
                .setPrevTick(searchKeyBs)
                .setNextTick(oldNextTickKey)
                .build();
        futureStore.put(dealKey, new FutureTransferCapsule(newTick));

        //save prev
        searchTick.setNextTick(ByteString.copyFrom(dealKey));
        futureStore.put(searchKeyBs.toByteArray(), searchTick);

        //save next
        var oldNextTick = futureStore.get(oldNextTickKey.toByteArray());
        oldNextTick.setPrevTick(ByteString.copyFrom(dealKey));
        futureStore.put(oldNextTickKey.toByteArray(), oldNextTick);

        //save summary
        summary = summary.toBuilder()
                .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
                .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
                .build();

        toAcc.setFutureSummary(summary);
        accountStore.put(address, toAcc);
        return;
      }
      else {
        searchKeyBs = searchTick.getPrevTick();
      }
    }
  }

  /**
   * Cut one deal by amount:
   * - if [amount >= balance] --> remove deal
   * - else update deal and summary
   */
  public static void cutFutureDeal(final Manager dbManager, byte[] fromAddr, byte[] dealKey, long amt) {
    var futureStore = dbManager.getFutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var fromAcc = accountStore.get(fromAddr);

    var summary = fromAcc.getFutureSummary();
    var deal = futureStore.get(dealKey);
    Assert.isTrue(deal.getBalance() >= amt, "cut amount over deal balance!");

    if (deal.getBalance() > amt)
    {
      /**
       * if smaller amount be cut, just update
       */

      //update deal
      deal.setBalance(Math.subtractExact(deal.getBalance(), amt));
      futureStore.put(dealKey, deal);

      //update summary
      summary = summary.toBuilder()
              .setTotalBalance(Math.subtractExact(summary.getTotalBalance(), amt))
              .build();
      fromAcc.setFutureSummary(summary);
      accountStore.put(fromAddr, fromAcc);
    }
    else
    {
      /**
       * move all amount, so just remove deal
       */

      //one deal, just clear all
      if (summary.getTotalDeal() <= 1) {
        fromAcc.clearFuture();
        accountStore.put(fromAddr, fromAcc);
        futureStore.delete(dealKey);
        return;
      }

      //other deals exist
      var headKey = summary.getLowerTick().toByteArray();
      if (Arrays.equals(headKey, dealKey)) {
        /**
         * this deal is head, so...
         */
        //update summary
        var head = futureStore.get(headKey);
        var nextTickKey = head.getNextTick().toByteArray();
        var nextTick = futureStore.get(nextTickKey);
        nextTick.clearPrevTick();
        futureStore.put(nextTickKey, nextTick);

        summary = summary.toBuilder()
                .setLowerTick(head.getNextTick())
                .setLowerTime(nextTick.getExpireTime())
                .setTotalBalance(Math.subtractExact(summary.getTotalBalance(), deal.getBalance()))
                .setTotalDeal(Math.subtractExact(summary.getTotalDeal(), 1))
                .build();
        fromAcc.setFutureSummary(summary);
        accountStore.put(fromAddr, fromAcc);

        //delete deal
        futureStore.delete(dealKey);
        return;
      }

      var tailKey = summary.getUpperTick().toByteArray();
      if (Arrays.equals(tailKey, dealKey)) {
        /**
         * if this deal is tail, so....
         */
        //update prev deal
        var tail = futureStore.get(tailKey);
        var prevTickKey = tail.getPrevTick().toByteArray();
        var prevTick = futureStore.get(prevTickKey);
        prevTick.clearNextTick();
        futureStore.put(prevTickKey, prevTick);

        //update summary
        summary = summary.toBuilder()
                .setUpperTick(tail.getPrevTick())
                .setUpperTime(prevTick.getExpireTime())
                .setTotalBalance(Math.subtractExact(summary.getTotalBalance(), deal.getBalance()))
                .setTotalDeal(Math.subtractExact(summary.getTotalDeal(), 1))
                .build();
        fromAcc.setFutureSummary(summary);
        accountStore.put(fromAddr, fromAcc);

        //delete deal
        futureStore.delete(dealKey);
        return;
      }

      /**
       *  finally: this deal is the middle node, so...
       */
      //update summary
      var prevTickPointer = deal.getPrevTick().toByteArray();
      var prevTick = futureStore.get(prevTickPointer);

      var nextTickPointer = deal.getNextTick().toByteArray();
      var nextTick = futureStore.get(nextTickPointer);

      prevTick.setNextTick(deal.getNextTick());
      nextTick.setPrevTick(deal.getPrevTick());

      futureStore.put(prevTickPointer, prevTick);
      futureStore.put(nextTickPointer, nextTick);

      summary = summary.toBuilder()
              .setTotalDeal(Math.subtractExact(summary.getTotalDeal(), 1))
              .setTotalBalance(Math.subtractExact(summary.getTotalBalance(), deal.getBalance()))
              .build();
      fromAcc.setFutureSummary(summary);
      accountStore.put(fromAddr, fromAcc);

      //delete deal
      futureStore.delete(dealKey);
    }
  }

  public static void addUrc20Future(final Manager dbManager, byte[] toAddress, byte[] contractAddr, BigInteger amount, long availableTime){
    var addrBase58 = Wallet.encode58Check(contractAddr);
    var tickDay = Util.makeDayTick(availableTime);
    var tickKey = Util.makeUrc20FutureTokenIndexKey(toAddress, addrBase58, tickDay);

    var contractCap = dbManager.getUrc20ContractStore().get(contractAddr);
    var futureStore = dbManager.getUrc20FutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var toAcc = accountStore.get(toAddress);
    var summary = toAcc.getUrc20FutureTokenSummary(addrBase58);

    /*
      tick exist: the fasted way!
     */
    if(futureStore.has(tickKey)){
      //update tick
      var tick = futureStore.get(tickKey);
      tick.addBalance(amount);
      futureStore.put(tickKey, tick);

      //update account summary
      summary = summary.toBuilder()
              .setTotalValue(new BigInteger(summary.getTotalValue()).add(amount).toString())
              .build();
      toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /*
      the first tick ever.
     */
    if(Objects.isNull(summary)){
      //save tick
      var tick = Protocol.Urc20FutureToken.newBuilder()
              .setFutureBalance(amount.toString())
              .setExpireTime(tickDay)
              .clearNextTick()
              .clearPrevTick()
              .build();
      futureStore.put(tickKey, new Urc20FutureTokenCapsule(tick));

      //save summary
      summary = Protocol.Urc20FutureTokenSummary.newBuilder()
              .setAddress(ByteString.copyFrom(contractAddr))
              .setSymbol(contractCap.getSymbol())
              .setTotalValue(amount.toString())
              .setTotalDeal(1)
              .setUpperBoundTime(tickDay)
              .setLowerBoundTime(tickDay)
              .setLowerTick(ByteString.copyFrom(tickKey))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /*
      other tick exist
     */
    var headKey = summary.getLowerTick().toByteArray();
    var head = futureStore.get(headKey);
    var headTime = head.getExpireTime();
    /*
      if new tick is head
     */
    if(tickDay < headTime){
      //save old head pointer
      head.setPrevTick(ByteString.copyFrom(tickKey));
      futureStore.put(headKey, head);

      //save new head
      var newHead = Protocol.Urc20FutureToken.newBuilder()
              .setExpireTime(tickDay)
              .setFutureBalance(amount.toString())
              .setNextTick(summary.getLowerTick())
              .clearPrevTick()
              .build();
      futureStore.put(tickKey, new Urc20FutureTokenCapsule(newHead));

      //save summary
      summary = summary.toBuilder()
              .setLowerBoundTime(tickDay)
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalValue(new BigInteger(summary.getTotalValue()).add(amount).toString())
              .setLowerTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
      accountStore.put(toAddress, toAcc);
      return ;
    }

    /*
      if new tick is tail
     */
    if(tickDay > headTime){
      var oldTailKeyBs = summary.getUpperTick();

      //save new tail
      var newTail = Protocol.Urc20FutureToken.newBuilder()
              .setFutureBalance(amount.toString())
              .setExpireTime(tickDay)
              .clearNextTick()
              .setPrevTick(oldTailKeyBs)
              .build();
      futureStore.put(tickKey, new Urc20FutureTokenCapsule(newTail));

      //save old tail
      var oldTail = futureStore.get(oldTailKeyBs.toByteArray());
      oldTail.setNextTick(ByteString.copyFrom(tickKey));
      futureStore.put(oldTailKeyBs.toByteArray(), oldTail);

      //save summary
      summary = summary.toBuilder()
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalValue(new BigInteger(summary.getTotalValue()).add(amount).toString())
              .setUpperTick(ByteString.copyFrom(tickKey))
              .setUpperBoundTime(tickDay)
              .build();
      toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
      accountStore.put(toAddress, toAcc);
      return;
    }

    /*
      lookup slot and insert tick
     */
    var searchKeyBs = summary.getUpperTick();
    while (true){
      var searchTick = futureStore.get(searchKeyBs.toByteArray());
      if(searchTick.getExpireTime() < tickDay)
      {
        var oldNextTickKey = searchTick.getNextTick();

        //save new tick
        var newTick = Protocol.Urc20FutureToken.newBuilder()
                .setExpireTime(tickDay)
                .setFutureBalance(amount.toString())
                .setPrevTick(searchKeyBs)
                .setNextTick(oldNextTickKey)
                .build();
        futureStore.put(tickKey, new Urc20FutureTokenCapsule(newTick));

        //save prev tick
        searchTick.setNextTick(ByteString.copyFrom(tickKey));
        futureStore.put(searchKeyBs.toByteArray(), searchTick);

        //save next tick
        var oldNextTick = futureStore.get(oldNextTickKey.toByteArray());
        oldNextTick.setPrevTick(ByteString.copyFrom(tickKey));
        futureStore.put(oldNextTickKey.toByteArray(), oldNextTick);

        //save tick summary
        summary = summary.toBuilder()
                .setTotalValue(new BigInteger(summary.getTotalValue()).add(amount).toString())
                .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
                .build();

        toAcc.setUrc20FutureTokenSummary(addrBase58, summary);
        accountStore.put(toAddress, toAcc);
        return;
      }
      else {
        searchKeyBs = searchTick.getPrevTick();
      }
    }
  }

}
