package org.unichain.common.utils;

import com.google.protobuf.ByteString;
import lombok.var;
import org.unichain.core.capsule.FutureTransferCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import java.util.Objects;

public class ServiceUtil {

  public static void addFutureBalance(Manager dbManager, byte[] toAddress, long amount, long availableTime) {
    var tickDay = Util.makeDayTick(availableTime);
    var tickKey = Util.makeFutureTransferIndexKey(toAddress, tickDay);

    var futureStore = dbManager.getFutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var toAcc = accountStore.get(toAddress);
    var summary = toAcc.getFutureSummary();
    /*
      tick exist: the fasted way!
     */
    if(futureStore.has(tickKey)){
      //save tick
      var tick = futureStore.get(tickKey);
      tick.addBalance(amount);
      futureStore.put(tickKey, tick);

      //save summary
      summary = summary.toBuilder()
              .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
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
      futureStore.put(tickKey, new FutureTransferCapsule(tick));

      //save summary
      summary = Protocol.FutureSummary.newBuilder()
              .setTotalBalance(amount)
              .setTotalDeal(1L)
              .setUpperTime(tickDay)
              .setLowerTime(tickDay)
              .setLowerTick(ByteString.copyFrom(tickKey))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
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
      head.setPrevTick(ByteString.copyFrom(tickKey));
      futureStore.put(headKey, head);

      //save new head
      var newHead = Protocol.Future.newBuilder()
              .setExpireTime(tickDay)
              .setFutureBalance(amount)
              .setNextTick(summary.getLowerTick())
              .clearPrevTick()
              .build();
      futureStore.put(tickKey, new FutureTransferCapsule(newHead));

      //save summary
      summary = summary.toBuilder()
              .setLowerTime(tickDay)
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
              .setLowerTick(ByteString.copyFrom(tickKey))
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
      return ;
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
      futureStore.put(tickKey, new FutureTransferCapsule(newTail));

      //save old tail
      var oldTail = futureStore.get(oldTailKeyBs.toByteArray());
      oldTail.setNextTick(ByteString.copyFrom(tickKey));
      futureStore.put(oldTailKeyBs.toByteArray(), oldTail);

      //save summary
      summary = summary.toBuilder()
              .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
              .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
              .setUpperTick(ByteString.copyFrom(tickKey))
              .setUpperTime(tickDay)
              .build();
      toAcc.setFutureSummary(summary);
      accountStore.put(toAddress, toAcc);
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
        futureStore.put(tickKey, new FutureTransferCapsule(newTick));

        //save prev
        searchTick.setNextTick(ByteString.copyFrom(tickKey));
        futureStore.put(searchKeyBs.toByteArray(), searchTick);

        //save next
        var oldNextTick = futureStore.get(oldNextTickKey.toByteArray());
        oldNextTick.setPrevTick(ByteString.copyFrom(tickKey));
        futureStore.put(oldNextTickKey.toByteArray(), oldNextTick);

        //save summary
        summary = summary.toBuilder()
                .setTotalBalance(Math.addExact(summary.getTotalBalance(), amount))
                .setTotalDeal(Math.incrementExact(summary.getTotalDeal()))
                .build();

        toAcc.setFutureSummary(summary);
        accountStore.put(toAddress, toAcc);
        return;
      }
      else {
        searchKeyBs = searchTick.getPrevTick();
      }
    }
  }

  public static void removeFutureTick(Manager dbManager, byte[] ownerAddress, FutureTransferCapsule futureTick) {
    var futureStore = dbManager.getFutureTransferStore();
    var accountStore = dbManager.getAccountStore();
    var ownerAcc = accountStore.get(ownerAddress);
    var ownerSummary = ownerAcc.getFutureSummary();

    // attach currentTick.prev tick to currentTick.next
    var prevTickPointer = futureTick.getPrevTick();
    var prevTick = futureStore.get(prevTickPointer.toByteArray());

    var nextTickPointer = futureTick.getNextTick();
    var nextTick = futureStore.get(nextTickPointer.toByteArray());

    // change nextTick of prev and prevTick of next in ownerSummary
    prevTick.setNextTick(futureTick.getNextTick());
    nextTick.setPrevTick(futureTick.getPrevTick());

    futureStore.put(prevTickPointer.toByteArray(), prevTick);
    futureStore.put(nextTickPointer.toByteArray(), nextTick);

    ownerSummary.toBuilder()
            .setTotalDeal(Math.addExact(ownerSummary.getTotalDeal(), -1))
            .setTotalBalance(Math.addExact(ownerSummary.getTotalBalance(), -futureTick.getBalance()))
            .build();
    ownerAcc.setFutureSummary(ownerSummary);
    accountStore.put(ownerAddress, ownerAcc);
  }
}
