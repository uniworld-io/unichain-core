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

package org.unichain.core.capsule.urc721;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol;

@Slf4j(topic = "capsule")
public class Urc721TokenApproveRelationCapsule implements ProtoCapsule<Protocol.Urc721TokenApproveRelation> {

  private static Descriptors.FieldDescriptor URC721_TOKEN_APPROVE_RELATION_FIELD_PREV = Protocol.Urc721TokenApproveRelation.getDescriptor().findFieldByNumber(Protocol.Urc721TokenApproveRelation.PREV_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC721_TOKEN_APPROVE_RELATION_FIELD_NEXT = Protocol.Urc721TokenApproveRelation.getDescriptor().findFieldByNumber(Protocol.Urc721TokenApproveRelation.NEXT_FIELD_NUMBER);

  private Protocol.Urc721TokenApproveRelation token;
  private byte[] key;

  public Urc721TokenApproveRelationCapsule(byte[] data) {
    try {
      this.token = Protocol.Urc721TokenApproveRelation.parseFrom(data);
      this.key = this.token.getTokenId().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc721TokenApproveRelationCapsule(Protocol.Urc721TokenApproveRelation token) {
    this.token = token;
    this.key = this.token.getTokenId().toByteArray();
  }

  public byte[] getData() {
    return this.token.toByteArray();
  }

  @Override
  public Protocol.Urc721TokenApproveRelation getInstance() {
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
    return token.hasField(URC721_TOKEN_APPROVE_RELATION_FIELD_PREV);
  }

  public boolean hasNext(){
    return token.hasField(URC721_TOKEN_APPROVE_RELATION_FIELD_NEXT);
  }

}
