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
import lombok.extern.slf4j.Slf4j;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol.NftAccountTemplateRelation;

import static org.unichain.core.services.http.utils.Util.NFT_TEMPLATE_ACCOUNT_FIELD_TAIL;

@Slf4j(topic = "capsule")
public class Urc721AccountTemplateRelationCapsule implements ProtoCapsule<NftAccountTemplateRelation> {
  private NftAccountTemplateRelation relation;
  private byte[] key;

  public Urc721AccountTemplateRelationCapsule(byte[] key, NftAccountTemplateRelation relation) {
    this.key = key;
    this.relation = relation;
  }

  @Override
  public byte[] getData() {
    return this.relation.toByteArray();
  }

  @Override
  public NftAccountTemplateRelation getInstance() {
    return this.relation;
  }

  public byte[] getKey(){
    return key;
  }

  public void setTotal(long total){
    this.relation = relation.toBuilder().setTotal(total).build();
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
    return relation.hasField(NFT_TEMPLATE_ACCOUNT_FIELD_TAIL);
  }

  public ByteString getTail(){
    return relation.getTail();
  }

  public ByteString getHead(){
    return relation.getHead();
  }
}
