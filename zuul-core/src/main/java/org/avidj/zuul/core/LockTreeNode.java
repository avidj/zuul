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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class LockTreeNode {
  private static final Logger LOG = LoggerFactory.getLogger(LockTreeNode.class);
  // Mutex for lock coupling.
  private final ReentrantLock mutex = new ReentrantLock();
  
  final LockTreeNode parent;

  final String key;

  private final Map<String, Lock> sharedLocks = Collections.synchronizedMap(new HashMap<>());
  private final Set<Lock> deepLocks = Collections.synchronizedSet(new HashSet<>());

  private Lock exclusiveLock;

  int is = 0;
  
  // The number of nested shared locks.
  int shared = 0;
  
  int ix = 0; 
  
  // The number of nested exclusive locks.
  int exclusive = 0;
  
  int six = 0;
  
  // TODO: use a patricia tree instead?
  final Map<Object, LockTreeNode> children = new HashMap<>();
  
  static LockTreeNode treeNode(String key, LockTreeNode parent) {
    assert ( key == null || parent != null );
    return new LockTreeNode(key, parent);
  }
  
  LockTreeNode(String key, LockTreeNode parent) {
    this.key = (key != null ) ? key.intern() : null;
    this.parent = parent;
  }
  
  public LockTreeNode getChild(String id) {
    return children.get(id);
  }
  
  @Override
  public String toString() {
    return new StringBuilder("Node(")
        .append("key = ").append(key)
        .append(", writes = ").append(exclusive)
        .append(", reads = ").append(shared)
        .append(", exclusive = ").append(exclusiveLock)
        .append(", shared = {").append(Strings.join(sharedLocks.values())).append("}")
        .append(")")
        .toString();
  }

  boolean hasExclusiveLock() {
    return exclusiveLock != null;
  }

  Lock getExclusiveLock() {
    return exclusiveLock;
  }

  Set<Lock> getSharedLocks() {
    return Collections.unmodifiableSet(new HashSet<>(sharedLocks.values()));
  }

  Lock getLock(String session) {
    if ( exclusiveLock != null ) {
      if ( exclusiveLock.session.equals(session) ) {
        return exclusiveLock;
      }
      return null;
    }
    return sharedLocks.get(session);
  }

  void addLock(Lock lock) {
    if ( lock.type == LockType.WRITE ) {
      Preconditions.checkState(exclusiveLock == null, "exclusive lock already exists");
      exclusiveLock = lock;
    } else {
      assert ( !sharedLocks.containsKey(lock.session) ) : "session already has a lock on " + this;
      sharedLocks.put(lock.session, lock);
    }
    if ( lock.scope == LockScope.DEEP ) {
      deepLocks.add(lock);
    }
    assert ( exclusiveLock == null || exclusiveLock.type == LockType.WRITE );
    assert ( locksCompatible() );
  }

  void removeLock(Lock lock) {
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

  Set<Lock> getDeepLocks() {
    return Collections.unmodifiableSet(deepLocks);
  }
  
  private boolean locksCompatible() {
    return ( exclusiveLock == null || sharedLocks.isEmpty() );
  }

  boolean canGetExclusiveLock(String session) {
    // there already is an exclusive lock ...
    if ( exclusiveLock != null ) {
      // ... and it is held by the session
      return exclusiveLock.session.equals(session);
    }
    // or there are no shared locks held by other sessions
    return ( sharedLocks.isEmpty() )
        || ( sharedLocks.size() == 1 && sharedLocks.get(session) != null );
  }

  // the number of shared and exclusive locks in the subtree rooted at this lock node
  int locksInSubtree() {
    return ( shared + exclusive );
  }
  
  // true, iff there are no locks in the subtree rooted at this lock node
  boolean subtreeEmpty() {
    return ( locksInSubtree() == 0 );
  }

  // obtain the java-level lock on this lock node
  void lock() {
    LOG.trace("try lock {}", key != null ? key : "root");
    mutex.lock();
    LOG.trace("locked {}", key != null ? key : "root");
  }

  // release the java-level lock on this lock node
  void unlock() {
    mutex.unlock();
    LOG.trace("unlocked {}", key != null ? key : "root");
  }

  // true iff the current thread holds the java-level lock on this lock node
  boolean isHeldByCurrentThread() {
    return mutex.isHeldByCurrentThread();
  }
}
