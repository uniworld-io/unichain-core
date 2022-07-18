package org.unx.core.services.ratelimiter.adapter;

import org.unx.core.services.ratelimiter.RuntimeData;

public interface IRateLimiter {

  boolean acquire(RuntimeData data);

}
