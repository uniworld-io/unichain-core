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

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "capsule")
public class NftAccountTokenRelationCapsule implements ProtoCapsule<NftAccountTokenRelation> {
  private NftAccountTokenRelation relation;
  private byte[] key;

  public NftAccountTokenRelationCapsule(byte[] key, byte[] data) {
    try {
      this.relation = NftAccountTokenRelation.parseFrom(data);
      this.key = key;
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public NftAccountTokenRelationCapsule(byte[] key, NftAccountTokenRelation relation) {
    this.relation = relation;
    this.key = key;
  }

  public byte[] getData() {
    return this.relation.toByteArray();
  }

  @Override
  public NftAccountTokenRelation getInstance() {
    return this.relation;
  }

  @Override
  public String toString() {
    return this.relation.toString();
  }

  public byte[] getKey(){
    return key;
  }

  public void setTotal(long total){
    relation = relation.toBuilder().setTotal(total).build();
  }

  public long getTotal(){
    return relation.getTotal();
  }

  public boolean hasTail(){
    return relation.hasField(NFT_ACC_TOKEN_RELATION_FIELD_TAIL);
  }

  public void setTail(ByteString tail){
    relation = relation.toBuilder().setTail(tail).build();
  }

  public ByteString getTail(){
    return relation.getTail();
  }

  public void setNext(ByteString next){
    relation = relation.toBuilder().setNext(next).build();
  }

  public ByteString getNext(){
    return relation.getNext();
  }

  public boolean hasNext(){
    return relation.hasField(NFT_ACC_TOKEN_RELATION_FIELD_NEXT);
  }

  public void setPrev(ByteString prev){
    relation = relation.toBuilder().setPrev(prev).build();
  }

  public ByteString getPrev(){
    return relation.getPrev();
  }

  public boolean isApproval(){
    return relation.getIsApproval();
  }

  public void setApproval(boolean approval){
    relation = relation.toBuilder().setIsApproval(approval).build();
  }

  public void setApprovalForAll(ByteString approvalForAll){
    relation = relation.toBuilder().setApprovalAll(approvalForAll).build();
  }

  public ByteString getTokenId(){
    return relation.getTokenId();
  }

  public void setTokenId(ByteString tokenId){
    relation = relation.toBuilder().setTokenId(tokenId).build();
  }

  public ByteString getApprovalForAll(){
    return relation.getApprovalAll();
  }

  public boolean hasApprovalForAll(){
    return relation.hasField(NFT_ACC_TOKEN_RELATION_FIELD_APPROVAL_FOR_ALL);
  }
}
