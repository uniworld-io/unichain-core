package org.unx.core.db2.core;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map.Entry;
import org.unx.common.utils.Quitable;
import org.unx.core.exception.BadItemException;
import org.unx.core.exception.ItemNotFoundException;

public interface IUnxChainBase<T> extends Iterable<Entry<byte[], T>>, Quitable {

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

  T getFromRoot(byte[] key) throws InvalidProtocolBufferException, ItemNotFoundException,
      BadItemException;

  T getUnchecked(byte[] key);

  boolean has(byte[] key);

  boolean isNotEmpty();

  String getName();

  String getDbName();

}
