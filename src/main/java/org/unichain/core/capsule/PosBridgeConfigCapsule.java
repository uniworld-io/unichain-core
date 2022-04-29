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
import org.apache.commons.lang3.ArrayUtils;
import org.unichain.core.services.http.utils.Util;
import org.unichain.protos.Protocol;

import java.util.List;

@Slf4j(topic = "capsule")
public class PosBridgeConfigCapsule implements ProtoCapsule<Protocol.PosBridgeConfig> {
  public static final byte[] DEFAULT_KEY = ArrayUtils.addAll(Util.stringAsBytesUppercase("PosBridgeConfig"));
  public static final String GENESIS_ADMIN_WALLET = "UmKK513F4s81Lmp1xW3VLbjewVUPBDVm1k";

  private Protocol.PosBridgeConfig config;
  private byte[] key;

  public PosBridgeConfigCapsule(byte[] data) {
    try {
      this.config = Protocol.PosBridgeConfig.parseFrom(data);
      this.key = ArrayUtils.addAll(Util.stringAsBytesUppercase("PosBridgeConfig"));
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage());
    }
  }

  public PosBridgeConfigCapsule(Protocol.PosBridgeConfig config) {
    this.config = config;
    this.key = ArrayUtils.addAll(Util.stringAsBytesUppercase("PosBridgeConfig"));
  }

  public PosBridgeConfigCapsule(Protocol.PosBridgeConfig config, byte[] key) {
    this.config = config;
    this.key = key;
  }

  public byte[] getData() {
    return this.config.toByteArray();
  }

  @Override
  public Protocol.PosBridgeConfig getInstance() {
    return this.config;
  }

  @Override
  public String toString() {
    return this.config.toString();
  }

  public byte[] getKey(){
    return key;
  }

  public void setNewOwner(byte[] newOwner){
    this.config = this.config.toBuilder().setOwnerAddress(ByteString.copyFrom(newOwner)).build();
  }

  public void setMinValidator(long minValidator){
    this.config = this.config.toBuilder().setMinValidator(minValidator).build();
  }

  public void setConsensusF1(long f1){
    this.config = this.config.toBuilder().setConsensusF1(f1).build();
  }

  public void setConsensusF2(long f2){
    this.config = this.config.toBuilder().setConsensusF1(f2).build();
  }

  public void clearThenPutValidators(List<String> hexValidators){
    var builder = this.config.toBuilder();
    builder.clearValidators();
    hexValidators.forEach(vs -> builder.putValidators(vs, vs));
    this.config = builder.build();
  }

  public void addValidators(List<String> validatorHexs){
    var builder = this.config.toBuilder();
    validatorHexs.forEach(vs -> builder.putValidators(vs, vs));
    this.config = builder.build();
  }

  public void putValidator(String validatorAddrHex){
    this.config = this.config.toBuilder().putValidators(validatorAddrHex, validatorAddrHex).build();
  }

  public void setInitialized(boolean flag){
    this.config = this.config.toBuilder().setInitialized(flag).build();
  }
}
