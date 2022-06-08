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

package org.unichain.core.capsule.utils;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.core.config.args.Account;
import org.unichain.core.config.args.Args;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Protocol.Transaction;
import org.unichain.protos.Protocol.Transaction.Contract;

import java.net.URL;
import java.util.Arrays;

@Slf4j(topic = "capsule")
public class TransactionUtil {

  public static Transaction newGenesisTransaction(byte[] key, long value)
      throws IllegalArgumentException {

    if (!Wallet.addressValid(key)) {
      throw new IllegalArgumentException("Invalid address");
    }
    TransferContract transferContract = TransferContract.newBuilder()
        .setAmount(value)
        .setOwnerAddress(ByteString.copyFrom("0x000000000000000000000".getBytes()))
        .setToAddress(ByteString.copyFrom(key))
        .build();

    return new TransactionCapsule(transferContract,
        Contract.ContractType.TransferContract).getInstance();
  }

  public static boolean validAccountName(byte[] accountName) {
    if (ArrayUtils.isEmpty(accountName)) {
      return true;   //accountname can empty
    }

    return accountName.length <= 200;
  }

  public static boolean validAccountId(byte[] accountId) {
    if (ArrayUtils.isEmpty(accountId)) {
      return false;
    }

    if (accountId.length < 8) {
      return false;
    }

    if (accountId.length > 32) {
      return false;
    }
    // b must read able.
    for (byte b : accountId) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }

  public static boolean validAssetName(byte[] assetName) {
    if (ArrayUtils.isEmpty(assetName)) {
      return false;
    }
    if (assetName.length > 32) {
      return false;
    }
    // b must read able.
    for (byte b : assetName) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }

  public static boolean validTokenSymbol(String symbol){
    byte[] chars = symbol.getBytes();
    if (ArrayUtils.isEmpty(chars)) {
      return false;
    }
    if (chars.length > 32) {
      return false;
    }
    // b must read able.
    for (byte b : chars) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }

  public static boolean validTokenName(String name){
    return validCharSpecial(name);
  }

  public static boolean validCharSpecial(String name) {
    byte[] chars = name.getBytes();
    if (ArrayUtils.isEmpty(chars) || chars.length > 32)
      return false;

    for (byte c : chars) {
      if (isCharSpecial(c))
        return false;
    }
    return true;
  }

  private static boolean isCharSpecial(byte c){
    switch (c){
      case 0x21:
      case 0x22:
      case 0x23:
      case 0x24:
      case 0x25:
      case 0x26:
      case 0x27:
      case 0x28:
      case 0x29:
      case 0x60:
      case 0x2b:
      case 0x2d:
      case 0x5b:
      case 0x5d:
      case 0x7b:
      case 0x7d:
      case 0x5c:
      case 0x7c:
      case 0x2e:
      case 0x2c:
      case 0x2f:
      case 0x3c:
      case 0x3f:
      case 0x3e:
      case 0x3a:
      case 0x3b:
      case 0x3d:
      case 0x5f:
      case 0x40:
      case 0x5e:
      case 0x2a:
      case 0x7e:
        return true;
      default:
        return false;
    }
  }

  public static boolean validHttpURI(String uri){
    final URL url;
    try {
      url = new URL(uri);
    } catch (Exception e1) {
      return false;
    }
    return validUrl(uri.getBytes()) && StringUtils.startsWith(url.getProtocol(), "http");
  }

  public static boolean validAssetDescription(byte[] description) {
    if (ArrayUtils.isEmpty(description)) {
      return true;
    }
    return description.length <= 200;
  }

  public static boolean validJsonString(byte[] jsonString) {
    if (ArrayUtils.isEmpty(jsonString)) {
      return true;
    }
    return jsonString.length <= 10*1024L;
  }

  public static boolean validUrl(byte[] url) {
    if (ArrayUtils.isEmpty(url)) {
      return false;
    }
    return url.length <= 1024*10;
  }

  public static boolean validUrl(String url) {
     return validUrl(url.getBytes());
  }

  public static boolean isNumber(byte[] id) {
    if (ArrayUtils.isEmpty(id)) {
      return false;
    }
    for (byte b : id) {
      if (b < '0' || b > '9') {
        return false;
      }
    }
    return !(id.length > 1 && id[0] == '0');
  }

  public static boolean isGenesisAddress(byte[] address) {
    var genericsBlock = Args.getInstance().getGenesisBlock();
    for (Account acc : genericsBlock.getAssets()){
      if(Arrays.equals(acc.getAddress(), address))
        return true;
    }
    return false;
  }
}
