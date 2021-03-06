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
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

/**
 * This default implementation of the {@link LockManager} interface builds a tree of lock tree nodes
 * that are traversed using lock coupling. That is, every node has its own mutex which is obtained 
 * when traversing and only released after obtaining the mutex for the nested node on a path. 
 */
@Component
public class DefaultEmbeddedLockManager implements EmbeddedLockManager {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultEmbeddedLockManager.class);
  private static final long DEFAULT_SESSION_TIMEOUT = 50000;
 
  private final Map<String, Session> sessions = new HashMap<>();
  private final Timer sessionTimer = new Timer();
  private final LockTreeNode root = treeNode(null, null);
  
  private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;
  
  @Override
  public Session getSession(String id) {
    Session session = sessions.get(id);
    if ( session != null ) {
      session.cancelTimeout();
    }
    if ( session == null ) {
      session = new Session(this, id);
      sessions.put(id, session);
    }
    synchronized ( this ) {
      sessionTimer.schedule(session.newTimeoutTask(), sessionTimeout);
    }
    return session;
  }

  @Override
  public void setSessionTimeout(long sessionTimeout) {
    synchronized ( this ) {
      this.sessionTimeout = sessionTimeout;
    }
  }

  @Override
  public LockTreeNode getRoot() {
    return root;
  }

  @Override
  public Set<Lock> getLocks(String id) {
    final Session session = sessions.get(id);
    if ( session == null ) {
      return Collections.emptySet();
    }
    final Set<Lock> locks = new HashSet<>();
    for ( LockTreeNode node : session.getLocks() ) {
      Lock lock = node.getLock(id);
      if ( lock != null ) {
        locks.add(lock);
      }
    }
    return Collections.unmodifiableSet(locks);
  }

  @Override
  public void heartbeat(String id) {
    Session session = sessions.get(id);
    if ( session != null ) {
      session.cancelTimeout();
      sessionTimer.schedule(session.newTimeoutTask(), sessionTimeout);
    }
  }

  @Override
  public void release(String id) {
    final Session session = sessions.get(id);
    if ( session != null ) {
      sessions.remove(id);
      session.cancelTimeout();
      for ( LockTreeNode node : session.getLocks() ) {
        final Lock lock = node.getLock(session.id);
        if ( lock != null ) {
          node.removeLock(lock);
          lock.type.decLock(root, lock.key);
        }
      }
    }
  }

  @Override
  public boolean release(String id, List<String> path) {
    final Session session = sessions.get(id);
    
    final LockTreeNode node = findExistingNode(id, path);
    if ( node == null ) {
      return false;
    }
    try {
      final Lock lock = node.getLock(id);
      if ( lock == null ) {
        return false;
      }
      synchronized ( node ) {
        lock.release();
      }
      if ( lock.count == 0 ) {
        node.removeLock(lock);
        session.removeLock(lock.key);
        lock.type.decLock(root, path);
      }
    } finally {
      node.unlock();
    }
    assert ( invariants(root, path) );
    return true;
  }

  @Override
  public boolean writeLock(String id, List<String> path, LockScope scope) {
    final Session session = getSession(id);

    // traverse path described by lock
    LockTreeNode prev = null;
    LockTreeNode current = root;

    current.lock();
    if ( deepLockedByOther(current, id, LockType.READ) ) {
      current.unlock();
      assert ( invariants(root, path) );
      return false;
    }
    synchronized ( current ) {
      current.exclusive++;
//      current.ix++;
    }
    
    final int n = path.size();
    int pos;
    for ( pos = 0; pos < n; pos++ ) {
      prev = current;
      current = current.children.get(path.get(pos));
      if ( current != null ) {
        current.lock();
        prev.unlock();
        if ( deepLockedByOther(current, id, LockType.READ) ) {
          current.unlock();
          LockType.WRITE.decLock(root, path.subList(0, pos));
          assert ( invariants(root, path) );
          return false;
        }
        synchronized ( current ) {
          current.exclusive++;
//          current.ix++;
        }
      } else {
        break;
      }
    }
    for ( ; pos < n; pos++ ) {
      current = treeNode(path.get(pos), prev);
      current.lock();
      prev.unlock();
      prev.children.put(path.get(pos), current);
      synchronized ( current ) {
        current.exclusive++;
//        current.ix++;
      }
      prev = current;
    }
    boolean success = setWriteLock(current, session, path, scope);
    current.unlock();
    if ( !success ) {
      LockType.WRITE.decLock(root, path);
//    } else {
//      LockType.WRITE.convertCounts(root, path);
    }
    assert ( invariants(root, path) );
    return success;
  }

  @Override
  public boolean readLock(String id, List<String> path, LockScope scope) {
    final Session session = getSession(id);

    // traverse path described by lock
    LockTreeNode prev = null;
    LockTreeNode current = root;

    current.lock();
    if ( deepLockedByOther(current, id, LockType.WRITE) ) {
      current.unlock();
      assert ( invariants(root, path) );
      return false;
    }
    synchronized ( current ) {
      current.shared++;
//      current.is++;
    }
    
    final int n = path.size();
    int pos;
    
    // traverse through path from root down into tree
    for ( pos = 0; pos < n; pos++ ) {
      prev = current;
      current = current.children.get(path.get(pos));
      if ( current != null ) {
        current.lock();
        prev.unlock();
        if ( deepLockedByOther(current, id, LockType.WRITE) ) {
          current.unlock();
          LockType.READ.decLock(root, path.subList(0, pos));
          assert ( invariants(root, path) );
          return false;
        }
        synchronized ( current ) {
          current.shared++;
//          current.is++;
        }
      } else {
        break;
      }
    }
    for ( ; pos < n; pos++ ) {
      current = treeNode(path.get(pos), prev);
      current.lock(); // must lock before adding to children list
      prev.unlock();
      prev.children.put(path.get(pos), current);
      synchronized ( current ) {
        current.shared++;
//        current.is++;
      }
      prev = current;
    }
    LOG.trace("try read lock");
    boolean success = setReadLock(current, session, path, scope);
    current.unlock();
    if ( !success ) {
      LockType.READ.decLock(root, path);
//    } else {
//      LockType.READ.convertCounts(root, path);
    }
    LOG.trace("check invariants");
    assert ( invariants(root, path) );
    return success;
  }
  
  @Override
  public boolean upScope(String id, List<String> path, LockType type) {
    return updateScope(id, path, type, LockScope.DEEP);
  }

  @Override
  public boolean downScope(String id, List<String> path, LockType type, LockScope shallow) {
    return updateScope(id, path, type, LockScope.SHALLOW);
  }

  public boolean updateScope(String id, List<String> path, LockType type, LockScope scope) {
    final Session session = getSession(id);

    // traverse path described by lock
    LockTreeNode prev = null;
    LockTreeNode current = root;

    current.lock();
    if ( deepLockedByOther(current, id, LockType.READ) ) {
      throw new IllegalStateException("deep locked by other session");
    }
    
    final int n = path.size();
    int pos;
    for ( pos = 0; pos < n; pos++ ) {
      prev = current;
      current = current.children.get(path.get(pos));
      if ( current != null ) {
        current.lock();
        prev.unlock();
        if ( deepLockedByOther(current, id, LockType.READ) ) {
          throw new IllegalStateException("deep locked by other session");
        }
      } else {
        throw new IllegalStateException("lock does not exist");
      }
    }
    boolean success = setLockScope(current, session, path, scope);
    current.unlock();
    return success;
  }

  private boolean setReadLock(
      LockTreeNode node, Session session, List<String> path, LockScope scope) {
    synchronized ( node ) {
      // TODO: what if current session has the deep locks?
      if ( scope == LockScope.DEEP && node.exclusive > 0 ) { 
        return false;
      }
    }
    final Lock exclusive = node.getExclusiveLock();
    if ( exclusive != null && !exclusive.session.equals(session.id) ) {
      return false;
    }
    
    final Lock existing = ( exclusive != null ) ? exclusive : node.getLock(session.id);
    if ( existing != null ) {
      node.removeLock(existing);
      node.unlock();
      existing.type.decLock(root, path);
      node.lock();
    }
    
    final Lock newLock = ( existing != null ) 
        ? existing.readLock(scope) : newLock(session.id, path, LockType.READ, scope);
    node.addLock(newLock);
    session.addLock(node);
  
    return true;
  }

  private boolean setLockScope(
      LockTreeNode node, Session session, List<String> path, LockScope scope) {
    final Lock existing = node.getLock(session.id);
    assert ( existing != null );
    // TODO: upscoping shall be allowed if all nested locks belong to this session 
    if ( scope == existing.scope 
       || ( scope == LockScope.DEEP && node.locksInSubtree() > 1 ) ) { 
      return false; // there are nested locks preventing a deep lock
    }
   
    // just update the lock scope
    node.removeLock(existing);
    node.addLock(existing.scope(scope));
    session.addLock(node);
  
    return true;
  }

  private boolean setWriteLock(
      LockTreeNode node, Session session, List<String> path, LockScope scope) {
    if ( !node.canGetExclusiveLock(session.id) ) {
      return false;
    }
    final Lock existing = node.getLock(session.id);
    if ( scope == LockScope.DEEP ) {
      if (   ( existing == null && node.locksInSubtree() != 1 ) 
          || ( existing != null && node.locksInSubtree() != 2 ) ) {
        return false; // there are nested locks preventing a deep write lock
      }
    }
  
    // locking is legal and there is no existing lock for the current session
    if ( existing == null ) {
      node.addLock(newLock(session.id, path, LockType.WRITE, scope));
      session.addLock(node);
      return true;
    }
    
    // on lock type upgrade, update counts
    node.unlock();
    existing.type.decLock(root, path);
    node.lock();
    Lock newLock = existing.writeLock(scope);
    node.removeLock(existing);
    node.addLock(newLock);
    session.addLock(node);
  
    return true;
  }

  private LockTreeNode findExistingNode(String session, List<String> path) {
    // special case of root lock
    if ( path.isEmpty() ) {
      root.lock();
      return root;
    }
    
    // traverse path described by lock
    LockTreeNode current = root;
    LockTreeNode prev = null;
    current.lock();
    for ( int pos = 0, n = path.size(); pos < n; pos++ ) {
      prev = current;
      current = current.children.get(path.get(pos));
      if ( current == null ) {
        prev.unlock();
        return null;
      }
      current.lock();
      prev.unlock();
    }
    return current;
  }

  static void visit(LockTreeNode root, List<String> path, LockTreeNodeVisitor visitor) {
    LockTreeNode prev = null;
    LockTreeNode current = root;
    
    current.lock();
    visit(current, visitor);
    
    for ( int i = 0, n = path.size(); i < n && current != null ; i++ ) {
      prev = current;
      current = current.children.get(path.get(i));
      if ( current != null ) {
        current.lock();
        current = visit(current, visitor);
      }
      prev.unlock();
    }
    if ( current != null ) {
      current.unlock();
    }
  }
  
  static LockTreeNode visit(LockTreeNode node, LockTreeNodeVisitor visitor) {
    synchronized ( node ) {
      visitor.visit(node);
      if ( node.subtreeEmpty() && node.parent != null ) {
        node.parent.children.remove(node.key);
        return null;
      }
      return node;
    }
  }

  private static boolean currentThreadHoldsNoLocksOnPath(LockTreeNode root, List<String> path) {
    LockTreeNode current = root;
    if ( current.isHeldByCurrentThread() ) {
      return false;
    }
    for ( int i = 0, n = path.size(); i < n; i++ ) {
      String step = path.get(i);
      current = current.children.get(step);
      if ( current == null ) {
        return true;
      } else if ( current.isHeldByCurrentThread() ) {
        LOG.error("Current thread holds a lock on mutex of node: {}", 
            Strings.join(path.subList(0, i + 1)));
        return false;
      }
    }
    return true;
  }

  private static boolean noLoiteringLockNodes(LockTreeNode root, List<String> path) {
    LockTreeNode current = root;
    for ( int i = 0, n = path.size(); i < n; i++ ) {
      String step = path.get(i);      
      current = current.children.get(step);
      if ( current == null ) {
        return true;
      } else {
        synchronized ( current ) {
          if ( !current.hasExclusiveLock() 
              && current.getSharedLocks().isEmpty() 
              && current.children.isEmpty() ) {
            LOG.error("No locks and no children in node: {}", Strings.join(path.subList(0, i + 1)));
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean invariants(LockTreeNode root, List<String> path) {
//    // TODO: implement proper intention locks and counts. Then lock counts corrected can be asserted 
//    synchronized ( root ) {
//      return currentThreadHoldsNoLocksOnPath(root, path)
//          && lockCountsAreCorrect(root)
//          && noLoiteringLockNodes(root, path);
//    }
    return true;
  }

  private static boolean deepLockedByOther(LockTreeNode current, String session, LockType type) {
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
    synchronized ( root ) {
      return readCount(root) == root.shared
          && writeCount(root) == root.exclusive;
    }
  }

  private static int readCount(LockTreeNode node) {
    int count = node.getSharedLocks().size();
    for ( LockTreeNode child : node.children.values() ) {
      count += readCount(child);
    }
    synchronized ( node ) {
      assert ( count == node.shared ) : 
        String.format("node = %1$s, reads = %2$d, actual = %3$d", 
            Strings.join(pathTo(node)), node.shared, count);
    }
    return count;
  }

  private static int writeCount(LockTreeNode node) {
    assert ( node != null );
    int count = ( node.hasExclusiveLock() ) ? 1 : 0;
    for ( LockTreeNode child : node.children.values() ) {
      count += writeCount(child);
    }
    synchronized ( node ) {
      assert ( count == node.exclusive ) : 
        String.format("node = %1$s, writes = %2$d, actual = %3$d", 
            Strings.join(pathTo(node)), node.exclusive, count);
    }
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
    void visit(LockTreeNode node);
  }
}
