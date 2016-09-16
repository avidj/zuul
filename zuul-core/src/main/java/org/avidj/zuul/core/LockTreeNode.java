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

/**
 * A node in a lock tree. A node is associated with a final key, which is the component of the lock path leading to that
 * node. 
 */
class LockTreeNode {
  private static final Logger LOG = LoggerFactory.getLogger(LockTreeNode.class);
  // Mutex for lock coupling.
  private final ReentrantLock mutex = new ReentrantLock();
  
  final LockTreeNode parent;

  final String key;

  // The exclusive lock on this node, or null. If there is an exclusive lock then there can be no shared locks.
  private Lock exclusiveLock;

  // A possibly empty map from session keys to shared locks. If not empty, then there can't be an exclusive lock.
  private final Map<String, Lock> sharedLocks = Collections.synchronizedMap(new HashMap<>());

  // The set of deep locks on this node.
  private final Set<Lock> deepLocks = Collections.synchronizedSet(new HashSet<>());

  // The number nested intention shared locks.
  int is = 0;

  // The number of nested intention exclusive locks.
  int ix = 0; 
  
  // The number of shared intention exclusive locks.
  int six = 0;
  
  // The number of nested shared locks.
  int shared = 0;
  
  // The number of nested exclusive locks.
  int exclusive = 0;
  
  // TODO: use a patricia tree instead?
  final Map<Object, LockTreeNode> children = new HashMap<>();

  /* Create a new tree node with the given parent and key component. */
  static LockTreeNode treeNode(String key, LockTreeNode parent) {
    assert ( key == null || parent != null );
    return new LockTreeNode(key, parent);
  }
  
  private LockTreeNode(String key, LockTreeNode parent) {
    this.key = (key != null ) ? key.intern() : null;
    this.parent = parent;
  }
  
  /**
   * @param id the id of the child to retrieve
   * @return the child if it exists
   */
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

  /** 
   * @return {@code true} if this node has an exclusive lock 
   */
  boolean hasExclusiveLock() {
    return exclusiveLock != null;
  }

  /**
   * @return the exclusive lock on this node if it exists 
   */
  Lock getExclusiveLock() {
    return exclusiveLock;
  }

  /** 
   * @return the possibly empty (immutable) set of shared locks on this node 
   */
  Set<Lock> getSharedLocks() {
    return Collections.unmodifiableSet(new HashSet<>(sharedLocks.values()));
  }

  /** 
   * @return the lock on this node owned by the given session, or {@code null} if none such exists 
   */
  Lock getLock(String session) {
    if ( exclusiveLock != null ) {
      if ( exclusiveLock.session.equals(session) ) {
        return exclusiveLock;
      }
      return null;
    }
    return sharedLocks.get(session);
  }

  /**
   * Add the given lock to this node.
   * @param lock the lock to add
   * @throws IllegalStateException if the lock cannot be added, e.g., it's exclusive but there are already shared locks
   */
  void addLock(Lock lock) throws IllegalStateException {
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
