package org.avidj.zuul.core;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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

/**
 * Interface of the in-memory lock manager. All locking operations shall be implmemented atomically.
 * That is, either they are successful or they fail completely. In other words, if an operation 
 * cannot successfully finish, it shall leave all affected objects as they were before the call.
 */
public interface LockManager {

  /**
   * After a timeout all locks of a session are released if that session is not kept alive through
   * heartbeats or otherwise accessed through lock operations. 
   * TODO: While this is currently the timeout used for all sessions, it should rather be a maximum
   * timeout where each session (or lock) can define its own TTL.
   * 
   * @param timeoutMillis number of milliseconds until sessions time out and locks are released
   */
  public void setSessionTimeout(long timeoutMillis);

  /**
   * The set of all locks held by the given session. The result may not be 
   * consistent if there are concurrent calls to {@link #lock(String, List, LockType, LockScope)} or
   * {{@link #release(String, Collection)} methods and their derivatives {@literal wrt.} the same
   * session.
   * 
   * @param session the session to query
   * @return the set of all locks held by the given session
   */
  public Set<Lock> getLocks(String session);

  /**
   * Obtain a read lock on the given {@code path} for the given {@code session}. As locks are 
   * reentrant this may instead reenter the lock, instead.
   * @param session the session to obtain the lock for
   * @param path the lock to be obtained or reentered
   * @param scope the scope of the lock (on reentering, the scope will be updated to this scope)
   * @return {@code true} if the operation was successful, {@code false} if the lock conflicted with
   *     an existing lock by another session 
   */
  public boolean readLock(String session, List<String> path, LockScope scope);

  /**
   * Obtain a write lock on the given {@code path} for the given {@code session}. As locks are 
   * reentrant this may instead reenter the lock, instead. If the session already holds a read lock,
   * it will be upgraded if possible.
   * @param session the session to obtain the lock for
   * @param path the lock to be obtained or reentered
   * @param scope the scope of the lock (on reentering, the scope will be updated to this scope)
   * @return {@code true} if the operation was successful, {@code false} if the lock conflicted with
   *     an existing lock by another session 
   */
  public boolean writeLock(String session, List<String> path, LockScope scope);

  /**
   * Try to obtain a lock of the given {@code type} and {@code scope} for the given {@code session}.
   * Lock type and scope may be upgraded as needed. If the lock already exists, it will be reentered
   * and, hence, must be released multiple times as well.
   * 
   * @param sessionId the session to obtain a lock for, not {@code null}
   * @param path the path of the resource to lock, not {@code null}
   * @param type the lock type, not {@code null}
   * @param scope the lock scope, not {@code null}
   * @return {@code true}, iff the lock attempt was successful
   */
  public boolean lock(String sessionId, List<String> path, LockType type, LockScope scope);

  /**
   * Try to obtain multiple locks of the given {@code type} and {@code scope} for the given 
   * {@code session}. If possible, this method should be preferred over multiple invocations of 
   * {@link #lock(String, List, LockType, LockScope)} so as to avoid deadlocks. If any of the locks
   * cannot be obtained this method rolls back all successful locks. This method can be used to 
   * implement two-phase locking.
   * 
   * @param sessionId the session to obtain a lock for, not {@code null}
   * @param paths the paths of the resources to lock, not {@code null}, and not empty
   * @param type the lock type, not {@code null}
   * @param scope the lock scope, not {@code null}
   * @return {@code true}, iff the lock attempt was successful
   */
  public boolean multiLock(
      String sessionId, List<List<String>> paths, LockType type, LockScope scope);
  
  /**
   * Releases the given {@code lock} for the given {@code session}. As locks are reentrant, they
   * may have to be released multiple times to be eventually <em>really</em> released. 
   * 
   * @param session the session to release the lock for
   * @param path the lock to be released
   * @return {@code true}, iff the session held the lock and it was released  
   */
  boolean release(String session, List<String> path) throws IllegalStateException;

  /**
   * Release a collection of locks. As locks are reentrant, they may have to be released multiple 
   * times to be eventually <em>really</em> released. Locks not held by the session are ignored by
   * this method.
   * 
   * @param session the session to release the locks for
   * @param paths the locks to release
   * @return the number of locks successfully released
   */
  public int release(String session, Collection<List<String>> paths);
  
  /**
   * Release all locks for the given session. Unlike the other methods 
   * ({@link #release(String, List)} and {@link #release(String, Collection)} this method definitely
   * releases all locks held by the session, no matter how often they have been reentered. 
   * 
   * @param session the session to release locks for
   */
  public void release(String session); 
  
  /**
   * Locks can time out. Regular heart beats keep the locks of a session alive. 
   * 
   * @param session the session to keep alive
   */
  public void heartbeat(String session);

  /**
   * Extend the scope of the given lock to a deep lock (if it is not yet). 
   * This operation blocks until it succeeds.
   * @return {@code true}, iff the scope was shallow and now is deep
   */
  public boolean upScope(String session, List<String> path, LockType type);

  /**
   * Decrease the scope of the given lock to a shallow lock (if it is not yet). 
   * This operation blocks until it succeeds.
   * @return {@code true}, iff the scope was deep and now is shallow
   */
  public boolean downScope(String session, List<String> path, LockType type, LockScope shallow);
}
