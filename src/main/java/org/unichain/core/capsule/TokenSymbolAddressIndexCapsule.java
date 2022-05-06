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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.protos.Protocol.TokenSymbolAddressIndex;

@Slf4j(topic = "capsule")
public class TokenSymbolAddressIndexCapsule implements ProtoCapsule<TokenSymbolAddressIndex> {
  private TokenSymbolAddressIndex message;

  public TokenSymbolAddressIndexCapsule(byte[] data) {
    try {
      this.message = TokenSymbolAddressIndex.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public TokenSymbolAddressIndexCapsule(TokenSymbolAddressIndex message) {
    this.message = message;
  }

  public byte[] getData() {
    return this.message.toByteArray();
  }

  @Override
  public TokenSymbolAddressIndex getInstance() {
    return this.message;
  }

  @Override
  public String toString() {
    return this.message.toString();
  }

  public byte[] getAddress() {
    return this.message.getAddress().toByteArray();
  }

  public void setAddress(byte[] address){
    message = message.toBuilder().setAddress(ByteString.copyFrom(address)).build();
  }
}
