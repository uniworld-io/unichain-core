package org.unx.common.utils;

import com.beust.jcommander.internal.Lists;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.junit.Ignore;
import org.junit.Test;
import org.unx.core.capsule.WitnessCapsule;

@Slf4j
@Ignore
public class RandomGeneratorTest {

  @Test
  public void shuffle() {
    final List<WitnessCapsule> witnessCapsuleListBefore = this.getWitnessList();
    logger.info("updateWitnessSchedule,before: " + getWitnessStringList(witnessCapsuleListBefore));
    final List<WitnessCapsule> witnessCapsuleListAfter = new RandomGenerator<WitnessCapsule>()
        .shuffle(witnessCapsuleListBefore, DateTime.now().getMillis());
    logger.info("updateWitnessSchedule,after: " + getWitnessStringList(witnessCapsuleListAfter));
  }

  private List<WitnessCapsule> getWitnessList() {
    final List<WitnessCapsule> witnessCapsuleList = Lists.newArrayList();
    final WitnessCapsule witnessUnx = new WitnessCapsule(
        ByteString.copyFrom("00000000001".getBytes()), 0, "");
    final WitnessCapsule witnessOlivier = new WitnessCapsule(
        ByteString.copyFrom("00000000003".getBytes()), 100, "");
    final WitnessCapsule witnessVivider = new WitnessCapsule(
        ByteString.copyFrom("00000000005".getBytes()), 200, "");
    final WitnessCapsule witnessSenaLiu = new WitnessCapsule(
        ByteString.copyFrom("00000000006".getBytes()), 300, "");
    witnessCapsuleList.add(witnessUnx);
    witnessCapsuleList.add(witnessOlivier);
    witnessCapsuleList.add(witnessVivider);
    witnessCapsuleList.add(witnessSenaLiu);
    return witnessCapsuleList;
  }

  private List<String> getWitnessStringList(List<WitnessCapsule> witnessStates) {
    return witnessStates.stream()
        .map(witnessCapsule -> ByteArray.toHexString(witnessCapsule.getAddress().toByteArray()))
        .collect(Collectors.toList());
  }
}