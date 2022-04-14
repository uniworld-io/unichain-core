package org.unichain.core.db2.common;

import org.unichain.core.db.common.WrappedByteArray;
import org.unichain.core.exception.ItemNotFoundException;

import java.util.Map;
import java.util.Set;

public interface IRevokingDB extends Iterable<Map.Entry<byte[], byte[]>> {

  void put(byte[] key, byte[] newValue);

  void delete(byte[] key);

  boolean has(byte[] key);

  byte[] get(byte[] key) throws ItemNotFoundException;

  byte[] getUnchecked(byte[] key);

  void close();

  void reset();

  void setSnapshotMode(boolean snapshotMode);

  // for blockstore
  Set<byte[]> getLatestValues(long limit);

  // for blockstore
  Set<byte[]> getNextValues(byte[] key, long limit);
  
  // for deferTransaction
  Set<byte[]> getPrevValues(byte[] key, long limit);

  Map<WrappedByteArray, WrappedByteArray> getAllValues();
}
