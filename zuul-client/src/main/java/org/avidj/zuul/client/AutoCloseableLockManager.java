package org.avidj.zuul.client;

/*
 * #%L
 * zuul-client
 * %%
 * Copyright (C) 2015 David Kensche
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.base.Preconditions;

import org.avidj.zuul.core.Lock;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;

import java.util.Collection;
import java.util.List;

/**
 * A decorator around implementations of {@link org.avidj.zuul.core.LockManager} that creates 
 * autocloseable locks to be used in try-with-resources blocks. The backing lock manager could be
 * an embedded or a remote lock manager.
 */
public class AutoCloseableLockManager {
  private final String session;
  private final LockManager lockManager;

  /**
   * Create a new AutoCloseableLockManager.
   * @param session the session to create locks for
   * @param lockManager the lock manager that manages the locks
   */
  public AutoCloseableLockManager(String session, LockManager lockManager) {
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

  /**
   * Obtain an auto closeable lock of the given type and scope for the given path.
   * @param path the path to lock
   * @param lockType the type of lock to obtain
   * @param scope the scope of the desired lock
   * @return the requested lock
   */
  public AutoCloseableLock lock(List<String> path, LockType lockType, LockScope scope) {
    @SuppressWarnings("resource")
    AutoCloseableLock lock = new AutoCloseableLock(this.lockManager, this.session, path, scope);
    switch ( lockType ) {
      case READ: 
        return lock.readLock();
      case WRITE: 
        return lock.writeLock();
      default:
        throw new IllegalArgumentException("Unsupported lock type: " + lockType);
    }
  }

  public AutoCloseableLock readLock(List<String> path) {
    return lock(path, LockType.READ, LockScope.SHALLOW);
  }

  public AutoCloseableLock readLock(List<String> path, LockScope scope) {
    return lock(path, LockType.READ, scope);
  }

  public AutoCloseableLock writeLock(List<String> path) {
    return lock(path, LockType.WRITE, LockScope.SHALLOW);
  }

  public AutoCloseableLock writeLock(List<String> path, LockScope scope) {
    return lock(path, LockType.WRITE, scope);
  }
}
