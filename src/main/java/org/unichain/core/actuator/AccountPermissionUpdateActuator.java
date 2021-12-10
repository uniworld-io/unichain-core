package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.AccountCapsule;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.AccountPermissionUpdateContract;
import org.unichain.protos.Protocol.Key;
import org.unichain.protos.Protocol.Permission;
import org.unichain.protos.Protocol.Permission.PermissionType;
import org.unichain.protos.Protocol.Transaction.Result.code;

import java.util.List;

import static java.util.stream.Collectors.toList;


@Slf4j(topic = "actuator")
public class AccountPermissionUpdateActuator extends AbstractActuator {

  AccountPermissionUpdateActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule result) throws ContractExeException {
    long fee = calcFee();
    try {
      val ctx = contract.unpack(AccountPermissionUpdateContract.class);
      var ownerAddress = ctx.getOwnerAddress().toByteArray();
      var accountStore = dbManager.getAccountStore();
      var account = accountStore.get(ownerAddress);
      account.updatePermissions(ctx.getOwner(), ctx.getWitness(), ctx.getActivesList());
      accountStore.put(ownerAddress, account);
      dbManager.adjustBalance(ownerAddress, -fee);
      dbManager.adjustBalance(dbManager.getAccountStore().getBurnaccount().createDbKey(), fee);
      result.setStatus(fee, code.SUCESS);
      return true;
    } catch (BalanceInsufficientException | InvalidProtocolBufferException e) {
      logger.error(e.getMessage(), e);
      result.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  private boolean checkPermission(Permission permission) throws ContractValidateException {
    try {
      Assert.isTrue(permission.getKeysCount() <= dbManager.getDynamicPropertiesStore().getTotalSignNum(), "number of keys in permission should not be greater than " + dbManager.getDynamicPropertiesStore().getTotalSignNum());
      Assert.isTrue(permission.getKeysCount() != 0, "key's count should be greater than 0");
      Assert.isTrue(!(permission.getType() == PermissionType.Witness && permission.getKeysCount() != 1), "Witness permission's key count should be 1");
      Assert.isTrue(permission.getThreshold() > 0, "permission's threshold should be greater than 0");

      String name = permission.getPermissionName();
      Assert.isTrue(!(!StringUtils.isEmpty(name) && name.length() > 32), "permission's name is too long");

      //check owner name
      Assert.isTrue(permission.getParentId() == 0, "permission's parent should be owner");

      long weightSum = 0;
      List<ByteString> addressList = permission.getKeysList()
              .stream()
              .map(x -> x.getAddress())
              .distinct()
              .collect(toList());

      Assert.isTrue(addressList.size() == permission.getKeysList().size(), "address should be distinct in permission " + permission.getType());

      for (Key key : permission.getKeysList()) {
        Assert.isTrue(Wallet.addressValid(key.getAddress().toByteArray()), "key is not a validate address");
        Assert.isTrue(key.getWeight() > 0, "key's weight should be greater than 0");
        weightSum = Math.addExact(weightSum, key.getWeight());
      }

      Assert.isTrue(weightSum >= permission.getThreshold(), "sum of all key's weight should not be less than threshold in permission " + permission.getType());

      ByteString operations = permission.getOperations();
      if (permission.getType() != PermissionType.Active) {
        Assert.isTrue(operations.isEmpty(), permission.getType() + " permission needn't operations");
        return true;
      }

      //check operations
      Assert.isTrue((operations.size() == 32), "operations size must 32");

      byte[] types1 = dbManager.getDynamicPropertiesStore().getAvailableContractType();
      for (int i = 0; i < 256; i++) {
        boolean b = (operations.byteAt(i / 8) & (1 << (i % 8))) != 0;
        boolean t = ((types1[(i / 8)] & 0xff) & (1 << (i % 8))) != 0;
        if (b && !t) {
          throw new ContractValidateException(i + " isn't a validate ContractType");
        }
      }
      return true;
    }
    catch (Exception e){
      logger.error(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    if (this.contract == null) {
      throw new ContractValidateException("No contract!");
    }
    if (this.dbManager == null) {
      throw new ContractValidateException("No dbManager!");
    }
    if (this.dbManager.getDynamicPropertiesStore().getAllowMultiSign() != 1) {
      throw new ContractValidateException("multi sign is not allowed, need to be opened by the committee");
    }
    if (!this.contract.is(AccountPermissionUpdateContract.class)) {
      throw new ContractValidateException("contract type error,expected type [AccountPermissionUpdateContract],real type[" + contract.getClass() + "]");
    }
    final AccountPermissionUpdateContract accountPermissionUpdateContract;
    try {
      accountPermissionUpdateContract = contract.unpack(AccountPermissionUpdateContract.class);
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
    byte[] ownerAddress = accountPermissionUpdateContract.getOwnerAddress().toByteArray();
    if (!Wallet.addressValid(ownerAddress)) {
      throw new ContractValidateException("invalidate ownerAddress");
    }
    AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
    if (accountCapsule == null) {
      throw new ContractValidateException("ownerAddress account does not exist");
    }

    if (!accountPermissionUpdateContract.hasOwner()) {
      throw new ContractValidateException("owner permission is missed");
    }

    if (accountCapsule.getIsWitness()) {
      if (!accountPermissionUpdateContract.hasWitness()) {
        throw new ContractValidateException("witness permission is missed");
      }
    } else {
      if (accountPermissionUpdateContract.hasWitness()) {
        throw new ContractValidateException("account isn't witness can't set witness permission");
      }
    }

    if (accountPermissionUpdateContract.getActivesCount() == 0) {
      throw new ContractValidateException("active permission is missed");
    }
    if (accountPermissionUpdateContract.getActivesCount() > 8) {
      throw new ContractValidateException("active permission is too many");
    }

    Permission owner = accountPermissionUpdateContract.getOwner();
    Permission witness = accountPermissionUpdateContract.getWitness();
    List<Permission> actives = accountPermissionUpdateContract.getActivesList();

    if (owner.getType() != PermissionType.Owner) {
      throw new ContractValidateException("owner permission type is error");
    }
    if (!checkPermission(owner)) {
      return false;
    }
    if (accountCapsule.getIsWitness()) {
      if (witness.getType() != PermissionType.Witness) {
        throw new ContractValidateException("witness permission type is error");
      }
      if (!checkPermission(witness)) {
        return false;
      }
    }
    for (Permission permission : actives) {
      if (permission.getType() != PermissionType.Active) {
        throw new ContractValidateException("active permission type is error");
      }
      if (!checkPermission(permission)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(AccountPermissionUpdateContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return dbManager.getDynamicPropertiesStore().getUpdateAccountPermissionFee();
  }
}
