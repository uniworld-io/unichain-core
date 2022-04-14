package org.unichain.core.db;

import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.unichain.core.exception.BadItemException;
import org.unichain.core.exception.ItemNotFoundException;

import java.util.Spliterator;
import java.util.function.Consumer;

@Component
public class CheckPointDb extends UnichainDatabase<byte[]> {

  @Autowired
  public CheckPointDb(ApplicationContext ctx) {
    super("tmp");
  }

  @Override
  public void put(byte[] key, byte[] item) {
  }

  @Override
  public void delete(byte[] key) {

  }

  @Override
  public byte[] get(byte[] key) throws InvalidProtocolBufferException, ItemNotFoundException, BadItemException {
    return null;
  }

  @Override
  public boolean has(byte[] key) {
    return false;
  }

  @Override
  public void forEach(Consumer action) {
  }

  @Override
  public Spliterator spliterator() {
    return null;
  }
}