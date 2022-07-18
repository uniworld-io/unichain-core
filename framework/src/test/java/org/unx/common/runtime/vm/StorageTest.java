package org.unx.common.runtime.vm;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Ignore;
import org.junit.Test;
import org.testng.Assert;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.runtime.UVMTestResult;
import org.unx.common.runtime.UvmTestUtils;
import org.unx.common.utils.WalletUtil;
import org.unx.core.config.Parameter.ForkBlockVersionConsts;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.ReceiptCheckErrException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.store.StoreFactory;
import org.unx.core.vm.config.VMConfig;
import org.unx.core.vm.repository.Repository;
import org.unx.core.vm.repository.RepositoryImpl;
import org.unx.protos.Protocol.Transaction;

@Slf4j
public class StorageTest extends VMTestBase {

  @Test
  public void writeAndCommit() {
    byte[] address = Hex.decode(OWNER_ADDRESS);
    DataWord storageKey1 = new DataWord("key1".getBytes());
    DataWord storageVal1 = new DataWord("val1".getBytes());
    DataWord nullKey = new DataWord("nullkey".getBytes());
    DataWord nullValue = new DataWord(0);

    rootRepository.putStorageValue(address, storageKey1, storageVal1);
    rootRepository.putStorageValue(address, nullKey, nullValue);

    // test cache
    Assert.assertEquals(rootRepository.getStorageValue(address, storageKey1), storageVal1);
    Assert.assertEquals(rootRepository.getStorageValue(address, nullKey), nullValue);
    rootRepository.commit();

    // use a new rootDeposit
    Repository repository1 = RepositoryImpl.createRoot(StoreFactory.getInstance());
    Assert.assertEquals(repository1.getStorageValue(address, storageKey1), storageVal1);
    Assert.assertNull(repository1.getStorageValue(address, nullKey));

    // delete key
    repository1.putStorageValue(address, storageKey1, nullValue);
    Assert.assertNotNull(repository1.getStorageValue(address, storageKey1));
    repository1.commit();
    Repository repository2 = RepositoryImpl.createRoot(StoreFactory.getInstance());
    Assert.assertNull(repository2.getStorageValue(address, storageKey1));
  }

