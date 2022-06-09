package org.unichain.common.utils;

import com.google.protobuf.ByteString;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.capsule.FutureTransferCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import java.util.Arrays;
import java.util.Objects;

public class ActuatorUtil {

  public static void addFutureDeal(Manager dbManager, byte[] address, long amount, long availableTime) {
    var tickDay = Util.makeDayTick(availableTime);
    var dealKey = Util.makeFutureTransferIndexKey(address, tickDay);

    var futureStore = dbManager.getFutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var toAcc = accountStore.get(address);
    var summary = toAcc.getFutureSummary();
    /*
      tick exist: the fasted way!
     */
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

    /*
      first tick ever: add new tick, add summary
     */
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

    /*
      other ticks exist
     */
    var headKey = summary.getLowerTick().toByteArray();
    var head = futureStore.get(headKey);
    var headTime = head.getExpireTime();

    /*
      if new tick is head
     */
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

    /*
      if new tick is tail
     */
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

    /*
      otherwise: lookup slot to insert
     */
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
   * Detach deal:
   * - if all --> just remove deal
   * - if not --> just update deal, update summary
   */
  public static void detachFutureDeal(Manager dbManager, byte[] ownerAddress, byte[] dealKey, long amt) {
    var futureStore = dbManager.getFutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var ownerAcc = accountStore.get(ownerAddress);

    var summary = ownerAcc.getFutureSummary();
    var deal = futureStore.get(dealKey);
    Assert.isTrue(deal.getBalance() >= amt, "detach amount over deal balance!");

    if (deal.getBalance() > amt) {
      //just update, not delete
      deal.setBalance(Math.subtractExact(deal.getBalance(), amt));
      futureStore.put(dealKey, deal);

      summary = summary.toBuilder()
              .setTotalBalance(Math.subtractExact(summary.getTotalBalance(), amt))
              .build();
      ownerAcc.setFutureSummary(summary);
      accountStore.put(ownerAddress, ownerAcc);
      return;
    } else {
      /**
       * Delete deal
       */

      //if one deal: just remove
      if (summary.getTotalDeal() == 1) {
        ownerAcc.clearFuture();
        accountStore.put(ownerAddress, ownerAcc);
        futureStore.delete(dealKey);
        return;
      }

      //if this deal is head: update next
      var headKey = summary.getLowerTick().toByteArray();
      if (Arrays.equals(headKey, dealKey)) {
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
        ownerAcc.setFutureSummary(summary);
        accountStore.put(ownerAddress, ownerAcc);
        futureStore.delete(dealKey);
        return;
      }

      // if this deal is tail: update prev
      var tailKey = summary.getUpperTick().toByteArray();
      if (Arrays.equals(dealKey, tailKey)) {
        var tail = futureStore.get(tailKey);
        var prevTickKey = tail.getPrevTick().toByteArray();
        var prevTick = futureStore.get(prevTickKey);
        prevTick.clearNextTick();
        futureStore.put(prevTickKey, prevTick);

        summary = summary.toBuilder()
                .setUpperTick(tail.getPrevTick())
                .setUpperTime(prevTick.getExpireTime())
                .setTotalBalance(Math.subtractExact(summary.getTotalDeal(), deal.getBalance()))
                .setTotalDeal(Math.subtractExact(summary.getTotalDeal(), 1))
                .build();
        ownerAcc.setFutureSummary(summary);
        accountStore.put(ownerAddress, ownerAcc);
        futureStore.delete(dealKey);
        return;
      }

      // if this deal is middle: update link & summary
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
      ownerAcc.setFutureSummary(summary);
      accountStore.put(ownerAddress, ownerAcc);
      futureStore.delete(dealKey);
    }
  }
}
