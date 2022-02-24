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
import org.apache.commons.lang3.ArrayUtils;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionCapsule;
import org.unichain.protos.Contract.TransferContract;
import org.unichain.protos.Protocol.Transaction;
import org.unichain.protos.Protocol.Transaction.Contract;

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

  public static boolean validTokenName(byte[] tokenName) {
    if (ArrayUtils.isEmpty(tokenName)) {
      return false;
    }
    if (tokenName.length > 32) {
      return false;
    }
    // b must read able.
    for (byte b : tokenName) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }

  public static boolean validTokenAbbrName(byte[] abbrName) {
    if (ArrayUtils.isEmpty(abbrName)) {
      return false;
    }
    if (abbrName.length > 5) {
      return false;
    }
    // b must read able.
    for (byte b : abbrName) {
      if (b < 0x21) {
        return false; // 0x21 = '!'
      }
      if (b > 0x7E) {
        return false; // 0x7E = '~'
      }
    }
    return true;
  }


  public static boolean validAssetDescription(byte[] description) {
    if (ArrayUtils.isEmpty(description)) {
      return true;   //description can empty
    }

    return description.length <= 200;
  }

  //@todo later
  public static boolean validJsonString(byte[] jsonString) {
    if (ArrayUtils.isEmpty(jsonString)) {
      return true;   //jsonString can empty
    }

    return jsonString.length <= 10*1024L;
  }

  public static boolean validUrl(byte[] url) {
    if (ArrayUtils.isEmpty(url)) {
      return false;
    }
    return url.length <= 256;
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

  /**
   * Get sender.
   */
 /* public static byte[] getSender(Transaction tx) {
    byte[] pubKey = tx.getRawData().getVin(0).getRawData().getPubKey().toByteArray();
    return ECKey.computeAddress(pubKey);
  } */

  public static boolean validNftTemplateName(byte[] name) {
    return validAccountName(name);
  }
}
