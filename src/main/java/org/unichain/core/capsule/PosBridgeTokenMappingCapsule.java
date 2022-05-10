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
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.PosBridgeTokenMapping;

@Slf4j(topic = "capsule")
public class PosBridgeTokenMappingCapsule implements ProtoCapsule<Protocol.PosBridgeTokenMapping> {
  public static final int ASSET_TYPE_NATIVE = 1;
  public static final int ASSET_TYPE_TOKEN = 2;
  public static final int ASSET_TYPE_NFT = 3;

  private PosBridgeTokenMapping tokenMap;

  public PosBridgeTokenMappingCapsule(byte[] data) {
    try {
      this.tokenMap = PosBridgeTokenMapping.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public PosBridgeTokenMappingCapsule(Protocol.PosBridgeTokenMapping tokenMap) {
    this.tokenMap = tokenMap;
  }

  public byte[] getData() {
    return this.tokenMap.toByteArray();
  }

  @Override
  public Protocol.PosBridgeTokenMapping getInstance() {
    return this.tokenMap;
  }

  @Override
  public String toString() {
    return this.tokenMap.toString();
  }

  public void putToken(long chainId, String token, long type){
    this.tokenMap = this.tokenMap.toBuilder()
            .putTokens(Long.toHexString(chainId), token)
            .setType(type)
            .build();
  }

  public boolean clearToken(long chainId){
    this.tokenMap = this.tokenMap.toBuilder()
            .removeTokens(Long.toHexString(chainId))
            .build();
    return this.tokenMap.getTokensMap().size() <= 0;
  }

  public boolean hasChainId(long chainId){
    return (this.tokenMap.containsTokens(Long.toHexString(chainId)));
  }

  public String getTokenByChainId(long chainId){
    return this.tokenMap.getTokensMap().get(Long.toHexString(chainId));
  }

  public long getAssetType(){
    return this.tokenMap.getType();
  }
}
