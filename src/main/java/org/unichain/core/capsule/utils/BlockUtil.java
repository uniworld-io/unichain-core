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
import org.unichain.common.utils.ByteArray;
import org.unichain.common.utils.Sha256Hash;
import org.unichain.core.capsule.BlockCapsule;
import org.unichain.core.config.args.Args;
import org.unichain.core.config.args.GenesisBlock;
import org.unichain.core.db.Manager;
import org.unichain.core.witness.WitnessController;
import org.unichain.protos.Protocol.Transaction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BlockUtil {

  /**
   * create genesis block from transactions.
   */
  public static BlockCapsule newGenesisBlockCapsule() {
    Args args = Args.getInstance();
    GenesisBlock genesisBlockArg = args.getGenesisBlock();
    List<Transaction> transactionList = genesisBlockArg.getAssets().stream()
                                                        .map(key -> {
                                                          byte[] address = key.getAddress();
                                                          long balance = key.getBalance();
                                                          return TransactionUtil.newGenesisTransaction(address, balance);
                                                        })
                                                        .collect(Collectors.toList());

    long timestamp = Long.parseLong(genesisBlockArg.getTimestamp());
    ByteString parentHash = ByteString.copyFrom(ByteArray.fromHexString(genesisBlockArg.getParentHash()));
    long number = Long.parseLong(genesisBlockArg.getNumber());

    BlockCapsule blockCapsule = new BlockCapsule(timestamp, parentHash, number, transactionList);

    blockCapsule.setMerkleRoot();
    blockCapsule.setWitness("A new system must allow existing systems to be linked together without " + "requiring any central control or coordination");
    blockCapsule.generatedByMyself = true;
    return blockCapsule;
  }

  /**
   * Whether the hash of the judge block is equal to the hash of the parent block.
   */
  public static boolean isParentOf(BlockCapsule blockCapsule1, BlockCapsule blockCapsule2) {
    return blockCapsule1.getBlockId().equals(blockCapsule2.getParentHash());
  }

  public static BlockCapsule createTestBlockCapsule(Manager dbManager, long time, int version, long number, ByteString hash, Map<ByteString, String> addressToProvateKeys) {
    WitnessController witnessController = dbManager.getWitnessController();
    ByteString witnessAddress = witnessController.getScheduledWitness(witnessController.getSlotAtTime(time));
    BlockCapsule blockCapsule = new BlockCapsule(version, number, Sha256Hash.wrap(hash), time, witnessAddress);
    blockCapsule.generatedByMyself = true;
    blockCapsule.setMerkleRoot();
    blockCapsule.sign(ByteArray.fromHexString(addressToProvateKeys.get(witnessAddress)));
    return blockCapsule;
  }
}
