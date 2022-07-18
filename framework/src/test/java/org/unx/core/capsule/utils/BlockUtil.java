package org.unx.core.capsule.utils;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.stream.Collectors;
import org.unx.common.args.GenesisBlock;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.utils.ByteArray;
import org.unx.core.capsule.BlockCapsule;
import org.unx.core.config.args.Args;
import org.unx.protos.Protocol.Transaction;

public class BlockUtil {

  /**
   * create genesis block from transactions.
   */
  public static BlockCapsule newGenesisBlockCapsule() {

    CommonParameter parameter = Args.getInstance();
    GenesisBlock genesisBlockArg = parameter.getGenesisBlock();
    List<Transaction> transactionList = genesisBlockArg.getAssets().stream().map(key -> {
      byte[] address = key.getAddress();
      long balance = key.getBalance();
      return TransactionUtil.newGenesisTransaction(address, balance);
    }).collect(Collectors.toList());

    long timestamp = Long.parseLong(genesisBlockArg.getTimestamp());
    ByteString parentHash = ByteString
        .copyFrom(ByteArray.fromHexString(genesisBlockArg.getParentHash()));
    long number = Long.parseLong(genesisBlockArg.getNumber());

    BlockCapsule blockCapsule = new BlockCapsule(timestamp, parentHash, number, transactionList);

    blockCapsule.setMerkleRoot();
    blockCapsule.setWitness("A new system must allow existing systems to be linked together "
        + "without requiring any central control or coordination");
    blockCapsule.generatedByMyself = true;

    return blockCapsule;
  }

  public static boolean isParentOf(BlockCapsule blockCapsule1, BlockCapsule blockCapsule2) {
    return blockCapsule1.getBlockId().equals(blockCapsule2.getParentHash());
  }

}