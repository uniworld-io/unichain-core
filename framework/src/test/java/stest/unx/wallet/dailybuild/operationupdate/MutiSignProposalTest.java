package stest.unx.wallet.dailybuild.operationupdate;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.unx.api.GrpcAPI.EmptyMessage;
import org.unx.api.GrpcAPI.ProposalList;
import org.unx.api.WalletGrpc;
import org.unx.api.WalletSolidityGrpc;
import org.unx.common.crypto.ECKey;
import org.unx.common.utils.ByteArray;
import org.unx.common.utils.Utils;
import stest.unx.wallet.common.client.Configuration;
import stest.unx.wallet.common.client.utils.PublicMethed;
import stest.unx.wallet.common.client.utils.PublicMethedForMutiSign;


@Slf4j
public class MutiSignProposalTest {

  private static final long now = System.currentTimeMillis();
  private final String testKey002 = Configuration.getByPath("testng.conf")
      .getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String witnessKey001 = Configuration.getByPath("testng.conf")
      .getString("witness.key2");
  private final byte[] witness001Address = PublicMethed.getFinalAddress(witnessKey001);
  private final String operations = Configuration.getByPath("testng.conf")
      .getString("defaultParameter.operations");
  String[] permissionKeyString = new String[2];
  String[] ownerKeyString = new String[2];
  String accountPermissionJson = "";
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] manager1Address = ecKey1.getAddress();
  String manager1Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  ECKey ecKey2 = new ECKey(Utils.getRandom());
  byte[] manager2Address = ecKey2.getAddress();
  String manager2Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());
  private long multiSignFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.multiSignFee");
  private long updateAccountPermissionFee = Configuration.getByPath("testng.conf")
      .getLong("defaultParameter.updateAccountPermissionFee");
  private ManagedChannel channelFull = null;
  private ManagedChannel channelSolidity = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  private String fullnode = Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list")
      .get(0);
  private String soliditynode = Configuration.getByPath("testng.conf")
      .getStringList("solidityNode.ip.list").get(0);



  /**
   * constructor.
   */

  @BeforeClass
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode)
        .usePlaintext(true)
        .build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode)
        .usePlaintext(true)
        .build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);
  }

  @Test(enabled = true)
  public void testMutiSignForProposal() {
    long needcoin = updateAccountPermissionFee + multiSignFee * 5;
    Assert.assertTrue(PublicMethed.sendcoin(witness001Address, needcoin + 10000000L,
        fromAddress, testKey002, blockingStubFull));

    ecKey1 = new ECKey(Utils.getRandom());
    manager1Address = ecKey1.getAddress();
    manager1Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());

    ecKey2 = new ECKey(Utils.getRandom());
    manager2Address = ecKey2.getAddress();
    manager2Key = ByteArray.toHexString(ecKey2.getPrivKeyBytes());

    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long balanceBefore = PublicMethed.queryAccount(witness001Address, blockingStubFull)
        .getBalance();
    logger.info("balanceBefore: " + balanceBefore);

    permissionKeyString[0] = manager1Key;
    permissionKeyString[1] = manager2Key;
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    ownerKeyString[0] = witnessKey001;
    ownerKeyString[1] = testKey002;

    accountPermissionJson = "{\"owner_permission\":{\"type\":0,\"permission_name\":\"owner\""
        + ",\"threshold\":2,\"keys\":[{\"address\":\"" + PublicMethed
        .getAddressString(witnessKey001) + "\","
        + "\"weight\":1},{\"address\":\"" + PublicMethed.getAddressString(testKey002)
        + "\",\"weight\":1}]},"
        + "\"witness_permission\":{\"type\":1,\"permission_name\":\"owner\",\"threshold\":1,"
        + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(witnessKey001)
        + "\",\"weight\":1}]},"
        + "\"active_permissions\":[{\"type\":2,\"permission_name\":\"active0\",\"threshold\":2,"
        + "\"operations\":\"7fff1fc0037e0000000000000000000000000000000000000000000000000000\","
        + "\"keys\":[{\"address\":\"" + PublicMethed.getAddressString(manager1Key)
        + "\",\"weight\":1},"
        + "{\"address\":\"" + PublicMethed.getAddressString(manager2Key) + "\",\"weight\":1}]}]} ";
    logger.info(accountPermissionJson);
    PublicMethedForMutiSign.accountPermissionUpdate(
        accountPermissionJson, witness001Address, witnessKey001,
        blockingStubFull, ownerKeyString);

    //Create a proposal

    PublicMethed.waitProduceNextBlock(blockingStubFull);
    HashMap<Long, Long> proposalMap = new HashMap<Long, Long>();
    proposalMap.put(0L, 81000L);
    Assert.assertTrue(
        PublicMethedForMutiSign.createProposalWithPermissionId(witness001Address, witnessKey001,
            proposalMap, 0, blockingStubFull, ownerKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Get proposal list
    ProposalList proposalList = blockingStubFull.listProposals(EmptyMessage.newBuilder().build());
    Optional<ProposalList> listProposals = Optional.ofNullable(proposalList);
    final Integer proposalId = listProposals.get().getProposalsCount();
    logger.info(Integer.toString(proposalId));

    Assert.assertTrue(PublicMethedForMutiSign.approveProposalWithPermission(
        witness001Address, witnessKey001, proposalId,
        true, 0, blockingStubFull, ownerKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    //Delete proposal list after approve
    Assert.assertTrue(PublicMethedForMutiSign.deleteProposalWithPermissionId(
        witness001Address, witnessKey001, proposalId, 0, blockingStubFull, ownerKeyString));
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    Long balanceAfter = PublicMethed.queryAccount(witness001Address, blockingStubFull)
        .getBalance();
    logger.info("balanceAfter: " + balanceAfter);

  }

  /**
   * constructor.
   */

  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (channelSolidity != null) {
      channelSolidity.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}


