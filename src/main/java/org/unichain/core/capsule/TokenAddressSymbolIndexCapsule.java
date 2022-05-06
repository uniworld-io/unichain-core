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
import org.unichain.protos.Protocol.TokenAddressSymbolIndex;

@Slf4j(topic = "capsule")
public class TokenAddressSymbolIndexCapsule implements ProtoCapsule<TokenAddressSymbolIndex> {
  private TokenAddressSymbolIndex message;

  public TokenAddressSymbolIndexCapsule(byte[] data) {
    try {
      this.message = TokenAddressSymbolIndex.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public TokenAddressSymbolIndexCapsule(TokenAddressSymbolIndex message) {
    this.message = message;
  }

  public byte[] getData() {
    return this.message.toByteArray();
  }

  @Override
  public TokenAddressSymbolIndex getInstance() {
    return this.message;
  }

  @Override
  public String toString() {
    return this.message.toString();
  }

  public String getSymbol() {
    return this.message.getSymbol();
  }

  public void setSymbol(String symbol){
    message = message.toBuilder().setSymbol(symbol).build();
  }
}
