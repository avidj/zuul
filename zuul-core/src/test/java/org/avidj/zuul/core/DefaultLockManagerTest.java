package org.avidj.zuul.core;

public class DefaultLockManagerTest extends LockManagerTest {

  @Override
  protected LockManager lockManager() {
    return new DefaultLockManager();
  }
}
