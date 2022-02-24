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

package org.unichain.core.capsule;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.unichain.common.utils.ByteArray;
import org.unichain.protos.Contract;
import org.unichain.protos.Protocol;

import java.util.Arrays;

//@todo later
@Slf4j(topic = "capsule")
public class NftTokenCapsule implements ProtoCapsule<Protocol.NftToken> {
  private Protocol.NftToken token;

  public NftTokenCapsule(byte[] data) {
    try {
      this.token = Protocol.NftToken.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public NftTokenCapsule(Protocol.NftToken token) {
    this.token = token;
  }

  public byte[] getData() {
    return this.token.toByteArray();
  }

  @Override
  public Protocol.NftToken getInstance() {
    return this.token;
  }

  @Override
  public String toString() {
    return this.token.toString();
  }

  public byte[] getKey(){
    return ArrayUtils.addAll(token.getTemplateId().toByteArray(), ByteArray.fromLong(token.getId()));
  }
}
