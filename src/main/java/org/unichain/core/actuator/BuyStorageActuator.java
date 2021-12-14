package org.unichain.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.util.Assert;
import org.unichain.common.utils.StringUtil;
import org.unichain.core.Wallet;
import org.unichain.core.capsule.TransactionResultCapsule;
import org.unichain.core.db.Manager;
import org.unichain.core.db.StorageMarket;
import org.unichain.core.exception.BalanceInsufficientException;
import org.unichain.core.exception.ContractExeException;
import org.unichain.core.exception.ContractValidateException;
import org.unichain.protos.Contract.BuyStorageContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Deprecated
@Slf4j(topic = "actuator")
public class BuyStorageActuator extends AbstractActuator {

  private StorageMarket storageMarket;

  BuyStorageActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
    storageMarket = new StorageMarket(dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    final BuyStorageContract buyStorageContract;
    try {
      buyStorageContract = contract.unpack(BuyStorageContract.class);
      var ownerAddress = buyStorageContract.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      long qty = buyStorageContract.getQuant();
      storageMarket.buyStorage(accountCapsule, qty);
      chargeFee(ownerAddress, fee);
      ret.setStatus(fee, code.SUCESS);
      return true;

    } catch (InvalidProtocolBufferException | BalanceInsufficientException e) {
      logger.debug(e.getMessage(), e);
      ret.setStatus(fee, code.FAILED);
      throw new ContractExeException(e.getMessage());
    }
  }

  @Override
  public boolean validate() throws ContractValidateException {
    try {
      Assert.notNull(contract, "No contract!");
      Assert.notNull(dbManager, "No dbManager!");
      Assert.isTrue(this.contract.is(BuyStorageContract.class), "contract type error,expected type [BuyStorageContract],real type[" + contract.getClass() + "]");

      val buyStorageContract = this.contract.unpack(BuyStorageContract.class);
      var ownerAddress = buyStorageContract.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      long quant = buyStorageContract.getQuant();
      Assert.isTrue(quant > 0, "quantity must be positive");
      Assert.isTrue(quant >= 1000_000L, "quantity must be larger than 1UNW");
      Assert.isTrue(quant <= accountCapsule.getBalance(), "quantity must be less than accountBalance");

      long storage_bytes = storageMarket.tryBuyStorage(quant);
      Assert.isTrue(storage_bytes >= 1L, "storage_bytes must be larger than 1,current storage_bytes[\" + storage_bytes + \"]");

//    long storageBytes = storageMarket.exchange(quant, true);
//    if (storageBytes > dbManager.getDynamicPropertiesStore().getTotalStorageReserved()) {
//      throw new ContractValidateException("storage is not enough");
//    }

      return true;
    } catch (InvalidProtocolBufferException e) {
      logger.debug(e.getMessage(), e);
      throw new ContractValidateException(e.getMessage());
    }
  }

  @Override
  public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
    return contract.unpack(BuyStorageContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
