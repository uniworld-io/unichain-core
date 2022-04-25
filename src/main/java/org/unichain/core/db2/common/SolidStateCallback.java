package org.unichain.core.db2.common;

//@todo refine later
public interface SolidStateCallback<T> {
    void writeState(Object key, Object value);
}
