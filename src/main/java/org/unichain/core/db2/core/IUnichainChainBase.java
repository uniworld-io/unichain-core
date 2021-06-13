package org.unichain.core.db2.core;

import com.google.protobuf.InvalidProtocolBufferException;
import org.unichain.common.utils.Quitable;
import org.unichain.core.exception.BadItemException;
import org.unichain.core.exception.ItemNotFoundException;

import java.util.Map.Entry;

public interface IUnichainChainBase<T> extends Iterable<Entry<byte[], T>>, Quitable {

  /**
   * reset the database.
   */
  void reset();

  /**
   * close the database.
   */
  void close();

  void put(byte[] key, T item);

  void delete(byte[] key);

  T get(byte[] key) throws InvalidProtocolBufferException, ItemNotFoundException, BadItemException;

  T getUnchecked(byte[] key);

  boolean has(byte[] key);

  String getName();

  String getDbName();

}
