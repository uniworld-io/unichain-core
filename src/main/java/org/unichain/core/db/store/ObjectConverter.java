package org.unichain.core.db.store;

import com.google.gson.JsonObject;
import org.unichain.core.capsule.ProtoCapsule;

public interface ObjectConverter {
    JsonObject toJson(ProtoCapsule capsule);
}
