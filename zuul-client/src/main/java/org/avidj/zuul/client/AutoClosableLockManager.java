package org.avidj.zuul.client;

import com.google.common.base.Preconditions;

import org.avidj.zuul.core.Lock;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;

import java.util.Collection;
import java.util.List;

public class AutoClosableLockManager {
  private final String session;
  private final LockManager lockManager;

  public AutoClosableLockManager(String session, LockManager lockManager) {
    Preconditions.checkNotNull(session);
    Preconditions.checkNotNull(lockManager);
    this.session = session;
    this.lockManager = lockManager;
  }

  public void setSessionTimeout(long sessionTimeout) {
    lockManager.setSessionTimeout(sessionTimeout);
  }

  public Collection<Lock> getLocks() {
    return lockManager.getLocks(session);
  }

  public AutoCloseableLock lock(List<String> path, LockScope scope) {
    return new AutoCloseableLock(this.lockManager, this.session, path, scope);
  }

  public AutoCloseableLock readLock(List<String> path, LockScope scope) {
    return lock(path, scope).readLock();
  }

  public AutoCloseableLock writeLock(List<String> path, LockScope scope) {
    return lock(path, scope).writeLock();
  }
}
