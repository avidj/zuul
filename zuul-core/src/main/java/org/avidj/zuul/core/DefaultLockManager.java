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

import static org.avidj.zuul.core.Lock.newLock;
import static org.avidj.zuul.core.LockTreeNode.treeNode;

import org.avidj.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

/**
 * This default implementation of the {@link LockManager} interface builds a tree of lock tree nodes
 * that are traversed using lock coupling. That is, every node has its own mutex which is obtained 
 * when traversing and only released after obtaining the mutex for the nested node on a path. 
 */
public class DefaultLockManager implements LockManager {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultLockManager.class);
  private static final long DEFAULT_SESSION_TIMEOUT = 500;
  
  static final LockTreeNodeVisitor DEC_READ_COUNTS = new LockTreeNodeVisitor() {
    @Override
    public LockTreeNode visit(LockTreeNode node) {
      synchronized ( node.mutex ) {
        node.reads--;
        if ( node.subtreeEmpty() && node.parent != null ) {
          node.parent.children.remove(node.key);
          return null;
        }
        return node;
      }
    }
  };
  
  static final LockTreeNodeVisitor DEC_WRITE_COUNTS = new LockTreeNodeVisitor() {
    @Override
    public LockTreeNode visit(LockTreeNode node) {
      synchronized ( node.mutex ) {
        node.writes--;
        if ( node.subtreeEmpty() && node.parent != null ) {
          node.parent.children.remove(node.key);
          return null;
        }
        return node;
      }
    }
  };

  private final Timer sessionTimer = new Timer();
  private final Map<String, SessionTimeoutTask> timerTasks = new WeakHashMap<>();
  private final Map<String, Set<LockTreeNode>> locksBySession = new WeakHashMap<>();
  private final LockTreeNode root = treeNode(null, null);
  
  private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;

  @Override
  public void setSessionTimeout(long sessionTimeout) {
    this.sessionTimeout = sessionTimeout;
  }
  
  @Override
  public Set<Lock> getLocks(String session) {
    Set<Lock> locks = new HashSet<>();
    for ( LockTreeNode node : getLocksBySession(session) ) {
      Lock lock = node.getLock(session);
      if ( lock != null ) {
        locks.add(lock);
      }
    }
    return Collections.unmodifiableSet(locks);
  }

  private Set<LockTreeNode> getLocksBySession(String session) {
    Set<LockTreeNode> locks = locksBySession.get(session);
    if ( locks == null ) {
      return Collections.emptySet();
    }
    return locks;
  }

  @Override
  public void heartbeat(String session) {
    TimerTask task = timerTasks.get(session);
    if ( task != null ) {
      task.cancel();
    } else {
      task = new SessionTimeoutTask(this, session);
    }
    sessionTimer.schedule(task, sessionTimeout);
  }

  @Override
  public void release(String session) {
    timerTasks.remove(session);
    for ( LockTreeNode node : getLocksBySession(session) ) {
      final Lock lock = node.getLock(session);
      if ( lock != null ) {
        node.removeLock(lock);
        lock.type.decCounts(root, lock.key);
      }
    }
  }

  @Override
  public boolean release(String session, List<String> path) {
    heartbeat(session);
    final LockTreeNode node = findExistingNode(session, path);
    if ( node == null ) {
      return false;
    }
    try {
      final Lock lock = node.getLock(session);
      if ( lock == null ) {
        return false;
      }
      synchronized ( node.mutex ) {
        lock.release();
      }
      if ( lock.count == 0 ) {
        node.removeLock(lock);
        lock.type.decCounts(root, path);
      }
    } finally {
      node.mutex.unlock();
    }
    assert ( invariants(root, path) );
    return true;
  }

  @Override
  public boolean writeLock(String session, List<String> path, LockScope scope) {
    heartbeat(session);

    // traverse path described by lock
    LockTreeNode prev = null;
    LockTreeNode current = root;

    current.mutex.lock();
    if ( deepLockByOtherSession(current, session, LockType.READ) ) {
      current.mutex.unlock();
      assert ( invariants(root, path) );
      return false;
    }
    current.writes++;
    
    final int n = path.size();
    int pos;
    for ( pos = 0; pos < n; pos++ ) {
      prev = current;
      current = current.children.get(path.get(pos));
      if ( current != null ) {
        current.mutex.lock();
        prev.mutex.unlock();
        if ( deepLockByOtherSession(current, session, LockType.READ) ) {
          current.mutex.unlock();
          LockType.WRITE.decCounts(root, path.subList(0, pos));
          assert ( invariants(root, path) );
          return false;
        }
        current.writes++;
      } else {
        break;
      }
    }
    for ( ; pos < n; pos++ ) {
      current = treeNode(path.get(pos), prev);
      prev.children.put(path.get(pos), current);
      current.mutex.lock();
      prev.mutex.unlock();
      current.writes++;
      prev = current;
    }
    boolean success = setWriteLock(current, session, path, scope);
    current.mutex.unlock();
    if ( !success ) {
      LockType.WRITE.decCounts(root, path);
    }
    assert ( invariants(root, path) );
    return success;
  }

  @Override
  public boolean readLock(String session, List<String> path, LockScope scope) {
    heartbeat(session);

    // traverse path described by lock
    LockTreeNode prev = null;
    LockTreeNode current = root;

    current.mutex.lock();
    if ( deepLockByOtherSession(current, session, LockType.WRITE) ) {
      current.mutex.unlock();
      assert ( invariants(root, path) );
      return false;
    }
    current.reads++;
    
    final int n = path.size();
    int pos;
    for ( pos = 0; pos < n; pos++ ) {
      prev = current;
      current = current.children.get(path.get(pos));
      if ( current != null ) {
        current.mutex.lock();
        prev.mutex.unlock();
        if ( deepLockByOtherSession(current, session, LockType.WRITE) ) {
          current.mutex.unlock();
          LockType.READ.decCounts(root, path.subList(0, pos));
          assert ( invariants(root, path) );
          return false;
        }
        current.reads++;
      } else {
        break;
      }
    }
    for ( ; pos < n; pos++ ) {
      current = treeNode(path.get(pos), prev);
      prev.children.put(path.get(pos), current);
      current.mutex.lock();
      prev.mutex.unlock();
      current.reads++;
      prev = current;
    }
    boolean success = setReadLock(current, session, path, scope);
    current.mutex.unlock();
    if ( !success ) {
      LockType.READ.decCounts(root, path);
    }
    assert ( invariants(root, path) );
    return success;
  }

  private boolean setReadLock(
      LockTreeNode node, String session, List<String> path, LockScope scope) {
    if ( scope == LockScope.DEEP && node.writes > 0 ) {
      return false;
    }
    final Lock exclusive = node.getExclusiveLock();
    if ( exclusive != null && !exclusive.session.equals(session) ) {
      return false;
    }
    
    final Lock existing = ( exclusive != null ) ? exclusive : node.getLock(session);
    if ( existing != null ) {
      node.removeLock(existing);
      existing.type.decCounts(root, path);
    }
    
    final Lock newLock = ( existing != null ) 
        ? existing.readLock(scope) : newLock(session, path, LockType.READ, scope);
    node.addLock(newLock);
    addLockToSession(session, node);
  
    return true;
  }

  private boolean setWriteLock(
      LockTreeNode node, String session, List<String> path, LockScope scope) {
    if ( !node.canGetExclusiveLock(session) ) {
      return false;
    }
    final Lock existing = node.getLock(session);
    if ( scope == LockScope.DEEP ) {
      if (   ( existing == null && node.locksInSubtree() != 1 ) 
          || ( existing != null && node.locksInSubtree() != 2 ) ) {
        return false; // there are nested locks preventing a deep write lock
      }
    }
  
    // locking is legal and there is no existing lock for the current session
    if ( existing == null ) {
      node.addLock(newLock(session, path, LockType.WRITE, scope));
      addLockToSession(session, node);
      return true;
    }
    
    // on lock type upgrade, update counts
    Lock newLock = existing.writeLock(scope);
    existing.type.decCounts(root, path);
    node.removeLock(existing);
    node.addLock(newLock);
    addLockToSession(session, node);
  
    return true;
  }

  private LockTreeNode findExistingNode(String session, List<String> path) {
    // special case of root lock
    if ( path.isEmpty() ) {
      root.mutex.lock();
      return root;
    }
    
    // traverse path described by lock
    LockTreeNode current = root;
    LockTreeNode prev = null;
    current.mutex.lock();
    for ( int pos = 0, n = path.size(); pos < n; pos++ ) {
      prev = current;
      current = current.children.get(path.get(pos));
      if ( current == null ) {
        prev.mutex.unlock();
        return null;
      }
      current.mutex.lock();
      prev.mutex.unlock();
    }
    return current;
  }

  private void addLockToSession(String session, LockTreeNode node) {
    Set<LockTreeNode> locks = locksBySession.get(session);
    if ( locks == null ) {
      locks = new HashSet<>();
      locksBySession.put(session, locks);
    }
    locks.add(node);
  }

  static void visit(LockTreeNode root, List<String> path, LockTreeNodeVisitor visitor) {
    LockTreeNode prev = null;
    LockTreeNode current = root;
    
    current.mutex.lock();
    visitor.visit(current);
    
    for ( int i = 0, n = path.size(); i < n && current != null ; i++ ) {
      prev = current;
      current = current.children.get(path.get(i));
      if ( current != null ) {
        current.mutex.lock();
        current = visitor.visit(current);
      }
      prev.mutex.unlock();
    }
    if ( current != null ) {
      current.mutex.unlock();
    }
  }

  private static boolean currentThreadHoldsNoLocksOnPath(LockTreeNode root, List<String> path) {
    LockTreeNode current = root;
    if ( current.mutex.isHeldByCurrentThread() ) {
      return false;
    }
    for ( int i = 0, n = path.size(); i < n; i++ ) {
      String step = path.get(i);
      current = current.children.get(step);
      if ( current == null ) {
        return true;
      } else if ( current.mutex.isHeldByCurrentThread() ) {
        LOG.error("Current thread holds a lock on mutex of node: {}", 
            Strings.join(path.subList(0, i + 1)));
        return false;
      }
    }
    return true;
  }

  private static boolean invariants(LockTreeNode root, List<String> path) {
    return currentThreadHoldsNoLocksOnPath(root, path)
        && lockCountsAreCorrect(root);
  }

  private static boolean deepLockByOtherSession(
      LockTreeNode current, String session, LockType type) {
    
    switch ( type ) {
      case READ:
        for ( Lock lock : current.getDeepLocks() ) {
          if ( !lock.session.equals(session) ) {
            return true;
          }
        }
        return false;
      case WRITE:
        return current.getExclusiveLock() != null
            && current.getExclusiveLock().scope == LockScope.DEEP 
            && !current.getExclusiveLock().session.equals(session);
      default: 
        throw new IllegalArgumentException("Unknown lock type: " + type);
    }
  }

  private static boolean lockCountsAreCorrect(LockTreeNode root) {    
    return readCount(root) == root.reads
        && writeCount(root) == root.writes;
  }

  private static int readCount(LockTreeNode node) {
    int count = node.getSharedLocks().size();
    for ( LockTreeNode child : node.children.values() ) {
      count += readCount(child);
    }
    assert ( count == node.reads ) : 
      String.format("node = %1$s, reads = %2$d, actual = %3$d", 
          Strings.join(pathTo(node)), node.reads, count);
    return count;
  }

  private static int writeCount(LockTreeNode node) {
    assert ( node != null );
    int count = ( node.hasExclusiveLock() ) ? 1 : 0;
    for ( LockTreeNode child : node.children.values() ) {
      count += writeCount(child);
    }
    assert ( count == node.writes ) : 
      String.format("node = %1$s, writes = %2$d, actual = %3$d", 
          Strings.join(pathTo(node)), node.writes, count);
    return count;
  }

  private static List<String> pathTo(LockTreeNode node) {
    List<String> path = new LinkedList<>();
    if ( node != null ) {
      path.add(0, node.key);
      path.addAll(0, pathTo(node.parent));
    }
    return path;
  }

  interface LockTreeNodeVisitor {
    LockTreeNode visit(LockTreeNode node);
  }
}
