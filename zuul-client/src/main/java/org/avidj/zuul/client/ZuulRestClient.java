package org.avidj.zuul.client;

import org.avidj.zuul.core.Lock;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;

import java.util.Collection;
import java.util.List;

public class ZuulRestClient implements LockManager {

  @Override
  public void setSessionTimeout(long timeoutMillis) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Collection<Lock> getLocks(String session) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean readLock(String session, List<String> path, LockScope scope) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean writeLock(String session, List<String> path, LockScope scope) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean lock(String sessionId, List<String> path, LockType type, LockScope scope) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean multiLock(String sessionId, List<List<String>> paths, LockType type,
      LockScope scope) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean release(String session, List<String> path) throws IllegalStateException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public int release(String session, Collection<List<String>> paths) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void release(String session) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void heartbeat(String session) {
    throw new UnsupportedOperationException("not yet implemented");
  }
}
