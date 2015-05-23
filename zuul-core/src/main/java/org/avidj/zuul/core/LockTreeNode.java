package org.avidj.zuul.core;

/*
 * #%L
 * zuul-core
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

import org.avidj.util.Strings;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

class LockTreeNode {
  // Mutex for lock coupling.
  final ReentrantLock mutex = new ReentrantLock();
  
  final LockTreeNode parent;

  final String key;

  private final Map<String, Lock> sharedLocks = new HashMap<>();
  private final Set<Lock> deepLocks = new HashSet<>();

  private Lock exclusiveLock;

  // The number of nested read locks.
  int reads = 0;
  
  // The number of nested write locks.
  int writes = 0;  
  
  // TODO: use a patricia tree instead?
  final Map<Object, LockTreeNode> children = new HashMap<>();
  
  static LockTreeNode treeNode(String key, LockTreeNode parent) {
    assert ( key == null || parent != null );
    return new LockTreeNode(key, parent);
  }
  
  private LockTreeNode(String key, LockTreeNode parent) {
    this.key = (key != null ) ? key.intern() : null;
    this.parent = parent;
  }
  
  @Override
  public String toString() {
    return new StringBuilder("Node(")
        .append("key = ").append(key)
        .append(", writes = ").append(writes)
        .append(", reads = ").append(reads)
        .append(", exclusive = ").append(exclusiveLock)
        .append(", shared = {").append(Strings.join(sharedLocks.values())).append("}")
        .append(")")
        .toString();
  }

  boolean hasExclusiveLock() {
    return exclusiveLock != null;
  }

  public Lock getExclusiveLock() {
    return exclusiveLock;
  }

  public Set<Lock> getSharedLocks() {
    return Collections.unmodifiableSet(new HashSet<>(sharedLocks.values()));
  }

  public Lock getLock(String session) {
    if ( exclusiveLock != null ) {
      if ( exclusiveLock.session.equals(session) ) {
        return exclusiveLock;
      }
      return null;
    }
    return sharedLocks.get(session);
  }

  public void addLock(Lock lock) {
    if ( lock.type == LockType.WRITE ) {
      Preconditions.checkState(exclusiveLock == null, "exclusive lock already exists");
      exclusiveLock = lock;
    } else {
      assert ( !sharedLocks.containsKey(lock.session) );
      sharedLocks.put(lock.session, lock);
    }
    if ( lock.scope == LockScope.DEEP ) {
      deepLocks .add(lock);
    }
    assert ( exclusiveLock == null || exclusiveLock.type == LockType.WRITE );
    assert ( locksCompatible() );
  }

  public void removeLock(Lock lock) {
    Preconditions.checkArgument(lock != null, "lock must not be null");
    if ( lock.scope == LockScope.DEEP ) {
      deepLocks.remove(lock);
    }
    if ( lock.equals(exclusiveLock) ) {
      exclusiveLock = null;
    } else {
      sharedLocks.values().remove(lock);
    }
    assert ( locksCompatible() );
  }

  public Set<Lock> getDeepLocks() {
    return Collections.unmodifiableSet(deepLocks);
  }
  
  private boolean locksCompatible() {
    return ( exclusiveLock == null || sharedLocks.isEmpty() );
  }

  public boolean canGetExclusiveLock(String session) {
    // there already is an exclusive lock ...
    if ( exclusiveLock != null ) {
      // ... and it is held by the session
      return exclusiveLock.session.equals(session);
    }
    // or there are no shared locks held by other sessions
    return ( sharedLocks.isEmpty() )
        || ( sharedLocks.size() == 1 && sharedLocks.get(session) != null );
  }

  int locksInSubtree() {
    return ( reads + writes );
  }
  
  boolean subtreeEmpty() {
    return ( locksInSubtree() == 0 );
  }
}
