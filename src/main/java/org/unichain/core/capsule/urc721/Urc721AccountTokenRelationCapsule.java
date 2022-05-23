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
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol.Urc721AccountTokenRelation;

import java.util.Map;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "capsule")
public class Urc721AccountTokenRelationCapsule implements ProtoCapsule<Urc721AccountTokenRelation> {
  private Urc721AccountTokenRelation relation;
  private byte[] key;

  public Urc721AccountTokenRelationCapsule(byte[] data) {
    try {
      this.relation = Urc721AccountTokenRelation.parseFrom(data);
      this.key = this.relation.getOwnerAddress().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc721AccountTokenRelationCapsule(byte[] key, Urc721AccountTokenRelation relation) {
    this.relation = relation;
    this.key = key;
  }

  public byte[] getData() {
    return this.relation.toByteArray();
  }

  @Override
  public Urc721AccountTokenRelation getInstance() {
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

  public long getTotalApprove(){
    return relation.getApproveTotal();
  }

  public void setHead(ByteString head){
    relation = relation.toBuilder().setHead(head).build();
  }

  public void setHeadApprove(ByteString head){
    relation = relation.toBuilder().setApproveHead(head).build();
  }

  public void setTailApprove(ByteString tail){
    relation = relation.toBuilder().setApproveTail(tail).build();
  }

  public void setTotalApprove(long total){
    relation = relation.toBuilder().setApproveTotal(total).build();
  }

  public ByteString getHead(){
    return relation.getHead();
  }

  public void clearHead(){
    relation = relation.toBuilder().clearHead().build();
  }

  public ByteString getTail(){
    return relation.getTail();
  }

  public ByteString getTailApprove(){
    return relation.getApproveTail();
  }

  public void clearTail(){
    relation = relation.toBuilder().clearTail().build();
  }

  public void clearTailApprove(){
    relation = relation.toBuilder().clearApproveTail().build();
  }

  public void clearHeadApprove(){
    relation = relation.toBuilder().clearApproveHead().build();
  }

  public boolean hasTail(){
    return relation.hasField(NFT_ACC_TOKEN_RELATION_FIELD_TAIL);
  }

  public boolean hasTailApprove(){
    return relation.hasField(NFT_ACC_TOKEN_RELATION_FIELD_TAIL_APPROVE);
  }

  public void setTail(ByteString tail){
    relation = relation.toBuilder().setTail(tail).build();
  }

  public boolean hasApprovalForAll(){
    return relation.hasField(NFT_ACC_TOKEN_RELATION_FIELD_APPROVAL_FOR_ALL);
  }

  public byte[] getApprovedForAll(){
    return relation.getApprovedForAll().toByteArray();
  }

  public void clearApprovedForAll(){
    relation = relation.toBuilder().clearApprovedForAll().build();
  }

  public void setApprovedForAll(ByteString approvedForAll){
    relation = relation.toBuilder().setApprovedForAll(approvedForAll).build();
  }

  public void removeApproveAll(ByteString approvedForAll){
    relation = relation.toBuilder().removeApproveAll(ByteArray.toHexString(approvedForAll.toByteArray())).build();
  }

  public void addApproveAll(ByteString owner){
    relation = relation.toBuilder().putApproveAll(ByteArray.toHexString(owner.toByteArray()), true).build();
  }

  public boolean hasApproveAll(ByteString owner){
    return relation.containsApproveAll(ByteArray.toHexString(owner.toByteArray()));
  }

  public Map<String, Boolean> getApproveAllMap(){
    return relation.getApproveAllMap();
  }

  public byte[] getHeadApprove(){
    return relation.getApproveHead().toByteArray();
  }


}
