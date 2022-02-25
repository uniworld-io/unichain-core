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
import org.unichain.protos.Protocol.*;

import static org.unichain.core.services.http.utils.Util.NFT_ACC_TOKEN_RELATION_FIELD_TAIL;

@Slf4j(topic = "capsule")
public class NftAccountTokenRelationCapsule implements ProtoCapsule<NftAccountTokenRelation> {
  private NftAccountTokenRelation token;
  private byte[] key;

  public NftAccountTokenRelationCapsule(byte[] key, byte[] data) {
    try {
      this.token = NftAccountTokenRelation.parseFrom(data);
      this.key = key;
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public NftAccountTokenRelationCapsule(byte[] key, NftAccountTokenRelation token) {
    this.token = token;
    this.key = key;
  }

  public byte[] getData() {
    return this.token.toByteArray();
  }

  @Override
  public NftAccountTokenRelation getInstance() {
    return this.token;
  }

  @Override
  public String toString() {
    return this.token.toString();
  }

  public byte[] getKey(){
    return key;
  }

  public boolean hasTail(){
    return token.hasField(NFT_ACC_TOKEN_RELATION_FIELD_TAIL);
  }

  public void setTotal(long total){
    token = token.toBuilder().setTotal(total).build();
  }

  public long getTotal(){
    return token.getTotal();
  }


  public void setTail(ByteString tail){
    token = token.toBuilder().setTail(tail).build();
  }

  public ByteString getTail(){
    return token.getTail();
  }

  public void setNext(ByteString next){
    token = token.toBuilder().setNext(next).build();
  }
}
