package org.unx.core.store;

import com.google.protobuf.ByteString;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.unx.common.parameter.CommonParameter;
import org.unx.common.utils.ByteArray;
import org.unx.core.capsule.TransactionInfoCapsule;
import org.unx.core.capsule.TransactionRetCapsule;
import org.unx.core.db.TransactionStore;
import org.unx.core.db.UnxStoreWithRevoking;
import org.unx.core.exception.BadItemException;
import org.unx.protos.Protocol.TransactionInfo;

@Slf4j(topic = "DB")
@Component
public class TransactionRetStore extends UnxStoreWithRevoking<TransactionRetCapsule> {

  @Autowired
  private TransactionStore transactionStore;

  @Autowired
  public TransactionRetStore(@Value("transactionRetStore") String dbName) {
    super(dbName);
  }

  @Override
  public void put(byte[] key, TransactionRetCapsule item) {
    if (BooleanUtils.toBoolean(CommonParameter.getInstance()
        .getStorage().getTransactionHistorySwitch())) {
      super.put(key, item);
    }
  }

  public TransactionInfoCapsule getTransactionInfo(byte[] key) throws BadItemException {
    long blockNumber = transactionStore.getBlockNumber(key);
    if (blockNumber == -1) {
      return null;
    }
    byte[] value = revokingDB.getUnchecked(ByteArray.fromLong(blockNumber));
    if (Objects.isNull(value)) {
      return null;
    }

    TransactionRetCapsule result = new TransactionRetCapsule(value);
    if (Objects.isNull(result.getInstance())) {
      return null;
    }

    ByteString id = ByteString.copyFrom(key);
    for (TransactionInfo transactionResultInfo : result.getInstance().getTransactioninfoList()) {
      if (transactionResultInfo.getId().equals(id)) {
        return new TransactionInfoCapsule(transactionResultInfo);
      }
    }
    return null;
  }

  public TransactionRetCapsule getTransactionInfoByBlockNum(byte[] key) throws BadItemException {

    byte[] value = revokingDB.getUnchecked(key);
    if (Objects.isNull(value)) {
      return null;
    }

    return new TransactionRetCapsule(value);
  }

}
