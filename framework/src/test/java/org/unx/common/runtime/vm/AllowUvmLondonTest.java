package org.unx.common.runtime.vm;

import static org.unx.common.utils.ByteUtil.longTo32Bytes;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;
import org.testng.Assert;
import org.unx.common.runtime.UVMTestResult;
import org.unx.common.runtime.UvmTestUtils;
import org.unx.common.utils.WalletUtil;
import org.unx.core.exception.ContractExeException;
import org.unx.core.exception.ContractValidateException;
import org.unx.core.exception.ReceiptCheckErrException;
import org.unx.core.exception.VMIllegalException;
import org.unx.core.vm.config.ConfigLoader;
import org.unx.core.vm.config.VMConfig;
import org.unx.protos.Protocol;
import stest.unx.wallet.common.client.utils.AbiUtil;

@Slf4j
public class AllowUvmLondonTest extends VMTestBase {

  /*contract c {

    function getbasefee() public returns(uint) {
      return block.basefee;
    }

  }*/

  @Test
  public void testBaseFee() throws ContractExeException, ReceiptCheckErrException,
      VMIllegalException, ContractValidateException {
    ConfigLoader.disable = true;
    VMConfig.initAllowUvmTransferUrc10(1);
    VMConfig.initAllowUvmConstantinople(1);
    VMConfig.initAllowUvmSolidity059(1);
    VMConfig.initAllowUvmIstanbul(1);
    VMConfig.initAllowUvmLondon(1);
    manager.getDynamicPropertiesStore().saveChangeDelegation(1);

    String contractName = "testBaseFee";
    byte[] address = Hex.decode(OWNER_ADDRESS);
    String abi = "[{\"inputs\":[],\"name\":\"getbasefee\","
        + "\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],"
        + "\"stateMutability\":\"view\",\"type\":\"function\"}]";
    String factoryCode = "6080604052348015600f57600080fd5b50607680"
        + "601d6000396000f3fe6080604052348015600f57600080fd5b5060043"
        + "61060285760003560e01c80632266bff614602d575b600080fd5b4860"
        + "405190815260200160405180910390f3fea2646970667358221220091"
        + "a527d7e484183e543b1ba4520176c089c72f6f3451d8419a19b363314"
        + "674a64736f6c63430008070033";
    long value = 0;
    long feeLimit = 100000000;
    long consumeUserResourcePercent = 0;

    // deploy contract
    Protocol.Transaction unx = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        contractName, address, abi, factoryCode, value, feeLimit, consumeUserResourcePercent,
        null);
    byte[] factoryAddress = WalletUtil.generateContractAddress(unx);
    runtime = UvmTestUtils.processTransactionAndReturnRuntime(unx, rootRepository, null);
    Assert.assertNull(runtime.getRuntimeError());

    // Trigger contract method: getbasefee()
    String methodByAddr = "getbasefee()";
    String hexInput =
        AbiUtil.parseMethod(methodByAddr, Collections.singletonList(""));

    UVMTestResult result = UvmTestUtils
        .triggerContractAndReturnUvmTestResult(Hex.decode(OWNER_ADDRESS),
            factoryAddress, Hex.decode(hexInput), 0, feeLimit, manager, null);
    byte[] returnValue = result.getRuntime().getResult().getHReturn();
    Assert.assertNull(result.getRuntime().getRuntimeError());
    Assert.assertEquals(returnValue,
        longTo32Bytes(manager.getDynamicPropertiesStore().getEnergyFee()));
  }

  @Test
  public void testStartWithEF() throws ContractExeException, ReceiptCheckErrException,
      VMIllegalException, ContractValidateException {
    ConfigLoader.disable = true;
    VMConfig.initAllowUvmTransferUrc10(1);
    VMConfig.initAllowUvmConstantinople(1);
    VMConfig.initAllowUvmSolidity059(1);
    VMConfig.initAllowUvmIstanbul(1);
    VMConfig.initAllowUvmLondon(1);
    manager.getDynamicPropertiesStore().saveChangeDelegation(1);

    String contractName = "testStartWithEF";
    byte[] address = Hex.decode(OWNER_ADDRESS);
    String abi = "[{\"inputs\":[],\"name\":\"getbasefee\","
        + "\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],"
        + "\"stateMutability\":\"view\",\"type\":\"function\"}]";
    String factoryCode = "60ef60005360016000f3";
    long value = 0;
    long feeLimit = 100000000;
    long consumeUserResourcePercent = 0;

    // deploy contract
    Protocol.Transaction unx = UvmTestUtils.generateDeploySmartContractAndGetTransaction(
        contractName, address, abi, factoryCode, value, feeLimit, consumeUserResourcePercent,
        null);
    runtime = UvmTestUtils.processTransactionAndReturnRuntime(unx, rootRepository, null);
    Assert.assertNotNull(runtime.getRuntimeError());
  }
}
