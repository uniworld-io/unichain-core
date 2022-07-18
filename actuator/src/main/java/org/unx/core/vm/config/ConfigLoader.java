package org.unx.core.vm.config;

import static org.unx.core.capsule.ReceiptCapsule.checkForEnergyLimit;

import lombok.extern.slf4j.Slf4j;
import org.unx.common.parameter.CommonParameter;
import org.unx.core.store.DynamicPropertiesStore;
import org.unx.core.store.StoreFactory;

@Slf4j(topic = "VMConfigLoader")
public class ConfigLoader {

  //only for unit test
  public static boolean disable = false;

  public static void load(StoreFactory storeFactory) {
    if (!disable) {
      DynamicPropertiesStore ds = storeFactory.getChainBaseManager().getDynamicPropertiesStore();
      VMConfig.setVmTrace(CommonParameter.getInstance().isVmTrace());
      if (ds != null) {
        VMConfig.initVmHardFork(checkForEnergyLimit(ds));
        VMConfig.initAllowMultiSign(ds.getAllowMultiSign());
        VMConfig.initAllowUvmTransferUrc10(ds.getAllowUvmTransferUrc10());
        VMConfig.initAllowUvmConstantinople(ds.getAllowUvmConstantinople());
        VMConfig.initAllowUvmSolidity059(ds.getAllowUvmSolidity059());
        VMConfig.initAllowShieldedURC20Transaction(ds.getAllowShieldedURC20Transaction());
        VMConfig.initAllowUvmIstanbul(ds.getAllowUvmIstanbul());
        VMConfig.initAllowUvmFreeze(ds.getAllowUvmFreeze());
        VMConfig.initAllowUvmVote(ds.getAllowUvmVote());
        VMConfig.initAllowUvmLondon(ds.getAllowUvmLondon());
        VMConfig.initAllowUvmCompatibleEvm(ds.getAllowUvmCompatibleEvm());
        VMConfig.initAllowHigherLimitForMaxCpuTimeOfOneTx(
            ds.getAllowHigherLimitForMaxCpuTimeOfOneTx());
      }
    }
  }
}
