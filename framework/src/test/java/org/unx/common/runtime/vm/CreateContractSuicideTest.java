package org.unx.common.runtime.vm;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;
import org.testng.Assert;
import org.unx.common.runtime.Runtime;
import org.unx.common.runtime.UvmTestUtils;
import org.unx.core.config.Parameter.ForkBlockVersionEnum;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.ReceiptCheckErrException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.store.StoreFactory;
import org.unx.core.vm.config.ConfigLoader;
import org.unx.core.vm.program.Program.OutOfEnergyException;
import org.unx.core.vm.repository.RepositoryImpl;
import org.unx.protos.Protocol.Transaction;

@Slf4j
public class CreateContractSuicideTest extends VMTestBase {

  /*

  pragma solidity ^0.4.24;

contract testA {
    constructor() public payable {
        A a = (new A).value(10)();
        a.fun();
    }
}

contract testB {
    constructor() public payable {
        B b = (new B).value(10)();
        b.fun();
    }
}


contract testC {
    constructor() public payable{
        C c = (new C).value(10)();
        c.fun();
    }
}

contract testD {
    constructor() public payable{
        D d = (new D).value(10)();
        d.fun();
    }
}


contract A {
    constructor() public payable{
        selfdestruct(msg.sender);
    }
    function fun() {
    }

}

contract B {
    constructor() public payable {
        revert();
    }
    function fun() {
    }
}


contract C {
    constructor() public payable {
       assert(1==2);
    }
    function fun() {
    }
}

contract D {
    constructor() public payable {
       require(1==2);
    }
    function fun() {
    }
}
   */

  String abi = "[{\"inputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\""
      + "type\":\"constructor\"}]";

  String aCode = "60806040526000600a600e609f565b6040518091039082f0801580156028573d6000803e3d6000fd"
      + "5b509050905080600160a060020a031663946644cd6040518163ffffffff167c0100000000000000000000000"
      + "000000000000000000000000000000000028152600401600060405180830381600087803b1580156083576000"
      + "80fd5b505af11580156096573d6000803e3d6000fd5b505050505060ad565b60405160088060ef83390190565"
      + "b60358060ba6000396000f3006080604052600080fd00a165627a7a723058205f699e7434a691ee9a433c4979"
      + "73f2eee624efde40e7b7dd86512767fbe7752c0029608060405233ff00";
  String bCode = "60806040526000600a600e609f565b6040518091039082f0801580156028573d6000803e3d6000fd"
      + "5b509050905080600160a060020a031663946644cd6040518163ffffffff167c0100000000000000000000000"
      + "000000000000000000000000000000000028152600401600060405180830381600087803b1580156083576000"
      + "80fd5b505af11580156096573d6000803e3d6000fd5b505050505060ae565b604051600a806100f1833901905"
      + "65b6035806100bc6000396000f3006080604052600080fd00a165627a7a7230582036a40a807cbf7150801157"
      + "4ef42c706ad7b40d844807909c3b8630f9fb9ae6f700296080604052600080fd00";
  String cCode = "60806040526000600a600e609f565b6040518091039082f0801580156028573d6000803e3d6000fd"
      + "5b509050905080600160a060020a031663946644cd6040518163ffffffff167c0100000000000000000000000"
      + "000000000000000000000000000000000028152600401600060405180830381600087803b1580156083576000"
      + "80fd5b505af11580156096573d6000803e3d6000fd5b505050505060ad565b60405160078060ef83390190565"
      + "b60358060ba6000396000f3006080604052600080fd00a165627a7a72305820970ee7543687d338b72131a122"
      + "af927a698a081c0118577f49fffd8831a1195800296080604052fe00";
  String dCode = "60806040526000600a600e609f565b6040518091039082f0801580156028573d6000803e3d6000fd"
      + "5b509050905080600160a060020a031663946644cd6040518163ffffffff167c0100000000000000000000000"
      + "000000000000000000000000000000000028152600401600060405180830381600087803b1580156083576000"
      + "80fd5b505af11580156096573d6000803e3d6000fd5b505050505060ae565b604051600a806100f1833901905"
      + "65b6035806100bc6000396000f3006080604052600080fd00a165627a7a72305820fd7ca23ea399b6d513a8d4"
      + "eb084f5eb748b94fab6437bfb5ea9f4a03d9715c3400296080604052600080fd00";


