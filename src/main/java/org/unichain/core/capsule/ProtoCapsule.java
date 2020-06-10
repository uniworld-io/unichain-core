package org.unichain.core.capsule;

public interface ProtoCapsule<T> {

  byte[] getData();

  T getInstance();
}
