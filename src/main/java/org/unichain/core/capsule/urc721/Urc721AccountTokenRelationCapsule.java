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
import org.unichain.core.Wallet;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.Urc721AccountTokenRelation;

import java.util.Map;
import java.util.Optional;

@Slf4j(topic = "capsule")
public class Urc721AccountTokenRelationCapsule implements ProtoCapsule<Urc721AccountTokenRelation> {

  private static Descriptors.FieldDescriptor URC721_ACC_TOKEN_RELATION_FIELD_TAIL = Protocol.Urc721AccountTokenRelation.getDescriptor().findFieldByNumber(Protocol.Urc721AccountTokenRelation.TAIL_FIELD_NUMBER);
  private static Descriptors.FieldDescriptor URC721_ACC_TOKEN_RELATION_FIELD_TAIL_APPROVE = Protocol.Urc721AccountTokenRelation.getDescriptor().findFieldByNumber(Protocol.Urc721AccountTokenRelation.APPROVE_TAIL_FIELD_NUMBER);


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

  public long getTotal(String contractBase58){
    var totals = relation.getTotalsMap();
    return totals.getOrDefault(contractBase58, 0L);
  }

  public void decreaseTotal(String contractBase58, long amt){
    if(!relation.getTotalsMap().containsKey(contractBase58))
      return;

    var total =  relation.getTotalsMap().get(contractBase58);
    total = Math.subtractExact(total, amt);
    if(total <= 0)
    {
      relation = relation.toBuilder().removeTotals(contractBase58).build();
    }
    else
    {
      relation = relation.toBuilder().putTotals(contractBase58, total).build();
    }
  }

  public void  increaseTotal(String contractBase58, long amt){
    var totalMap = relation.getTotalsMap();
    var total = totalMap == null ? 0L : totalMap.containsKey(contractBase58) ? totalMap.get(contractBase58) : 0L;
    total = Math.addExact(total, amt);
    relation = relation.toBuilder().putTotals(contractBase58, total).build();
  }

  public void clearTotal(String contractBase58){
    if(!relation.getTotalsMap().containsKey(contractBase58))
      return;
    relation = relation.toBuilder().removeTotals(contractBase58).build();
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

  public void increaseTotalApprove(long amt){
    relation = relation.toBuilder().setApproveTotal(Math.addExact(relation.getApproveTotal(), amt)).build();
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
    return relation.hasField(URC721_ACC_TOKEN_RELATION_FIELD_TAIL);
  }

  public boolean hasTailApprove(){
    return relation.hasField(URC721_ACC_TOKEN_RELATION_FIELD_TAIL_APPROVE);
  }

  public void setTail(ByteString tail){
    relation = relation.toBuilder().setTail(tail).build();
  }

  public boolean hasApprovalForAll(String operatorBase58, String contractBase58){
    if(!relation.containsApprovedForAlls(contractBase58))
      return false;
    return operatorBase58.equals(relation.getApprovedForAllsMap().get(contractBase58));
  }

  public  boolean isApprovedForAll(byte[] contractAddr, byte[] operatorAddr){
    var contractBase58 = Wallet.encode58Check(contractAddr);
    var operatorBase58 = Wallet.encode58Check(operatorAddr);

    if(!relation.containsApprovedForAlls(contractBase58))
      return false;
    else
      return (operatorBase58.equals(relation.getApprovedForAllsMap().get(contractBase58)));
  }

  public Optional<byte[]> getApprovedForAll(byte[] contractAddr){
    var contractBase58 = Wallet.encode58Check(contractAddr);

    if(!relation.containsApprovedForAlls(contractBase58))
      return Optional.empty();
    else
    {
      var operatorBase58 = relation.getApprovedForAllsMap().get(contractBase58);
      return (operatorBase58 == null) ? Optional.empty() : Optional.ofNullable(Wallet.decodeFromBase58Check(operatorBase58));
    }
  }

  public void clearApprovedForAll(byte[] contract, byte[] _operator){
    relation = relation.toBuilder()
            .removeApprovedForAlls(Wallet.encode58Check(contract))
            .build();
  }

  public void setApprovedForAll(byte[] contractAddr, byte[] toAddr){
    relation = relation.toBuilder()
            .putApprovedForAlls(Wallet.encode58Check(contractAddr), Wallet.encode58Check(toAddr))
            .build();
  }

  public void removeApproveAll(byte[] owner, byte[] contract){
    var ownerBase58 = Wallet.encode58Check(owner);
    var contractBase58 = Wallet.encode58Check(contract);

    if(!relation.containsApproveAlls(ownerBase58))
      return;

    var contracts = relation.getApproveAllsMap()
            .get(ownerBase58)
            .toBuilder()
            .removeContracts(contractBase58)
            .build();

    relation = relation.toBuilder()
            .putApproveAlls(ownerBase58, contracts)
            .build();
  }

  public void addApproveAll(byte[] ownerAddr, byte[] contractAddr){
    var ownerBase58 = Wallet.encode58Check(ownerAddr);
    var contractBase58 = Wallet.encode58Check(contractAddr);

    var contracts = relation.getApproveAllsOrDefault(ownerBase58, Protocol.Urc721ApproveAllMap.newBuilder().build())
            .toBuilder()
            .putContracts(contractBase58, true)
            .build();

    relation = relation.toBuilder()
            .putApproveAlls(ownerBase58, contracts)
            .build();
  }

  public boolean hasApproveAll(byte[] owner, byte[] contract){
    var ownerBase58 = Wallet.encode58Check(owner);
    return !relation.containsApproveAlls(ownerBase58) ? false :
            (relation.getApproveAllsMap()
                    .get(ownerBase58)
                    .getContractsMap()
                    .get(Wallet.encode58Check(contract)) == true);
  }

  public Map<String, Protocol.Urc721ApproveAllMap> getApproveAllMap(){
    return relation.getApproveAllsMap();
  }

  public byte[] getHeadApprove(){
    return relation.getApproveHead().toByteArray();
  }
}
