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
import org.unichain.core.exception.ContractExeException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.CreateTokenContract;

@Slf4j(topic = "capsule")
public class TokenPoolCapsule implements ProtoCapsule<Contract.CreateTokenContract> {
  private CreateTokenContract createTokenContract;

  /**
   * get asset issue contract from bytes data.
   */
  public TokenPoolCapsule(byte[] data) {
    try {
      this.createTokenContract = CreateTokenContract.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public TokenPoolCapsule(CreateTokenContract createTokenContract) {
    this.createTokenContract = createTokenContract;
  }

  public byte[] getData() {
    return this.createTokenContract.toByteArray();
  }

  @Override
  public CreateTokenContract getInstance() {
    return this.createTokenContract;
  }

  @Override
  public String toString() {
    return this.createTokenContract.toString();
  }

  public String getName() {
    return this.createTokenContract.getName();
  }

  public String getDescription() {
    return this.createTokenContract.getDescription();
  }

  public byte[] createDbKey() {
    return getName().getBytes();
  }

  public long getStartTime() {
    return this.createTokenContract.getStartTime();
  }

  public long getEndTime() {
    return this.createTokenContract.getEndTime();
  }

  public ByteString getOwnerAddress() {
    return this.createTokenContract.getOwnerAddress();
  }

  public long getOriginFeePool() {
    return this.createTokenContract.getFeePoolOrigin();
  }

  public Long getLatestOperationTime() {
    return this.createTokenContract.getLatestOperationTime();
  }

  public String getUrl() {
    return this.createTokenContract.getUrl();
  }

  public long getTotalSupply() {
    return this.createTokenContract.getTotalSupply();
  }

  public String getTokenName(){
    return this.createTokenContract.getName();
  }

  public long getMaxSupply() {
    return this.createTokenContract.getMaxSupply();
  }

  public long getFee() {
    return this.createTokenContract.getFee();
  }

  public long getExtraFeeRate() {
    return this.createTokenContract.getExtraFeeRate();
  }

  public long getFeePool() {
    return this.createTokenContract.getFeePool();
  }

  public String getAbbr() {
    return this.createTokenContract.getAbbr();
  }

  public long getExchUnw() {
    return this.createTokenContract.getExchUnxNum();
  }

  public long getExchToken() {
    return this.createTokenContract.getExchNum();
  }

  public long getBurnedToken() {
    return createTokenContract.getBurned();
  }

  public void setOwnerAddress(ByteString ownerAddress) {
    this.createTokenContract= this.createTokenContract.toBuilder()
            .setOwnerAddress(ownerAddress).build();
  }

  public void setStartTime(long startTime) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setStartTime(startTime).build();
  }

  public void setEndTime(long endTime) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setEndTime(endTime).build();
  }

  public void setFee(long fee) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setFee(fee).build();
  }

  public void setExtraFeeRate(long extraFeeRate) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setExtraFeeRate(extraFeeRate).build();
  }

  public void setExchUnwNum(long exchUnwNum) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setExchUnxNum(exchUnwNum).build();
  }

  public void setExchTokenNum(long exchTokenNum) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setExchNum(exchTokenNum).build();
  }

  public void setTotalSupply(long amount) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setTotalSupply(amount).build();
  }

  public void setTokenName(String name) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setName(name).build();
  }

  public void burnToken(long amount) throws ContractExeException {
    if(amount <= 0)
      throw  new ContractExeException("mined token amount must greater than ZERO");
    setBurnedToken(createTokenContract.getBurned() + amount);
    setTotalSupply(createTokenContract.getTotalSupply() - amount);
  }

  public void setOriginFeePool(long originFeePool) {
    this.createTokenContract = this.createTokenContract.toBuilder().setFeePoolOrigin(originFeePool).build();
  }

  public void setLatestOperationTime(long latestOpTime) {
    this.createTokenContract = this.createTokenContract.toBuilder().setLatestOperationTime(latestOpTime).build();
  }

  public void setFeePool(long feePool) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setFeePool(feePool).build();
  }

  public void setUrl(String newUrl) {
    this.createTokenContract = this.createTokenContract.toBuilder()
        .setUrl(newUrl).build();
  }

  public void setDescription(String description) {
    this.createTokenContract = this.createTokenContract.toBuilder()
        .setDescription(description).build();
  }

  public void setBurnedToken(long amount){
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setBurned(amount).build();
  }

  public void mineToken(long amount) throws ContractExeException{
    if(amount <= 0)
      throw  new ContractExeException("mined token amount must greater than ZERO");
    if(getMaxSupply() - getBurnedToken() - getTotalSupply() < amount)
      throw  new ContractExeException("mined token amount exceed amount available");
    setTotalSupply(getTotalSupply() + amount);
  }

  public long getLot() {
    return createTokenContract.getLot();
  }

  public void setLot(long lot){
    createTokenContract = createTokenContract.toBuilder()
            .setLot(lot).build();
  }
}
