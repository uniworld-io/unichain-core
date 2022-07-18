package org.unx.core.vm.program;

import static java.lang.System.arraycopy;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.unx.common.crypto.Hash;
import org.unx.common.runtime.vm.DataWord;
import org.unx.common.utils.ByteUtil;
import org.unx.core.capsule.StorageRowCapsule;
import org.unx.core.store.StorageRowStore;

public class Storage {

  private static final int PREFIX_BYTES = 16;
  @Getter
  private final Map<DataWord, StorageRowCapsule> rowCache = new HashMap<>();
  @Getter
  private byte[] addrHash;
  @Getter
  private StorageRowStore store;
  @Getter
  private byte[] address;
  @Setter
  private int contractVersion;

  public Storage(byte[] address, StorageRowStore store) {
    addrHash = addrHash(address);
    this.address = address;
    this.store = store;
  }

  public Storage(Storage storage) {
    this.addrHash = storage.addrHash.clone();
    this.address = storage.getAddress().clone();
    this.store = storage.store;
    this.contractVersion = storage.contractVersion;
    storage.getRowCache().forEach((DataWord rowKey, StorageRowCapsule row) -> {
      StorageRowCapsule newRow = new StorageRowCapsule(row);
      this.rowCache.put(rowKey.clone(), newRow);
    });
  }

  private byte[] compose(byte[] key, byte[] addrHash) {
    if (contractVersion == 1) {
      key = Hash.sha3(key);
    }
    byte[] result = new byte[key.length];
    arraycopy(addrHash, 0, result, 0, PREFIX_BYTES);
    arraycopy(key, PREFIX_BYTES, result, PREFIX_BYTES, PREFIX_BYTES);
    return result;
  }

  // 32 bytes
  private static byte[] addrHash(byte[] address) {
    return Hash.sha3(address);
  }

  private static byte[] addrHash(byte[] address, byte[] unxHash) {
    if (ByteUtil.isNullOrZeroArray(unxHash)) {
      return Hash.sha3(address);
    }
    return Hash.sha3(ByteUtil.merge(address, unxHash));
  }

  public void generateAddrHash(byte[] unxId) {
    // update addressHash for create2
    addrHash = addrHash(address, unxId);
  }

  public DataWord getValue(DataWord key) {
    if (rowCache.containsKey(key)) {
      return new DataWord(rowCache.get(key).getValue());
    } else {
      StorageRowCapsule row = store.get(compose(key.getData(), addrHash));
      if (row == null || row.getInstance() == null) {
        return null;
      }
      rowCache.put(key, row);
      return new DataWord(row.getValue());
    }
  }

  public void put(DataWord key, DataWord value) {
    if (rowCache.containsKey(key)) {
      rowCache.get(key).setValue(value.getData());
    } else {
      byte[] rowKey = compose(key.getData(), addrHash);
      StorageRowCapsule row = new StorageRowCapsule(rowKey, value.getData());
      rowCache.put(key, row);
    }
  }

  public void commit() {
    rowCache.forEach((DataWord rowKey, StorageRowCapsule row) -> {
      if (row.isDirty()) {
        if (new DataWord(row.getValue()).isZero()) {
          this.store.delete(row.getRowKey());
        } else {
          this.store.put(row.getRowKey(), row);
        }
      }
    });
  }
}
