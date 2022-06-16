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
import org.unichain.protos.Protocol.Urc721AccountContractRelation;

@Slf4j(topic = "capsule")
public class Urc721AccountContractRelationCapsule implements ProtoCapsule<Urc721AccountContractRelation> {
  private static Descriptors.FieldDescriptor URC721_CONTRACT_ACCOUNT_FIELD_TAIL = Protocol.Urc721AccountContractRelation.getDescriptor().findFieldByNumber(Protocol.Urc721AccountContractRelation.TAIL_FIELD_NUMBER);

  private Urc721AccountContractRelation relation;
  private byte[] key;

  public Urc721AccountContractRelationCapsule(byte[] data) {
    try {
      this.relation = Protocol.Urc721AccountContractRelation.parseFrom(data);
      this.key = relation.getOwnerAddress().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc721AccountContractRelationCapsule(byte[] key, Urc721AccountContractRelation relation) {
    this.key = key;
    this.relation = relation;
  }

  @Override
  public byte[] getData() {
    return this.relation.toByteArray();
  }

  @Override
  public Urc721AccountContractRelation getInstance() {
    return this.relation;
  }

  public byte[] getKey(){
    return key;
  }

  public void setTotal(long total){
    this.relation = relation.toBuilder().setTotal(total).build();
  }

  public void increaseTotal(long amt){
    this.relation = relation.toBuilder().setTotal(Math.addExact(relation.getTotal(), amt)).build();
  }

  public long getTotal(){
    return relation.getTotal();
  }

  public void setTail(ByteString tail){
    this.relation = relation.toBuilder().setTail(tail).build();
  }

  public void setHead(ByteString head){
    this.relation = relation.toBuilder().setHead(head).build();
  }

  public boolean hasTail(){
    return relation.hasField(URC721_CONTRACT_ACCOUNT_FIELD_TAIL);
  }

  public ByteString getTail(){
    return relation.getTail();
  }

  public ByteString getHead(){
    return relation.getHead();
  }
}
