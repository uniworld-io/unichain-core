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

package org.unichain.core.capsule.urc20;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.unichain.core.actuator.urc20.Urc20CreateContractActuator;
import org.unichain.core.capsule.ProtoCapsule;
import org.unichain.core.config.Parameter;
import org.unichain.core.exception.ContractExeException;
import org.unichain.protos.Contract;
import org.unichain.protos.Contract.Urc20CreateContract;

import java.math.BigInteger;

@Slf4j(topic = "capsule")
public class Urc20ContractCapsule implements ProtoCapsule<Urc20CreateContract> {
  public static Descriptors.FieldDescriptor URC20_CREATE_FIELD_CRITICAL_TIME = Contract.Urc20CreateContract.getDescriptor().findFieldByNumber(Contract.Urc20CreateContract.CRITICAL_UPDATE_TIME_FIELD_NUMBER);
  public static Descriptors.FieldDescriptor URC20_CREATE_FIELD_CREATE_ACC_FEE = Contract.Urc20CreateContract.getDescriptor().findFieldByNumber(Contract.Urc20CreateContract.CREATE_ACC_FEE_FIELD_NUMBER);

  private Urc20CreateContract ctx;

  public Urc20ContractCapsule(byte[] data) {
    try {
      this.ctx = Urc20CreateContract.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public Urc20ContractCapsule(Urc20CreateContract ctx) {
    this.ctx = ctx;
  }

  public byte[] getData() {
    return this.ctx.toByteArray();
  }

  @Override
  public Urc20CreateContract getInstance() {
    return this.ctx;
  }

  @Override
  public String toString() {
    return this.ctx.toString();
  }

  public byte[] createDbKey() {
    return getAddress().toByteArray();
  }

  public long getStartTime() {
    return this.ctx.getStartTime();
  }

  public long getEndTime() {
    return this.ctx.getEndTime();
  }

  public ByteString getOwnerAddress() {
    return this.ctx.getOwnerAddress();
  }

  public long getOriginFeePool() {
    return this.ctx.getFeePoolOrigin();
  }

  public Long getLatestOperationTime() {
    return this.ctx.getLatestOperationTime();
  }

  public Long getCriticalUpdateTime() {
    return this.ctx.hasField(URC20_CREATE_FIELD_CRITICAL_TIME) ? this.ctx.getCriticalUpdateTime() : 0;
  }

  public String getUrl() {
    return this.ctx.getUrl();
  }

  public BigInteger getTotalSupply() {
    return new BigInteger(this.ctx.getTotalSupply());
  }

  public String getName(){
    return this.ctx.getName();
  }

  public String getSymbol(){
    return this.ctx.getSymbol();
  }

  public long getDecimals(){
    return this.ctx.getDecimals();
  }

  public BigInteger getMaxSupply() {
    return new BigInteger(this.ctx.getMaxSupply());
  }

  public long getFee() {
    return this.ctx.getFee();
  }

  public long getCreateAccountFee() {
    return this.ctx.hasField(URC20_CREATE_FIELD_CREATE_ACC_FEE) ? this.ctx.getCreateAccFee() : Parameter.ChainConstant.TOKEN_DEFAULT_CREATE_ACC_FEE;
  }

  public long getExtraFeeRate() {
    return this.ctx.getExtraFeeRate();
  }

  public long getFeePool() {
    return this.ctx.getFeePool();
  }

  public long getExchUnw() {
    return this.ctx.getExchUnxNum();
  }

  public long getExchToken() {
    return this.ctx.getExchNum();
  }

  public boolean getEnableExch() {
    return this.ctx.getExchEnable();
  }

  public BigInteger getBurnedToken() {
    return new BigInteger(ctx.getBurned());
  }

  public void setOwnerAddress(ByteString ownerAddress) {
    this.ctx = this.ctx.toBuilder().setOwnerAddress(ownerAddress).build();
  }

  public void setCreateAccountFee(long createAccountFee) {
    this.ctx = this.ctx.toBuilder().setCreateAccFee(createAccountFee).build();
  }

  public void setStartTime(long startTime) {
    this.ctx = this.ctx.toBuilder().setStartTime(startTime).build();
  }

  public void setEndTime(long endTime) {
    this.ctx = this.ctx.toBuilder().setEndTime(endTime).build();
  }

  public void setFee(long fee) {
    this.ctx = this.ctx.toBuilder().setFee(fee).build();
  }

  public void setExtraFeeRate(long extraFeeRate) {
    this.ctx = this.ctx.toBuilder().setExtraFeeRate(extraFeeRate).build();
  }

  public void setExchUnwNum(long exchUnwNum) {
    this.ctx = this.ctx.toBuilder().setExchUnxNum(exchUnwNum).build();
  }

  public void setExchTokenNum(long exchTokenNum) {
    this.ctx = this.ctx.toBuilder().setExchNum(exchTokenNum).build();
  }

  public void setTotalSupply(BigInteger amount) {
    this.ctx = this.ctx.toBuilder().setTotalSupply(amount.toString()).build();
  }

  public void setName(String name) {
    this.ctx = this.ctx.toBuilder().setName(name).build();
  }

  public void setSymbol(String symbol) {
    this.ctx = this.ctx.toBuilder().setSymbol(symbol).build();
  }

  public void setCreateAccFee(long createAccFee) {
    this.ctx = this.ctx.toBuilder().setCreateAccFee(createAccFee).build();
  }

  public void setEnableExch(boolean enableExch) {
    this.ctx = this.ctx.toBuilder().setExchEnable(enableExch).build();
  }

  public void burnToken(BigInteger amount) throws ContractExeException {
    Assert.isTrue(amount.compareTo(BigInteger.ZERO) > 0, "burn token amount must be positive");
    setBurnedToken(new BigInteger(ctx.getBurned()).add(amount));
    setTotalSupply(new BigInteger(ctx.getTotalSupply()).subtract(amount));
  }

  public void setOriginFeePool(long originFeePool) {
    this.ctx = this.ctx.toBuilder().setFeePoolOrigin(originFeePool).build();
  }

  public void setAddress(byte[] address) {
    this.ctx = this.ctx.toBuilder().setAddress(ByteString.copyFrom(address)).build();
  }

  public void setLatestOperationTime(long timestamp) {
    this.ctx = this.ctx.toBuilder().setLatestOperationTime(timestamp).build();
  }

  public void setCriticalUpdateTime(long timestamp) {
    this.ctx = this.ctx.toBuilder().setCriticalUpdateTime(timestamp).build();
  }

  public void setFeePool(long feePool) {
    this.ctx = this.ctx.toBuilder().setFeePool(feePool).build();
  }

  public void setUrl(String newUrl) {
    this.ctx = this.ctx.toBuilder().setUrl(newUrl).build();
  }

  public void setBurnedToken(BigInteger amount){
    this.ctx = this.ctx.toBuilder().setBurned(amount.toString()).build();
  }

  public long getLot() {
    return ctx.getLot();
  }

  public void setLot(long lot){
    ctx = ctx.toBuilder().setLot(lot).build();
  }

  public ByteString getAddress() {
      return ctx.getAddress();
  }

}
