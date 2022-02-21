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
import org.unichain.protos.Contract.CreateNftTemplateContract;
import org.unichain.protos.Protocol.NftTemplate;

@Slf4j(topic = "capsule")
public class NftTemplateCapsule implements ProtoCapsule<NftTemplate> {
  private NftTemplate template;

  /**
   * get asset issue contract from bytes data.
   */
  public NftTemplateCapsule(byte[] data) {
    try {
      this.template = NftTemplate.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public NftTemplateCapsule(NftTemplate template) {
    this.template = template;
  }

  public NftTemplateCapsule(CreateNftTemplateContract contract, long lastOperation, long tokenIndex) {
    this.template = NftTemplate.newBuilder()
            .setSymbol(contract.getSymbol())
            .setName(contract.getName())
            .setTotalSupply(contract.getTotalSupply())
            .setTokenIndex(tokenIndex)
            .setMinter(contract.getMinter()) //@todo check minter exist before set or else clear that field for safe
            .setLastOperation(lastOperation)
            .setOwner(contract.getOwner()).build();
  }

  public NftTemplateCapsule(CreateNftTemplateContract contract, long lastOperation) {
    new NftTemplateCapsule(contract, lastOperation, 0);
  }

  public byte[] getData() {
    return this.template.toByteArray();
  }

  @Override
  public NftTemplate getInstance() {
    return this.template;
  }

  @Override
  public String toString() {
    return this.template.toString();
  }

  public String getSymbol() {
    return this.template.getSymbol();
  }

  public void setSymbol(String symbol) {
    this.template.toBuilder().setSymbol(symbol);
  }

  public String getName() {
    return this.template.getName();
  }

  public void setName(String name) {
    this.template.toBuilder().setName(name);
  }

  public long getTotalSupply() {
    return this.template.getTotalSupply();
  }

  public void setTotalSupply(long totalSupply) {
    this.template.toBuilder().setTotalSupply(totalSupply);
  }

  public long getTokenIndex() {
    return this.template.getTokenIndex();
  }

  public void setTokenIndex(long tokenIndex) {
    this.template.toBuilder().setTokenIndex(tokenIndex);
  }

  public byte[] getMinter() {
    return this.template.getMinter().toByteArray();
  }

  public void setMinter(ByteString minter) {
    this.template.toBuilder().setMinter(minter);
  }

  public long getLastOperation() {
    return this.template.getLastOperation();
  }

  public void setLastOperation(long lastOperation) {
    this.template.toBuilder().setLastOperation(lastOperation);
  }

  public byte[] getOwner() {
    return this.template.getOwner().toByteArray();
  }

  public void setOwner(ByteString owner) {
    this.template.toBuilder().setOwner(owner);
  }

  public byte[] getKey(){
    return this.template.getSymbol().getBytes();
  }
}
