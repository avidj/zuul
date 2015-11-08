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

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class AutoCloseableLock implements AutoCloseable {
  
  private final LockManager lockManager;
  private final String session;
  private final List<String> path;
  private LockScope scope;
  private int r;
  private int w;
  private final Stack<LockOp> locks = new Stack<>();
  
  private enum LockOp {
    READ(LockType.READ, l -> l.lockManager.release(l.session, l.path),   l -> l.r++, l -> l.r--),
    WRITE(LockType.WRITE, l -> l.lockManager.release(l.session, l.path), l -> l.w++, l -> l.w--),
    UPSCOPE(null, l -> true, l -> { }, l -> { l.scope = LockScope.SHALLOW; }),
    NO_OP(null, l -> true, l -> { }, l -> { });
    
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
   
    void onAcquire(AutoCloseableLock l) {
      onAcquire.onSuccess(l);
    }

    void onRelease(AutoCloseableLock l) {
      onRelease.onSuccess(l);
    }

    boolean revert(AutoCloseableLock l) {
      return revert.revert(l);
    }
  }
  
  private interface Revert {
    boolean revert(AutoCloseableLock l);
  }
  
  @FunctionalInterface
  private interface OnSuccess {
    void onSuccess(AutoCloseableLock l);
  }

  AutoCloseableLock(LockManager lockManager, String session, List<String> path, LockScope scope) {
    this.lockManager = lockManager;
    this.session = session;
    this.path = new ArrayList<>(path);
    this.scope = scope;
  }

  public AutoCloseableLock readLock() {
    synchronized ( this ) {
      if ( this.isWriteLocked() ) {
        LockOp op = LockOp.NO_OP;
        op.onAcquire(this);
        locks.push(op);
      } else {
        boolean success = lockManager.readLock(this.session, this.path, this.scope);
        if ( success ) {
          LockOp op = LockOp.READ;
          op.onAcquire(this);
          locks.push(op);
        }
      }
    }
    return this;
  }

  public AutoCloseableLock writeLock() {
    synchronized ( this ) {
      boolean success = lockManager.writeLock(this.session, this.path, this.scope);
      if ( success ) {
        LockOp op = LockOp.WRITE;
        op.onAcquire(this);
        locks.push(op);
      }
    }
    return this;
  }

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
    return w > 0;
  }

  public boolean isDeepLocked() {
    return scope == LockScope.DEEP;
  }

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
      }
    }
    return this;
  }

  private LockType type() {
    if ( w > 0 ) {
      return LockType.WRITE;
    } else if ( r > 0 ) {
      return LockType.READ;
    }
    return null;
  }
}
