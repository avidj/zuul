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

import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * A type of lock that can be used in try-with-resources blocks. These locks are created using an
 * {@link org.avidj.zuul.client.AutoCloseableLockManager} which may be backed by an embedded or a
 * remote lock manager implementation.
 */
public class AutoCloseableLock implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(AutoCloseableLock.class);
  private final LockManager lockManager;
  private final String session;
  private final List<String> path;
  private LockScope scope;
  private int read;
  private int write;
  private final Stack<LockOp> locks = new Stack<>();
  
  private enum LockOp {
    READ(LockType.READ, 
        l -> l.lockManager.release(l.session, l.path),   
        l -> l.read++, 
        l -> l.read--),
    WRITE(LockType.WRITE, 
        l -> l.lockManager.release(l.session, l.path), 
        l -> l.write++, 
        l -> l.write--),
    UPSCOPE(null, 
        l -> {
          // TODO: this increments the lock count and therefore does not yet work
          l.lockManager.lock(l.session, l.path, l.type(), LockScope.SHALLOW);
          return true;
        },
        l -> { }, 
        l -> l.scope = LockScope.SHALLOW ),
    NO_OP(null, 
        l -> true, 
        l -> { }, 
        l -> { });
    
    private final LockType lockType;
    private final Revert revert;
    private final OnSuccess onRelease;
    private final OnSuccess onAcquire;
    
    LockOp(LockType lockType, Revert revert, OnSuccess onAcquire, OnSuccess onRelease) {
      this.revert = revert;
      this.lockType = lockType;
      this.onAcquire = onAcquire;
      this.onRelease = onRelease;
    }
   
    void onAcquire(AutoCloseableLock lock) {
      onAcquire.onSuccess(lock);
    }

    void onRelease(AutoCloseableLock lock) {
      onRelease.onSuccess(lock);
    }

    boolean revert(AutoCloseableLock lock) {
      return revert.revert(lock);
    }
  }
  
  private interface Revert {
    boolean revert(AutoCloseableLock lock);
  }
  
  @FunctionalInterface
  private interface OnSuccess {
    void onSuccess(AutoCloseableLock lock);
  }

  AutoCloseableLock(LockManager lockManager, String session, List<String> path, LockScope scope) {
    this.lockManager = lockManager;
    this.session = session;
    this.path = Collections.unmodifiableList(new ArrayList<>(path));
    this.scope = scope;
  }
  
  /**
   * Returns the path represented by this lock object. This path is locked or released by the 
   * operations in this class.
   * @return the path represented by this lock
   */
  public List<String> path() {
    return path;
  }

  /**
   * Acquire a shared / read lock on the resource described by this lock's path.
   * This operation blocks until it succeeds.
   * @return this
   */
  public AutoCloseableLock readLock() {
    synchronized ( this ) {
      boolean success;
      LockOp op = null;
      if ( this.isWriteLocked() ) {
        // if this is already write locked a read lock would effectively downgrade the lock
        success = lockManager.writeLock(this.session, this.path, this.scope);
        op = LockOp.WRITE;
      } else {
        success = lockManager.readLock(this.session, this.path, this.scope);
        op = LockOp.READ;
      }
      if ( success ) {
        op.onAcquire(this);
        locks.push(op);
      } else {
        LOG.warn("Create blocking lock operations so that autoclosable locks can be used.");
      }
    }
    return this;
  }

  /**
   * Acquire an exclusive / write lock on the resource described by this lock's path.
   * This operation blocks until it succeeds.
   * @return this
   */
  public AutoCloseableLock writeLock() {
    synchronized ( this ) {
      boolean success = lockManager.writeLock(this.session, this.path, this.scope);
      if ( success ) {
        LockOp op = LockOp.WRITE;
        op.onAcquire(this);
        locks.push(op);
      } else {
        LOG.warn("Create blocking lock operations so that autoclosable locks can be used.");
      }
    }
    return this;
  }

  /**
   * Releases this lock. Recall that locks are reentrant, so to actually release a lock this method
   * has to be called as often as shared or exclusive locks on this path have been obtained.
   * @return this
   * @throws IllegalStateException if the lock is not being held
   */
  public AutoCloseableLock release() throws IllegalStateException {
    if ( locks.isEmpty() ) {
      return this;
    }
    synchronized ( this ) {
      LockOp lockOp = locks.peek();
      boolean success = lockOp.revert(this);
      if ( success ) {
        lockOp.onRelease(this);
        locks.pop();
      } else {
        LOG.warn("Create blocking lock operations so that autoclosable locks can be used.");
      }
    }
    return this;
  }

  @Override
  public void close() {
    release();
  }

  public boolean isReadLocked() {
    return !locks.isEmpty();
  }

  public boolean isWriteLocked() {
    return write > 0;
  }

  public boolean isDeepLocked() {
    return scope == LockScope.DEEP;
  }
  
  /**
   * Extend the scope of this lock to a deep lock (if it is not yet). 
   * This operation blocks until it succeeds.
   * @return this
   */
  public AutoCloseableLock upScope() {
    final LockType type = type();
    if ( type == null ) { 
      throw new IllegalStateException("Not locked at all " + this);
    }
    if ( this.scope == LockScope.DEEP ) {
      locks.push(LockOp.NO_OP);
    } else {
      if ( lockManager.lock(session, path, type, LockScope.DEEP) ) {
        this.scope = LockScope.DEEP;
        LockOp op = LockOp.UPSCOPE;
        op.onAcquire(this);
        locks.push(op);
      } else {
        locks.push(LockOp.NO_OP);
        LOG.warn("Create blocking lock operations so that autoclosable locks can be used.");
      }
    }
    return this;
  }

  private LockType type() {
    if ( write > 0 ) {
      return LockType.WRITE;
    } else if ( read > 0 ) {
      return LockType.READ;
    }
    return null;
  }
}
