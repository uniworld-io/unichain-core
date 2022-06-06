/*
 * unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * unichain-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.unichain.core.capsule.urc20;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Urc20Spender;

import java.util.Objects;

@Slf4j(topic = "capsule")
public class Urc20SpenderCapsule implements ProtoCapsule<Urc20Spender> {
  private Urc20Spender ctx;

  public Urc20SpenderCapsule(byte[] data) {
    try {
      this.ctx = Urc20Spender.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc20SpenderCapsule(byte[] spender, byte[]  contract, byte[] owner, long limit) {
    this.ctx = Urc20Spender.newBuilder()
                .setSpender(ByteString.copyFrom(spender))
                .setContract(ByteString.copyFrom(contract))
                .putQuotas(Wallet.encode58Check(owner),
                        Protocol.Urc20SpenderQuota.newBuilder()
                                .setLimit(limit)
                                .setSpent(0L)
                                .setAvail(limit)
                                .build())
                .build();
  }

  public Urc20SpenderCapsule(Urc20Spender ctx) {
    this.ctx = ctx;
  }

  public byte[] getData() {
    return this.ctx.toByteArray();
  }

  @Override
  public Urc20Spender getInstance() {
    return this.ctx;
  }

  @Override
  public String toString() {
    return this.ctx.toString();
  }

  public byte[] getSpender(){
    return ctx.getSpender().toByteArray();
  }

  public byte[] getContractAddr(){
    return ctx.getContract().toByteArray();
  }

  public void setSpender(byte[] spender){
    ctx = ctx.toBuilder().setSpender(ByteString.copyFrom(spender)).build();
  }

  public void setContractAddr(byte[] contractAddr){
    ctx = ctx.toBuilder().setContract(ByteString.copyFrom(contractAddr)).build();
  }

  public void setQuotaTo(byte[] owner, long limit){
      Assert.isTrue(limit >= 0, "Bad limit value: must be gte zero");
      var base58 = Wallet.encode58Check(owner);
      var quota = ctx.getQuotasMap().get(base58);
      if(Objects.isNull(quota)){
          quota = Protocol.Urc20SpenderQuota.newBuilder()
                  .setLimit(limit)
                  .setAvail(limit)
                  .setSpent(0)
                  .build();
          ctx = ctx.toBuilder().putQuotas(base58, quota).build();
      }
      else {
        Assert.isTrue(quota.getSpent() < limit, "Spent amount already reached limit");
        var diff = Math.subtractExact(limit, quota.getLimit());
        quota = quota.toBuilder()
                .setLimit(limit)
                .setAvail(Math.addExact(quota.getAvail(), diff))
                .build();
        ctx = ctx.toBuilder().putQuotas(base58, quota).build();
      }
  }

  public void checkSetQuota(byte[] owner, long limit, long tokenAvailable){
    Assert.isTrue(limit >= 0, "Bad quota limit value: must be gte zero");
    var base58 = Wallet.encode58Check(owner);
    var quota = ctx.getQuotasMap().get(base58);
    if(Objects.isNull(quota)){
      Assert.isTrue(limit <= tokenAvailable, "Spender amount reached out available token!");
    }
    else {
      Assert.isTrue(quota.getSpent() < limit, "Already spent amount larger than provided limit");
      Assert.isTrue(Math.subtractExact(limit, quota.getSpent()) <= tokenAvailable, "Spender amount reached out available token!");
    }
  }

  public void spend(byte[] owner, long amt){
    Assert.isTrue(amt > 0, "amount must be positive");
    var base58 = Wallet.encode58Check(owner);
    var quota = ctx.getQuotasMap().get(base58);
    Assert.notNull(quota, "no spend quota for: " + base58);
    Assert.isTrue(quota.getAvail() >= amt, "out of spending quota: " + base58);
    var avail = quota.getAvail();
    var spent = quota.getSpent();
    quota = quota.toBuilder()
            .setAvail(Math.subtractExact(avail, amt))
            .setSpent(Math.addExact(spent, amt))
            .build();
    ctx = ctx.toBuilder().putQuotas(base58, quota).build();
  }

  public void checkSpend(byte[] owner, long amt){
    Assert.isTrue(amt > 0, "amount must be positive");
    var base58 = Wallet.encode58Check(owner);
    var quota = ctx.getQuotasMap().get(base58);
    Assert.notNull(quota, "No spend permission to: " + base58);
    Assert.isTrue(quota.getAvail() >= amt, "out of spending quota: " + base58);
  }

  public long getQuota(byte[] owner){
    var base58 = Wallet.encode58Check(owner);
    var quota = ctx.getQuotasMap().get(base58);
    return Objects.isNull(quota)  ? 0L : quota.getAvail();
  }

  public byte[] getKey(){
      return ctx.getSpender()
              .concat(ctx.getContract())
              .toByteArray();
  }

  public static byte[] genKey(byte[] spender, byte[] contract){
    return ByteString.copyFrom(spender)
            .concat(ByteString.copyFrom(contract))
            .toByteArray();
  }
}
