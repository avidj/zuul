package org.avidj.zuul.client;

import java.util.ArrayList;
import java.util.List;

import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;

public class AutoCloseableLock implements AutoCloseable {
  
  private final LockManager lockManager;
  private final String session;
  private final List<String> path;
  private LockScope scope;
  private int r;
  private int w;

  AutoCloseableLock(
      LockManager lockManager, String session,
      List<String> path, LockScope scope) {
    this.lockManager = lockManager;
    this.session = session;
    this.path = new ArrayList<>(path);
    this.scope = scope;
    // TODO Auto-generated constructor stub
  }

  public AutoCloseableLock readLock() {
    synchronized ( this ) {
      boolean success = lockManager.readLock(this.session, this.path, this.scope);
      r++;
    }
    return this;
  }

  public AutoCloseableLock writeLock() {
    synchronized ( this ) {
      boolean success = lockManager.writeLock(this.session, this.path, this.scope);
      w++;
    }
    return this;
  }

  public AutoCloseableLock release() throws IllegalStateException {
    synchronized ( this ) {
      boolean success = lockManager.release(this.session, this.path);
      w--;
    }
    return this;
  }

  @Override
  public void close() throws Exception {
    release();
  }
}
