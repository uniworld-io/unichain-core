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
import org.unichain.common.utils.ByteArray;
import org.unichain.core.db.Manager;
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

  public ByteString getName() {
    return this.createTokenContract.getName();
  }

  public ByteString getDescription() {
    return this.createTokenContract.getDescription();
  }

  public String getId() {
    return this.createTokenContract.getId();
  }

  public int getPrecision() {
    return this.createTokenContract.getPrecision();
  }

  public byte[] createDbV2Key() {
    return ByteArray.fromString(this.createTokenContract.getId());
  }

  public byte[] createDbKey() {
    return getName().toByteArray();
  }

  public byte[] createDbKeyFinal(Manager manager) {
    if (manager.getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      return createDbKey();
    } else {
      return createDbV2Key();
    }
  }

  public static String createDbKeyString(String name, long order) {
    return name + "_" + order;
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

  public Long getLatestOperationTime() {
    return this.createTokenContract.getLatestOperationTime();
  }

  public ByteString getUrl() {
    return this.createTokenContract.getUrl();
  }

  public long getTotalSupply() {
    return this.createTokenContract.getTotalSupply();
  }

  public long getMaxSupply() {
    return this.createTokenContract.getMaxSupply();
  }

  public long getFee() {
    return this.createTokenContract.getFee();
  }

  public long getFeePool() {
    return this.createTokenContract.getFeePool();
  }

  public ByteString getAbbr() {
    return this.createTokenContract.getAbbr();
  }

  public void setFee(long fee) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setFee(fee).build();
  }

  public void setTotalSupply(long amount) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setTotalSupply(amount).build();
  }

  public void burnToken(long amount) throws ContractExeException {
    if(amount <= 0)
      throw  new ContractExeException("mined token amount must greater than ZERO");
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setBurned(createTokenContract.getBurned() + amount).build();
  }

  public void setLatestOperationTime(long latest_time) {
    this.createTokenContract = this.createTokenContract.toBuilder().setLatestOperationTime(latest_time).build();
  }

  public void setFeePool(long feePool) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setFeePool(feePool).build();
  }


  public void setId(String id) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setId(id)
            .build();
  }

  public void setPrecision(int precision) {
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setPrecision(precision)
            .build();
  }

  public void setUrl(ByteString newUrl) {
    this.createTokenContract = this.createTokenContract.toBuilder()
        .setUrl(newUrl).build();
  }

  public void setDescription(ByteString description) {
    this.createTokenContract = this.createTokenContract.toBuilder()
        .setDescription(description).build();
  }

  public void setBurnedToken(long amount){
    this.createTokenContract = this.createTokenContract.toBuilder()
            .setBurned(amount).build();
  }

  public long getBurnedToken() {
    return createTokenContract.getBurned();
  }

  public void mineToken(long amount) throws ContractExeException{
    if(amount <= 0)
      throw  new ContractExeException("mined token amount must greater than ZERO");
    if(getMaxSupply() - getBurnedToken() - getTotalSupply() < amount)
      throw  new ContractExeException("mined token amount exceed amount available");
    setTotalSupply(getTotalSupply() + amount);
  }
}
