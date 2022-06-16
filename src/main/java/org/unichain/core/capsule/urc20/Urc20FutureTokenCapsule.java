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
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol.Urc20FutureToken;

import java.math.BigInteger;

@Slf4j(topic = "capsule")
public class Urc20FutureTokenCapsule implements ProtoCapsule<Urc20FutureToken> {
  private Urc20FutureToken index;

  public Urc20FutureTokenCapsule(byte[] data) {
    try {
      this.index = Urc20FutureToken.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc20FutureTokenCapsule(Urc20FutureToken index) {
    this.index = index;
  }

  public byte[] getData() {
    return this.index.toByteArray();
  }

  @Override
  public Urc20FutureToken getInstance() {
    return this.index;
  }

  @Override
  public String toString() {
    return index.toString();
  }

  public ByteString getPrevTick(){
    return index.getPrevTick();
  }

  public ByteString getNextTick(){
    return index.getNextTick();
  }

  public void setPrevTick(ByteString tick){
    if(tick == null)
      this.index = index.toBuilder().clearPrevTick().build();
    else
      this.index = index.toBuilder().setPrevTick(tick).build();
  }

  public void clearPrevTick(){
      this.index = index.toBuilder().clearPrevTick().build();
  }

  public void setNextTick(ByteString tick){
    if(tick == null)
      this.index = index.toBuilder().clearNextTick().build();
    else
      this.index = index.toBuilder().setNextTick(tick).build();
  }

  public void clearNextTick(){
      this.index = index.toBuilder().clearNextTick().build();
  }

  public void addBalance(BigInteger balance){
    index = index.toBuilder()
            .setFutureBalance(new BigInteger(index.getFutureBalance()).add(balance).toString())
            .build();
  }

  public BigInteger getBalance(){
    return new BigInteger(index.getFutureBalance());
  }

  public long getExpireTime(){
    return index.getExpireTime();
  }
}