  long value = 100;
  long fee = 1000000000;
  long consumeUserResourcePercent = 0;
  long engeryLimit = 1000000000;


  @Test
  public void testAAfterAllowMultiSignProposal()
      throws ContractExeException, ReceiptCheckErrException,
      VMIllegalException, ContractValidateException {
    byte[] stats = new byte[27];
    Arrays.fill(stats, (byte) 1);
    //VMConfig.initAllowUvmTransferUrc10(1);
    ConfigLoader.disable = false;
    this.manager.getDynamicPropertiesStore().saveAllowMultiSign(1);
    this.manager.getDynamicPropertiesStore().saveAllowUvmTransferUrc10(1);
    byte[] address = Hex.decode(OWNER_ADDRESS);

    this.manager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionEnum.VERSION_3_2_2.getValue(), stats);
    this.manager.getDynamicPropertiesStore()
        .statsByVersion(ForkBlockVersionEnum.VERSION_3_5.getValue(), stats);

    Transaction aUnx = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testA", address, abi, aCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime aRuntime = UvmTestUtils.processTransactionAndReturnRuntime(aUnx,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertEquals(aRuntime.getRuntimeError(), "REVERT opcode executed");

    Transaction bUnx = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testB", address, abi, bCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime bRuntime = UvmTestUtils.processTransactionAndReturnRuntime(bUnx,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertEquals(bRuntime.getRuntimeError(), "REVERT opcode executed");

    Transaction cUnx = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testC", address, abi, cCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime cRuntime = UvmTestUtils.processTransactionAndReturnRuntime(cUnx,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertTrue(cRuntime.getResult().getException() instanceof OutOfEnergyException);

    Transaction dUnx = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testC", address, abi, dCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime dRuntime = UvmTestUtils.processTransactionAndReturnRuntime(dUnx,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertEquals(dRuntime.getRuntimeError(), "REVERT opcode executed");

  }

  @Test
  public void testABeforeAllowMultiSignProposal()
      throws ContractExeException, ReceiptCheckErrException,
      VMIllegalException, ContractValidateException {
    //VMConfig.initAllowMultiSign(0);
    ConfigLoader.disable = false;
    this.manager.getDynamicPropertiesStore().saveAllowMultiSign(0);
    this.manager.getDynamicPropertiesStore().saveAllowUvmTransferUrc10(1);

    byte[] address = Hex.decode(OWNER_ADDRESS);
    Transaction aUnw = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testA", address, abi, aCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime aRuntime = UvmTestUtils.processTransactionAndReturnRuntime(aUnw,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertEquals(aRuntime.getRuntimeError(), "Unknown Exception");

    Transaction bUnw = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testB", address, abi, bCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime bRuntime = UvmTestUtils.processTransactionAndReturnRuntime(bUnw,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertEquals(bRuntime.getRuntimeError(), "REVERT opcode executed");

    Transaction cUnw = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testC", address, abi, cCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime cRuntime = UvmTestUtils.processTransactionAndReturnRuntime(cUnw,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertTrue(cRuntime.getResult().getException() instanceof OutOfEnergyException);

    Transaction dUnw = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        "testC", address, abi, dCode, value, fee, consumeUserResourcePercent, null, engeryLimit);
    Runtime dRuntime = UvmTestUtils.processTransactionAndReturnRuntime(dUnw,
        RepositoryImpl.createRoot(StoreFactory.getInstance()), null);
    Assert.assertEquals(dRuntime.getRuntimeError(), "REVERT opcode executed");


  }
}
