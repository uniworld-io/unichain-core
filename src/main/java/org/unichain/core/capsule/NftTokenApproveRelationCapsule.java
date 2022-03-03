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
import org.apache.commons.lang3.ArrayUtils;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "capsule")
public class NftTokenApproveRelationCapsule implements ProtoCapsule<Protocol.NftTokenApproveRelation> {
  private Protocol.NftTokenApproveRelation token;
  private byte[] key;

  public NftTokenApproveRelationCapsule(byte[] data) {
    try {
      this.token = Protocol.NftTokenApproveRelation.parseFrom(data);
      this.key = this.token.getTokenId().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public NftTokenApproveRelationCapsule(Protocol.NftTokenApproveRelation token) {
    this.token = token;
    this.key = this.token.getTokenId().toByteArray();
  }

  public byte[] getData() {
    return this.token.toByteArray();
  }

  @Override
  public Protocol.NftTokenApproveRelation getInstance() {
    return this.token;
  }

  @Override
  public String toString() {
    return this.token.toString();
  }

  public byte[] getKey(){
    return key;
  }

  public void setOwner(ByteString owner){
    token = token.toBuilder().setOwnerAddress(owner).build();
  }

  public byte[] getOwner(){
    return token.getOwnerAddress().toByteArray();
  }

  public byte[] getNext(){
    return token.getNext().toByteArray();
  }

  public byte[] getPrev(){
    return token.getPrev().toByteArray();
  }

  public void setNext(byte[] next){
    token = token.toBuilder().setNext(ByteString.copyFrom(next)).build();
  }

  public void setPrev(byte[] prev){
    token = token.toBuilder().setPrev(ByteString.copyFrom(prev)).build();
  }

  public void clearNext(){
    this.token = token.toBuilder().clearNext().build();
  }

  public void clearPrev(){
    this.token = token.toBuilder().clearPrev().build();
  }

  public boolean hasPrev(){
    return token.hasField(NFT_TOKEN_APPROVE_RELATION_FIELD_PREV);
  }

  public boolean hasNext(){
    return token.hasField(NFT_TOKEN_APPROVE_RELATION_FIELD_NEXT);
  }

}
