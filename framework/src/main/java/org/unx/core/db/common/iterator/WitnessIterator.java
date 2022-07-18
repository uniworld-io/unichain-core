package org.unx.core.db.common.iterator;

import java.util.Iterator;
import java.util.Map.Entry;
import org.unx.core.capsule.WitnessCapsule;

public class WitnessIterator extends AbstractIterator<WitnessCapsule> {

  public WitnessIterator(Iterator<Entry<byte[], byte[]>> iterator) {
    super(iterator);
  }

  @Override
  protected WitnessCapsule of(byte[] value) {
    return new WitnessCapsule(value);
  }
}
