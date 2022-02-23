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
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.NftAccountTemplateRelation;

@Slf4j(topic = "capsule")
public class NftAccountTemplateRelationCapsule implements ProtoCapsule<NftAccountTemplateRelation> {
  private NftAccountTemplateRelation token;
  private byte[] accountAddress;

  public NftAccountTemplateRelationCapsule(byte[] data) {
    try {
      this.token = NftAccountTemplateRelation.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public NftAccountTemplateRelationCapsule(byte[] accountAddress, NftAccountTemplateRelation token) {
    this.token = Protocol.NftAccountTemplateRelation.newBuilder()
            .setPrev(token.getPrev())
            .setNext(token.getNext())
            .setTemplateId(token.getTemplateId())
            .setIsMinter(token.getIsMinter())
            .setTotal(token.getTotal())
            .build();
  }

  public NftAccountTemplateRelationCapsule(byte[] accountAddress, ByteString templateId, long total) {
    this.accountAddress = accountAddress;
    this.token = Protocol.NftAccountTemplateRelation.newBuilder()
            .setTemplateId(templateId)
            .setTotal(total)
            .build();
  }

  public NftAccountTemplateRelationCapsule(byte[] accountAddress, ByteString prev, ByteString templateId) {
    this.accountAddress = accountAddress;
    this.token = Protocol.NftAccountTemplateRelation.newBuilder()
            .setPrev(prev)
            .setTemplateId(templateId)
            .build();
  }

  public byte[] getData() {
    return this.token.toByteArray();
  }

  @Override
  public NftAccountTemplateRelation getInstance() {
    return this.token;
  }

  @Override
  public String toString() {
    return this.token.toString();
  }

  public void setPrev(ByteString prev) {
    this.token = this.token.toBuilder().setPrev(prev).build();
  }

  public void setNext(ByteString next) {
    this.token = this.token.toBuilder().setNext(next).build();
  }

  public void setTemplateId(ByteString templateId) {
    this.token = this.token.toBuilder().setTemplateId(templateId).build();
  }

  public void setIsMinter(boolean isMinter) {
    this.token = this.token.toBuilder().setIsMinter(isMinter).build();
  }

  public void setTotal(long total) {
    this.token = this.token.toBuilder().setTotal(total).build();
  }

  public ByteString getPrev() {
    return this.token.getPrev();
  }

  public ByteString getNext() {
    return this.token.getNext();
  }

  public ByteString getTemplateId() {
    return this.token.getTemplateId();
  }

  public boolean getIsMinter() {
    return this.token.getIsMinter();
  }

  public long getTotal() {
    return this.token.getTotal();
  }

  public void setAccountAddress(byte[] accountAddress) {
    this.accountAddress = accountAddress;
  }

  public byte[] getAccountAddress() {
    return this.accountAddress;
  }
}
