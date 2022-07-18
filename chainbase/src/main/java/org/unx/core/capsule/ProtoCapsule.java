package org.unx.core.capsule;

public interface ProtoCapsule<T> {

  byte[] getData();

  T getInstance();
}