  @Test
  public void writeWithoutCommit() {
    byte[] address = Hex.decode(OWNER_ADDRESS);
    DataWord storageKey1 = new DataWord("key1".getBytes());
    DataWord storageVal1 = new DataWord("val1".getBytes());
    DataWord nullKey = new DataWord("nullkey".getBytes());
    DataWord nullValue = new DataWord(0);

    rootRepository.putStorageValue(address, storageKey1, storageVal1);
    rootRepository.putStorageValue(address, nullKey, nullValue);
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, nullKey));
    rootRepository.commit();
    Assert.assertEquals(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageKey1), storageVal1);
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, nullKey));
  }

  /*
    pragma solidity ^0.4.0;
    contract StorageDemo{
      mapping(uint => string) public int2str;

      function testPut(uint256 i, string s) public {
        int2str[i] = s;
      }

      function testDelete(uint256 i) public {
        delete int2str[i];
      }
    }
  */
  @Test
  public void contractWriteAndDeleteStorage()
      throws ContractExeException, ReceiptCheckErrException, ContractValidateException,
      VMIllegalException {
    String contractName = "contractWriteAndDeleteStorage";
    byte[] address = Hex.decode(OWNER_ADDRESS);
    String ABI = "[{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"name\":"
        + "\"int2str\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,"
        + "\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\""
        + ":[{\"name\":\"i\",\"type\":\"uint256\"}],\"name\":\"testDelete\",\"outputs\":[],"
        + "\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},"
        + "{\"constant\":false,\"inputs\":[{\"name\":\"i\",\"type\":\"uint256\"},{\"name\":\"s\","
        + "\"type\":\"string\"}],\"name\":\"testPut\",\"outputs\":[],\"payable\":false,"
        + "\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]\n";

    String code = "608060405234801561001057600080fd5b50610341806100206000396000f3006080604052600436"
        + "106100565763ffffffff7c010000000000000000000000000000000000000000000000000000000060003504"
        + "166313d821f4811461005b57806330099fa9146100e8578063c38e31cc14610102575b600080fd5b34801561"
        + "006757600080fd5b50610073600435610160565b604080516020808252835181830152835191928392908301"
        + "9185019080838360005b838110156100ad578181015183820152602001610095565b50505050905090810190"
        + "601f1680156100da5780820380516001836020036101000a031916815260200191505b509250505060405180"
        + "910390f35b3480156100f457600080fd5b506101006004356101fa565b005b34801561010e57600080fd5b50"
        + "60408051602060046024803582810135601f8101859004850286018501909652858552610100958335953695"
        + "6044949193909101919081908401838280828437509497506102149650505050505050565b60006020818152"
        + "9181526040908190208054825160026001831615610100026000190190921691909104601f81018590048502"
        + "82018501909352828152929091908301828280156101f25780601f106101c757610100808354040283529160"
        + "2001916101f2565b820191906000526020600020905b8154815290600101906020018083116101d557829003"
        + "601f168201915b505050505081565b600081815260208190526040812061021191610236565b50565b600082"
        + "81526020818152604090912082516102319284019061027a565b505050565b50805460018160011615610100"
        + "020316600290046000825580601f1061025c5750610211565b601f0160209004906000526020600020908101"
        + "9061021191906102f8565b828054600181600116156101000203166002900490600052602060002090601f01"
        + "6020900481019282601f106102bb57805160ff19168380011785556102e8565b828001600101855582156102"
        + "e8579182015b828111156102e85782518255916020019190600101906102cd565b506102f49291506102f856"
        + "5b5090565b61031291905b808211156102f457600081556001016102fe565b905600a165627a7a72305820c9"
        + "8643943ea978505f9cca68bdf61681462daeee9f71a6aa4414609e48dbb46b0029";
    long value = 0;
    long fee = 100000000;
    long consumeUserResourcePercent = 0;

    // deploy contract
    Transaction unx = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        contractName, address, ABI, code, value, fee, consumeUserResourcePercent, null);
    byte[] contractAddress = WalletUtil.generateContractAddress(unx);
    runtime = UvmTestUtils.processTransactionAndReturnRuntime(unx, rootRepository, null);
    Assert.assertNull(runtime.getRuntimeError());

    // write storage
    // testPut(uint256,string) 1,"abc"
    // 1,"abc"
    String params1 = "00000000000000000000000000000000000000000000000000000000000000010000000000000"
        + "0000000000000000000000000000000000000000000000000400000000000000000000000000000000000000"
        + "0000000000000000000000000036162630000000000000000000000000000000000000000000000000000000"
        + "000";
    String params2 = "00000000000000000000000000000000000000000000000000000000000000010000000000000"
        + "0000000000000000000000000000000000000000000000000400000000000000000000000000000000000000"
        + "0000000000000000000000000033132330000000000000000000000000000000000000000000000000000000"
        + "000";
    byte[] triggerData = UvmTestUtils.parseAbi("testPut(uint256,string)", params1);
    UVMTestResult result = UvmTestUtils
        .triggerContractAndReturnUvmTestResult(Hex.decode(OWNER_ADDRESS),
            contractAddress, triggerData, 0, fee, manager, null);

    Assert.assertNull(result.getRuntime().getRuntimeError());

    // overwrite storage with same value
    // testPut(uint256,string) 1,"abc"
    triggerData = UvmTestUtils.parseAbi("testPut(uint256,string)", params1);
    result = UvmTestUtils
        .triggerContractAndReturnUvmTestResult(Hex.decode(OWNER_ADDRESS),
            contractAddress, triggerData, 0, fee, manager, null);

    Assert.assertNull(result.getRuntime().getRuntimeError());
    Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), 10855);

    // overwrite storage with same value
    // testPut(uint256,string) 1,"123"

    triggerData = UvmTestUtils.parseAbi("testPut(uint256,string)", params2);
    result = UvmTestUtils
        .triggerContractAndReturnUvmTestResult(Hex.decode(OWNER_ADDRESS),
            contractAddress, triggerData, 0, fee, manager, null);

    Assert.assertNull(result.getRuntime().getRuntimeError());
    Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), 10855);

    // delete storage
    // testDelete(uint256) 1
    triggerData = UvmTestUtils.parseAbi("testDelete(uint256)",
        "0000000000000000000000000000000000000000000000000000000000000001");
    result = UvmTestUtils
        .triggerContractAndReturnUvmTestResult(Hex.decode(OWNER_ADDRESS),
            contractAddress, triggerData, 0, fee, manager, null);
    Assert.assertNull(result.getRuntime().getRuntimeError());
    Assert.assertNull(result.getRuntime().getResult().getException());
    Assert.assertEquals(result.getReceipt().getEnergyUsageTotal(), 5389);
  }

  @Test
  @Ignore
  public void testParentChild() {
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    this.manager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionConsts.ENERGY_LIMIT, stats);
    VMConfig.initVmHardFork(true);
    byte[] address = Hex.decode(OWNER_ADDRESS);
    DataWord storageKey1 = new DataWord("key1".getBytes());
    DataWord storageVal1 = new DataWord("val1".getBytes());
    DataWord zeroKey = new DataWord("zero_key".getBytes());
    DataWord zeroValue = new DataWord(0);
    DataWord parentChangedVal = new DataWord("parent_changed_val".getBytes());

    DataWord storageParentKey1 = new DataWord("parent_key1".getBytes());
    DataWord storageParentVal1 = new DataWord("parent_val1".getBytes());
    DataWord storageParentZeroKey = new DataWord("parent_zero_key1".getBytes());

    Repository childRepository = rootRepository.newRepositoryChild();

    // write to root cache
    rootRepository.putStorageValue(address, storageParentKey1, storageParentVal1);
    rootRepository.putStorageValue(address, storageParentZeroKey, zeroValue);

    // write to child cache
    childRepository.putStorageValue(address, storageKey1, storageVal1);
    childRepository.putStorageValue(address, zeroKey, zeroValue);

    // check child cache
    Assert.assertEquals(childRepository.getStorageValue(address, storageKey1), storageVal1);
    Assert.assertEquals(childRepository.getStorageValue(address, zeroKey), zeroValue);
    Assert
        .assertEquals(childRepository.getStorageValue(address, storageParentKey1),
            storageParentVal1);
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentZeroKey), zeroValue);

    childRepository.putStorageValue(address, storageParentKey1, parentChangedVal);

    // check root cache
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentKey1),
        parentChangedVal);
    Assert.assertEquals(rootRepository.getStorageValue(address, storageParentKey1),
        storageParentVal1);

    Assert.assertNull(rootRepository.getStorageValue(address, storageKey1));
    Assert.assertNull(rootRepository.getStorageValue(address, zeroKey));
    Assert.assertEquals(rootRepository.getStorageValue(address, storageParentZeroKey), zeroValue);

    // check db
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, zeroKey));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageParentKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageParentZeroKey));

    // commit child cache
    childRepository.commit();

    // check root cache
    Assert.assertEquals(rootRepository.getStorageValue(address, storageKey1), storageVal1);
    Assert.assertEquals(rootRepository.getStorageValue(address, zeroKey), zeroValue);
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentKey1),
        parentChangedVal);
    Assert.assertEquals(rootRepository.getStorageValue(address, storageParentKey1),
        parentChangedVal);
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentZeroKey), zeroValue);

    // check db
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, zeroKey));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageParentKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageParentZeroKey));

    rootRepository.commit();
    Assert.assertEquals(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageKey1), storageVal1);
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, zeroKey));
    Assert.assertEquals(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageParentKey1), parentChangedVal);
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageParentZeroKey));
    CommonParameter.setENERGY_LIMIT_HARD_FORK(false);
  }

  @Test
  public void testParentChildOldVersion() {
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 0);
    this.manager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionConsts.ENERGY_LIMIT, stats);
    byte[] address = Hex.decode(OWNER_ADDRESS);
    DataWord storageKey1 = new DataWord("key1".getBytes());
    DataWord storageVal1 = new DataWord("val1".getBytes());
    DataWord zeroKey = new DataWord("zero_key".getBytes());
    DataWord zeroValue = new DataWord(0);
    DataWord parentChangedVal = new DataWord("parent_changed_val".getBytes());

    DataWord storageParentKey1 = new DataWord("parent_key1".getBytes());
    DataWord storageParentVal1 = new DataWord("parent_val1".getBytes());
    DataWord storageParentZeroKey = new DataWord("parent_zero_key1".getBytes());

    Repository childRepository = rootRepository.newRepositoryChild();

    // write to root cache
    rootRepository.putStorageValue(address, storageParentKey1, storageParentVal1);
    rootRepository.putStorageValue(address, storageParentZeroKey, zeroValue);

    // write to child cache
    childRepository.putStorageValue(address, storageKey1, storageVal1);
    childRepository.putStorageValue(address, zeroKey, zeroValue);

    // check child cache
    Assert.assertEquals(childRepository.getStorageValue(address, storageKey1), storageVal1);
    Assert.assertEquals(childRepository.getStorageValue(address, zeroKey), zeroValue);
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentKey1),
        storageParentVal1);
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentZeroKey), zeroValue);

    childRepository.putStorageValue(address, storageParentKey1, parentChangedVal);

    // check root cache
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentKey1),
        parentChangedVal);
    Assert.assertEquals(rootRepository.getStorageValue(address, storageParentKey1),
        parentChangedVal);

    Assert.assertEquals(rootRepository.getStorageValue(address, storageKey1), storageVal1);
    Assert.assertEquals(rootRepository.getStorageValue(address, zeroKey), zeroValue);
    Assert.assertEquals(rootRepository.getStorageValue(address, storageParentZeroKey), zeroValue);

    // check parent deposit == child deposit
    Assert.assertEquals(rootRepository.getStorageValue(address, storageKey1),
        childRepository.getStorageValue(address, storageKey1));
    Assert.assertEquals(rootRepository.getStorageValue(address, zeroKey),
        childRepository.getStorageValue(address, zeroKey));
    Assert.assertEquals(rootRepository.getStorageValue(address, storageParentKey1),
        childRepository.getStorageValue(address, storageParentKey1));
    Assert.assertEquals(rootRepository.getStorageValue(address, storageParentZeroKey),
        childRepository.getStorageValue(address, storageParentZeroKey));

    // check db
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, zeroKey));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageParentKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageParentZeroKey));

    // didn't commit child cache
    //    childRepository.commit();

    // check root cache
    Assert.assertEquals(rootRepository.getStorageValue(address, storageKey1), storageVal1);
    Assert.assertEquals(rootRepository.getStorageValue(address, zeroKey), zeroValue);
    Assert.assertEquals(rootRepository
        .getStorageValue(address, storageParentKey1), parentChangedVal);

    Assert.assertEquals(childRepository
        .getStorageValue(address, storageParentKey1), parentChangedVal);
    Assert.assertEquals(childRepository.getStorageValue(address, storageParentZeroKey), zeroValue);

    // check db
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, zeroKey));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageParentKey1));
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageParentZeroKey));

    rootRepository.commit();
    Assert.assertEquals(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageKey1), storageVal1);
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, zeroKey));
    Assert.assertEquals(RepositoryImpl.createRoot(StoreFactory.getInstance())
            .getStorageValue(address, storageParentKey1), parentChangedVal);
    Assert.assertNull(RepositoryImpl.createRoot(StoreFactory.getInstance())
        .getStorageValue(address, storageParentZeroKey));
    CommonParameter.setENERGY_LIMIT_HARD_FORK(false);
  }
}
