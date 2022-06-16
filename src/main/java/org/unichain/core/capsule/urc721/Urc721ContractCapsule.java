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
import lombok.var;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Contract.Urc721CreateContract;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Urc721Contract;

@Slf4j(topic = "capsule")
public class Urc721ContractCapsule implements ProtoCapsule<Urc721Contract> {

  private static Descriptors.FieldDescriptor URC721_CONTRACT_FIELD_NEXT = Protocol.Urc721Contract.getDescriptor().findFieldByNumber(Protocol.Urc721Contract.NEXT_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC721_CONTRACT_FIELD_MINTER = Protocol.Urc721Contract.getDescriptor().findFieldByNumber(Protocol.Urc721Contract.MINTER_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC721_CONTRACT_FIELD_NEXT_OF_MINTER = Protocol.Urc721Contract.getDescriptor().findFieldByNumber(Protocol.Urc721Contract.NEXT_OF_MINTER_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC721_CONTRACT_FIELD_PREV_OF_MINTER = Protocol.Urc721Contract.getDescriptor().findFieldByNumber(Protocol.Urc721Contract.PREV_OF_MINTER_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor URC721_CREATE_CONTRACT_FIELD_MINTER = Urc721CreateContract.getDescriptor().findFieldByNumber(Urc721CreateContract.MINTER_FIELD_NUMBER);

  private Urc721Contract contract;

  public Urc721ContractCapsule(byte[] data) {
    try {
      this.contract = Urc721Contract.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc721ContractCapsule(Urc721Contract contract) {
    this.contract = contract;
  }

  public Urc721ContractCapsule(Urc721CreateContract ctx, long lastOperation, long tokenIndex) {
    var builder = Urc721Contract.newBuilder()
            .setSymbol(ctx.getSymbol().toUpperCase())
            .setName(ctx.getName())
            .setOwner(ctx.getOwnerAddress())
            .setTotalSupply(ctx.getTotalSupply())
            .setTokenIndex(tokenIndex)
            .setLastOperation(lastOperation)
            .setAddress(ctx.getAddress());

    if (ctx.hasField(URC721_CREATE_CONTRACT_FIELD_MINTER))
      builder.setMinter(ctx.getMinter());
    else
      builder.clearMinter();

    this.contract = builder.build();
  }

  public byte[] getData() {
    return this.contract.toByteArray();
  }

  @Override
  public Urc721Contract getInstance() {
    return this.contract;
  }

  @Override
  public String toString() {
    return this.contract.toString();
  }

  public String getSymbol() {
    return this.contract.getSymbol();
  }

  public void setSymbol(String symbol) {
    this.contract = this.contract.toBuilder().setSymbol(symbol).build();
  }

  public byte[] getAddress() {
    return this.contract.getAddress().toByteArray();
  }

  public void setAddress(byte[] address) {
    this.contract = this.contract.toBuilder().setAddress(ByteString.copyFrom(address)).build();
  }

  public String getName() {
    return this.contract.getName();
  }

  public void setName(String name) {
    this.contract = this.contract.toBuilder().setName(name).build();
  }

  public long getTotalSupply() {
    return this.contract.getTotalSupply();
  }

  public void setTotalSupply(long totalSupply) {
    this.contract = this.contract.toBuilder().setTotalSupply(totalSupply).build();
  }

  public long getTokenIndex() {
    return this.contract.getTokenIndex();
  }

  public void setTokenIndex(long tokenIndex) {
    this.contract = this.contract.toBuilder().setTokenIndex(tokenIndex).build();
  }

  public byte[] getMinter() {
    return hasMinter() ? contract.getMinter().toByteArray() : null;
  }

  public boolean hasMinter(){
    return this.contract.hasField(URC721_CONTRACT_FIELD_MINTER);
  }

  public void setMinter(ByteString minter) {
    this.contract = this.contract.toBuilder().setMinter(minter).build();
  }

  public long getLastOperation() {
    return this.contract.getLastOperation();
  }

  public void setLastOperation(long lastOperation) {
    this.contract = this.contract.toBuilder().setLastOperation(lastOperation).build();
  }

  public byte[] getOwner() {
    return this.contract.getOwner().toByteArray();
  }

  public void setOwner(ByteString owner) {
    this.contract = this.contract.toBuilder().setOwner(owner).build();
  }

  public byte[] getKey(){
    return this.contract.getAddress().toByteArray();
  }

  public void clearMinter(){
    this.contract = contract.toBuilder().clearMinter().build();
  }

  public byte[] getNext(){
    return contract.getNext().toByteArray();
  }

  public byte[] getPrev(){
    return contract.getPrev().toByteArray();
  }

  public void setNext(byte[] next){
     contract = contract.toBuilder().setNext(ByteString.copyFrom(next)).build();
  }

  public void setPrev(byte[] prev){
    contract = contract.toBuilder().setPrev(ByteString.copyFrom(prev)).build();
  }

  public void clearNext(){
    this.contract = contract.toBuilder().clearNext().build();
  }

  public boolean hasNext(){
    return this.contract.hasField(URC721_CONTRACT_FIELD_NEXT);
  }

  public void clearPrev(){
    this.contract = contract.toBuilder().clearPrev().build();
  }

  public void clearPrevOfMinter(){
    this.contract = contract.toBuilder().clearPrevOfMinter().build();
  }

  public void clearNextOfMinter(){
    this.contract = contract.toBuilder().clearNextOfMinter().build();
  }

  public void setNextOfMinter(byte[] next){
    contract = contract.toBuilder().setNextOfMinter(ByteString.copyFrom(next)).build();
  }

  public void setPrevOfMinter(byte[] prev){
    contract = contract.toBuilder().setPrevOfMinter(ByteString.copyFrom(prev)).build();
  }

  public boolean hasNextOfMinter(){
    return this.contract.hasField(URC721_CONTRACT_FIELD_NEXT_OF_MINTER);
  }

  public boolean hasPrevOfMinter(){
    return this.contract.hasField(URC721_CONTRACT_FIELD_PREV_OF_MINTER);
  }

  public byte[] getNextOfMinter(){
    return contract.getNextOfMinter().toByteArray();
  }

  public byte[] getPrevOfMinter(){
    return contract.getPrevOfMinter().toByteArray();
  }

}
