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
import org.apache.commons.lang3.ArrayUtils;
import org.unichain.common.utils.ByteArray;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "capsule")
public class Urc721TokenCapsule implements ProtoCapsule<Protocol.NftToken> {
  private Protocol.NftToken token;
  private byte[] key;

  public Urc721TokenCapsule(byte[] data) {
    try {
      this.token = Protocol.NftToken.parseFrom(data);
      this.key =  genTokenKey(token.getAddress().toByteArray(), token.getId());
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc721TokenCapsule(Protocol.NftToken token) {
    this.token = token;
    this.key = genTokenKey(token.getAddress().toByteArray(), token.getId());
  }

  public byte[] getData() {
    return this.token.toByteArray();
  }

  @Override
  public Protocol.NftToken getInstance() {
    return this.token;
  }

  @Override
  public String toString() {
    return this.token.toString();
  }

  public byte[] getKey(){
    return key;
  }

  public byte[] getApproval(){
    return token.getApproval().toByteArray();
  }

  public boolean hasApproval(){
    return token.hasField(NFT_TOKEN_FIELD_APPROVAL);
  }

  public void setApproval(ByteString approval){
    token = token.toBuilder().setApproval(approval).build();
  }

  public void setOwner(ByteString owner){
    token = token.toBuilder().setOwnerAddress(owner).build();
  }

  public void setLastOperation(long lastOperation){
    token = token.toBuilder().setLastOperation(lastOperation).build();
  }

  public void clearApproval(){
    token = token.toBuilder().clearApproval().build();
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
    return token.hasField(NFT_TOKEN_FIELD_PREV);
  }

  public boolean hasNext(){
    return token.hasField(NFT_TOKEN_FIELD_NEXT);
  }

  public String getSymbol() {
    return token.getSymbol();
  }

  public byte[] getAddr() {
    return token.getAddress().toByteArray();
  }

  public static byte[] genTokenKey(byte[] contractAddr, long tokenId){
    return ArrayUtils.addAll(contractAddr, ByteArray.fromLong(tokenId));
  }

}
