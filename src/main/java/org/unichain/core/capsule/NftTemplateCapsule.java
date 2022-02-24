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
import lombok.var;
import org.unichain.protos.Contract.CreateNftTemplateContract;
import org.unichain.protos.Protocol.NftTemplate;

import static org.unichain.core.services.http.utils.Util.NFT_CREATE_TEMPLATE_FIELD_MINTER;

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
    var builder = NftTemplate.newBuilder()
            .setSymbol(contract.getSymbol())
            .setName(contract.getName())
            .setTotalSupply(contract.getTotalSupply())
            .setTokenIndex(tokenIndex)
            .setLastOperation(lastOperation)
            .setOwner(contract.getOwner());
    if (contract.hasField(NFT_CREATE_TEMPLATE_FIELD_MINTER))
      builder.setMinter(contract.getMinter());
    else
      builder.clearMinter();

    this.template = builder.build();
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
    this.template = this.template.toBuilder().setSymbol(symbol).build();
  }

  public String getName() {
    return this.template.getName();
  }

  public void setName(String name) {
    this.template = this.template.toBuilder().setName(name).build();
  }

  public long getTotalSupply() {
    return this.template.getTotalSupply();
  }

  public void setTotalSupply(long totalSupply) {
    this.template = this.template.toBuilder().setTotalSupply(totalSupply).build();
  }

  public long getTokenIndex() {
    return this.template.getTokenIndex();
  }

  public void setTokenIndex(long tokenIndex) {
    this.template = this.template.toBuilder().setTokenIndex(tokenIndex).build();
  }

  public byte[] getMinter() {
    return this.template.getMinter().toByteArray();
  }

  public boolean hasMinter(){
    return this.template.hasField(NFT_CREATE_TEMPLATE_FIELD_MINTER);
  }

  public void setMinter(ByteString minter) {
    this.template = this.template.toBuilder().setMinter(minter).build();
  }

  public long getLastOperation() {
    return this.template.getLastOperation();
  }

  public void setLastOperation(long lastOperation) {
    this.template = this.template.toBuilder().setLastOperation(lastOperation).build();
  }

  public byte[] getOwner() {
    return this.template.getOwner().toByteArray();
  }

  public void setOwner(ByteString owner) {
    this.template = this.template.toBuilder().setOwner(owner).build();
  }

  public byte[] getKey(){
    return this.template.getSymbol().getBytes();
  }
}
