package org.avidj.zuul.client;

import org.avidj.zuul.core.DefaultEmbeddedLockManager;
import org.avidj.zuul.core.LockManager;

public class DefaultEmbeddedAutoClosableLockManagerTest extends AutoClosableLockTest {

  @Override
  protected LockManager lockManager() {
    return new DefaultEmbeddedLockManager();
  }
}
