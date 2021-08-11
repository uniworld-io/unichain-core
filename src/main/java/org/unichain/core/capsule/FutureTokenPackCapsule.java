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
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;
import org.unichain.protos.Protocol.FutureTokenPack;

import java.util.ArrayList;
import java.util.List;

@Slf4j(topic = "capsule")
public class FutureTokenPackCapsule implements ProtoCapsule<FutureTokenPack> {
  private FutureTokenPack index;

  /**
   * get asset issue contract from bytes data.
   */
  public FutureTokenPackCapsule(byte[] data) {
    try {
      this.index = FutureTokenPack.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public FutureTokenPackCapsule(FutureTokenPack index) {
    this.index = index;
  }

  public byte[] getData() {
    return this.index.toByteArray();
  }

  @Override
  public FutureTokenPack getInstance() {
    return this.index;
  }

  @Override
  public String toString() {
    return index.toString();
  }

  public byte[] createDbKey() {
    return Util.makeFutureTokenIndexKey(index.getOwnerAddress().toByteArray(), index.getTokenName().getBytes());
  }

  public void setTotalDeal(long size){
    this.index = this.index.toBuilder().setTotalDeal(size).build();
  }

  public long getTotalDeal(){
    return this.index.getTotalDeal();
  }


  public void setTokenName(String tokenName){
    this.index = this.index.toBuilder().setTokenName(tokenName).build();
  }

  public String getTokenName(){
    return this.index.getTokenName();
  }

  public void setOwnerAddress(ByteString ownerAddress){
    this.index = this.index.toBuilder().setOwnerAddress(ownerAddress).build();
  }

  public ByteString getOwnerAddress(){
    return this.index.getOwnerAddress();
  }

  public long getLowerBoundTime(){
    return this.index.getLowerBoundTime();
  }

  public long getUpperBoundTime(){
    return this.index.getUpperBoundTime();
  }

  public long getTotalValue(){
    return this.index.getTotalValue();
  }

  public void zip(){
    //@todo later: zip deal with near time slot prevent big deal list
  }

  public void inspireInfo(){
    var lowerBound = -1L;
    var upperBound = -1L;
    var total_value = 0L;
    for(var deal : index.getDealsList()){
      lowerBound = lowerBound == -1L ? deal.getExpireTime() : Math.min(lowerBound, deal.getExpireTime());
      upperBound = upperBound == -1L ? deal.getExpireTime() : Math.max(upperBound, deal.getExpireTime());
      total_value += deal.getFutureBalance();
    }
    this.index = this.index.toBuilder()
            .setLowerBoundTime(lowerBound)
            .setUpperBoundTime(upperBound)
            .setTotalValue(total_value)
            .setTotalDeal(index.getDealsCount())
            .build();
  }

  public void addDeal(Protocol.FutureToken deal){
    this.index = this.index.toBuilder().addDeals(deal).build();
  }

  public List<Protocol.FutureToken> getDeals(){
    return this.index.getDealsList();
  }

  public void setDeals(ArrayList<Protocol.FutureToken> keepList) {
    this.index = this.index.toBuilder()
            .clearDeals()
            .addAllDeals(keepList)
            .build();
  }

  public void doPaging(int pageSize, int pageIndex){
      this.index = this.index.toBuilder()
              .clearDeals()
              .addAllDeals(Util.doPaging(index.getDealsList(), pageSize, pageIndex))
              .build();
  }
}
