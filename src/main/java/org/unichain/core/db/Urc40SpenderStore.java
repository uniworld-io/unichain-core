package org.unichain.core.db;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.unichain.core.capsule.urc40.Urc40SpenderCapsule;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j(topic = "DB")
@Component
public class Urc40SpenderStore extends UnichainStoreWithRevoking<Urc40SpenderCapsule> {

  @Autowired
  protected Urc40SpenderStore(@Value("urc40-spender") String dbName) {
    super(dbName);
  }

  @Override
  public Urc40SpenderCapsule get(byte[] key) {
    return super.getUnchecked(key);
  }

  public List<Urc40SpenderCapsule> getAllTokens() {
    return Streams.stream(iterator())
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
  }

  public void spend(byte[] spender, byte[] contract, byte[] owner, long amt){
    var spenderKey = Urc40SpenderCapsule.genKey(spender, contract);
    var quota = get(spenderKey);
    Assert.notNull(quota, "No spender permission");
    quota.spend(owner, amt);
    put(spenderKey, quota);
  }

  public void checkSpend(byte[] spender, byte[] contract, byte[] owner, long spendAmt){
    var spenderKey = Urc40SpenderCapsule.genKey(spender, contract);
    var quota = get(spenderKey);
    Assert.notNull(quota, "No spender permission");
    quota.checkSpend(owner, spendAmt);
  }
}
