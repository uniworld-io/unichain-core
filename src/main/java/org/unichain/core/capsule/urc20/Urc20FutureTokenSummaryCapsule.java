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

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol.Urc20FutureTokenSummary;

import java.math.BigInteger;

@Slf4j(topic = "capsule")
public class Urc20FutureTokenSummaryCapsule implements ProtoCapsule<Urc20FutureTokenSummary> {

  private Urc20FutureTokenSummary ctx;

  public Urc20FutureTokenSummaryCapsule(byte[] data) {
    try {
      this.ctx = Urc20FutureTokenSummary.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc20FutureTokenSummaryCapsule(Urc20FutureTokenSummary ctx) {
    this.ctx = ctx;
  }

  public byte[] getData() {
    return this.ctx.toByteArray();
  }

  @Override
  public Urc20FutureTokenSummary getInstance() {
    return this.ctx;
  }

  @Override
  public String toString() {
    return this.ctx.toString();
  }

  public BigInteger getTotalValue() {
    return new BigInteger(this.ctx.getTotalValue());
  }
}
