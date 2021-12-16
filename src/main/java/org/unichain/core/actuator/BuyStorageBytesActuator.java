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
import org.unichain.protos.Contract.BuyStorageBytesContract;
import org.unichain.protos.Protocol.Transaction.Result.code;

@Deprecated
@Slf4j(topic = "actuator")
public class BuyStorageBytesActuator extends AbstractActuator {

  private StorageMarket storageMarket;

  BuyStorageBytesActuator(Any contract, Manager dbManager) {
    super(contract, dbManager);
    storageMarket = new StorageMarket(dbManager);
  }

  @Override
  public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
    var fee = calcFee();
    try {
      val BuyStorageBytesContract = contract.unpack(BuyStorageBytesContract.class);
      var ownerAddress = BuyStorageBytesContract.getOwnerAddress().toByteArray();
      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      var bytes = BuyStorageBytesContract.getBytes();
      storageMarket.buyStorageBytes(accountCapsule, bytes);
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
      Assert.isTrue(contract.is(BuyStorageBytesContract.class), "contract type error,expected type [BuyStorageBytesContract],real type[" + contract.getClass() + "]");

      val BuyStorageBytesContract = this.contract.unpack(BuyStorageBytesContract.class);
      var ownerAddress = BuyStorageBytesContract.getOwnerAddress().toByteArray();
      Assert.isTrue(Wallet.addressValid(ownerAddress), "Invalid address");

      var accountCapsule = dbManager.getAccountStore().get(ownerAddress);
      Assert.notNull(accountCapsule, "Account[" + StringUtil.createReadableString(ownerAddress) + "] not exists");

      var bytes = BuyStorageBytesContract.getBytes();
      Assert.isTrue(bytes >= 0, "bytes must be positive");
      Assert.isTrue(bytes >= 1L, "bytes must be larger than 1, current storage_bytes[" + bytes + "]");

      var quant = storageMarket.tryBuyStorageBytes(bytes);
      Assert.isTrue(quant >= 1_000_000L, "quantity must be larger than 1 UNW");
      Assert.isTrue(quant <= accountCapsule.getBalance(), "quantity must be less than accountBalance");

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
    return contract.unpack(BuyStorageBytesContract.class).getOwnerAddress();
  }

  @Override
  public long calcFee() {
    return 0;
  }
}
