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
import org.unichain.protos.Protocol.NftAccountTemplateRelation;

//@todo nft review
@Slf4j(topic = "capsule")
public class NftAccountTemplateRelationCapsule implements ProtoCapsule<NftAccountTemplateRelation> {
  private NftAccountTemplateRelation token;

  public NftAccountTemplateRelationCapsule(byte[] data) {
    try {
      this.token = NftAccountTemplateRelation.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public NftAccountTemplateRelationCapsule(NftAccountTemplateRelation token) {
    this.token = token;
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

  public byte[] getKey(){
    //@todo later
    return null;
  }
}
