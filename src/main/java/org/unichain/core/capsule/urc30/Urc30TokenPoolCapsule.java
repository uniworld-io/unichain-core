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

package org.unichain.core.capsule.urc30;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.exception.ContractExeException;
import org.unichain.protos.Contract.CreateTokenContract;

import static org.unichain.core.services.http.utils.Util.TOKEN_CREATE_FIELD_CREATE_ACC_FEE;
import static org.unichain.core.services.http.utils.Util.TOKEN_CREATE_FIELD_CRITICAL_TIME;

@Slf4j(topic = "capsule")
public class Urc30TokenPoolCapsule implements ProtoCapsule<CreateTokenContract> {
  private CreateTokenContract createTokenContract;

  /**
   * get asset issue contract from bytes data.
   */
  public Urc30TokenPoolCapsule(byte[] data) {
    try {
      this.createTokenContract = CreateTokenContract.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc30TokenPoolCapsule(CreateTokenContract createTokenContract) {
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

  /**
   * In the case with old token model (block version < 3) set to zero!
   */
  public Long getCriticalUpdateTime() {
    return this.createTokenContract.hasField(TOKEN_CREATE_FIELD_CRITICAL_TIME) ?
            this.createTokenContract.getCriticalUpdateTime() : 0;
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

  public long getCreateAccountFee() {
    //when old model don't have this field yet, return to default value. should be update later
    return this.createTokenContract.hasField(TOKEN_CREATE_FIELD_CREATE_ACC_FEE) ? this.createTokenContract.getCreateAccFee() : Parameter.ChainConstant.TOKEN_DEFAULT_CREATE_ACC_FEE;
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

  public void setCreateAccountFee(long createAccountFee) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setCreateAccFee(createAccountFee).build();
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

  public void setCreateAccFee(long createAccFee) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setCreateAccFee(createAccFee).build();
  }

  public void burnToken(long amount) throws ContractExeException {
    Assert.isTrue(amount > 0, "mint token amount must be positive");
    setBurnedToken(Math.addExact(createTokenContract.getBurned(), amount));
    setTotalSupply(Math.subtractExact(createTokenContract.getTotalSupply(), amount));
  }

  public void setOriginFeePool(long originFeePool) {
    this.createTokenContract = this.createTokenContract.toBuilder().setFeePoolOrigin(originFeePool).build();
  }

  public void setAddress(byte[] address) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setAddress(ByteString.copyFrom(address))
            .build();
  }

  public void setLatestOperationTime(long timestamp) {
    this.createTokenContract = this.createTokenContract.toBuilder().setLatestOperationTime(timestamp).build();
  }

  public void setCriticalUpdateTime(long timestamp) {
    this.createTokenContract = this.createTokenContract.toBuilder().setCriticalUpdateTime(timestamp).build();
  }

  public void setFeePool(long feePool) {
    this.createTokenContract = this.createTokenContract.toBuilder().setFeePool(feePool).build();
  }

  public void setUrl(String newUrl) {
    this.createTokenContract = this.createTokenContract.toBuilder().setUrl(newUrl).build();
  }

  public void setDescription(String description) {
    this.createTokenContract = this.createTokenContract.toBuilder().setDescription(description).build();
  }

  public void setBurnedToken(long amount){
    this.createTokenContract = this.createTokenContract.toBuilder().setBurned(amount).build();
  }

  public void mineToken(long amount) throws ContractExeException{
    Assert.isTrue(amount > 0 && (getMaxSupply() - getBurnedToken() - getTotalSupply() >= amount), "mint token amount must be positive and do not exceed mint capacity");
    setTotalSupply(getTotalSupply() + amount);
  }

  public long getLot() {
    return createTokenContract.getLot();
  }

  public void setLot(long lot){
    createTokenContract = createTokenContract.toBuilder().setLot(lot).build();
  }

  public ByteString getAddress() {
      return createTokenContract.getAddress();
  }
}
