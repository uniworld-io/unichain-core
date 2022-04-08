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
import org.unichain.common.crypto.Hash;
import org.unichain.common.utils.ByteUtil;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Contract.CreateNftTemplateContract;
import org.unichain.protos.Protocol.NftTemplate;

import static org.unichain.core.services.http.utils.Util.*;

@Slf4j(topic = "capsule")
public class NftTemplateCapsule implements ProtoCapsule<NftTemplate> {
  private NftTemplate template;

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

  public NftTemplateCapsule(CreateNftTemplateContract ctx, long lastOperation, long tokenIndex) {
    var builder = NftTemplate.newBuilder()
            .setContract(ctx.getContract().toUpperCase())
            .setName(ctx.getName())
            .setOwner(ctx.getOwnerAddress())
            .setTotalSupply(ctx.getTotalSupply())
            .setTokenIndex(tokenIndex)
            .setLastOperation(lastOperation)
            .setContractAddress(generateNftContractAddress(ctx));

    if (ctx.hasField(NFT_CREATE_TEMPLATE_FIELD_MINTER))
      builder.setMinter(ctx.getMinter());
    else
      builder.clearMinter();

    this.template = builder.build();
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

  public String getContract() {
    return this.template.getContract();
  }

  public void setContract(String contract) {
    this.template = this.template.toBuilder().setContract(contract).build();
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
    return this.template.hasField(NFT_TEMPLATE_FIELD_MINTER);
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
    return this.template.getContract().getBytes();
  }

  public void clearMinter(){
    this.template = template.toBuilder().clearMinter().build();
  }

  public byte[] getNext(){
    return template.getNext().toByteArray();
  }

  public byte[] getPrev(){
    return template.getPrev().toByteArray();
  }

  public void setNext(byte[] next){
     template = template.toBuilder().setNext(ByteString.copyFrom(next)).build();
  }

  public void setPrev(byte[] prev){
    template = template.toBuilder().setPrev(ByteString.copyFrom(prev)).build();
  }

  public void clearNext(){
    this.template = template.toBuilder().clearNext().build();
  }

  public boolean hasNext(){
    return this.template.hasField(NFT_TEMPLATE_FIELD_NEXT);
  }

  public void clearPrev(){
    this.template = template.toBuilder().clearPrev().build();
  }

  public void clearPrevOfMinter(){
    this.template = template.toBuilder().clearPrevOfMinter().build();
  }

  public void clearNextOfMinter(){
    this.template = template.toBuilder().clearNextOfMinter().build();
  }

  public void setNextOfMinter(byte[] next){
    template = template.toBuilder().setNextOfMinter(ByteString.copyFrom(next)).build();
  }

  public void setPrevOfMinter(byte[] prev){
    template = template.toBuilder().setPrevOfMinter(ByteString.copyFrom(prev)).build();
  }

  public boolean hasNextOfMinter(){
    return this.template.hasField(NFT_TEMPLATE_FIELD_NEXT_OF_MINTER);
  }

  public boolean hasPrevOfMinter(){
    return this.template.hasField(NFT_TEMPLATE_FIELD_PREV_OF_MINTER);
  }

  public byte[] getNextOfMinter(){
    return template.getNextOfMinter().toByteArray();
  }

  public byte[] getPrevOfMinter(){
    return template.getPrevOfMinter().toByteArray();
  }

  private ByteString generateNftContractAddress(CreateNftTemplateContract ctx) {
    byte[] addressByte = ctx.getOwnerAddress().toByteArray();
    byte[] contractByte = Util.stringAsBytesUppercase(ctx.getContract());
    byte[] mergedData = ByteUtil.merge(addressByte, Hash.sha3(contractByte));
    return ByteString.copyFrom(Hash.sha3omit12(mergedData));
  }
}
