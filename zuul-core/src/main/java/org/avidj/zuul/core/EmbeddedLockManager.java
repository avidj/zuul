package org.avidj.zuul.core;

import com.google.common.base.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
public interface EmbeddedLockManager extends LockManager {
  static final Logger LOG = LoggerFactory.getLogger(EmbeddedLockManager.class);

  public Session getSession(String id);

  public LockTreeNode getRoot();

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
  public default boolean lock(String sessionId, List<String> path, LockType type, LockScope scope) {
    Preconditions.checkNotNull(sessionId, "sessionId must not be null");
    Preconditions.checkNotNull(path, "path must not be null");
    Preconditions.checkNotNull(type, "type must not be null");
    Preconditions.checkNotNull(scope, "scope must not be null");
    final Session session = getSession(sessionId);
    return type.lock(this, session, path, scope);
  }

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
  public default boolean multiLock(
      String sessionId, List<List<String>> paths, LockType type, LockScope scope) {
    Preconditions.checkNotNull(sessionId, "session must not be null");
    Preconditions.checkNotNull(paths, "paths must not be null");
    Preconditions.checkArgument(!paths.isEmpty(), "paths must not be empty");
    Preconditions.checkNotNull(type, "type must not be null");
    Preconditions.checkNotNull(scope, "scope must not be null");
    sessionId = sessionId.intern();
    
    Collections.sort(paths, LockUtils.pathComparator());
    boolean success = true;
    Set<List<String>> obtained = new HashSet<>();
    for ( List<String> path : paths ) {
      success &= lock(sessionId, path, type, scope);
      if ( success ) {
        obtained.add(path);
      } else {
        break;
      }
    }
    if ( !success ) {
      for ( List<String> path : obtained ) {
        release(sessionId, path);
      }
    }
    return success;
  }
  
  /**
   * Release a collection of locks. As locks are reentrant, they may have to be released multiple 
   * times to be eventually <em>really</em> released. Locks not held by the session are ignored by
   * this method.
   * 
   * @param session the session to release the locks for
   * @param paths the locks to release
   * @return the number of locks successfully released
   */
  public default int release(String session, Collection<List<String>> paths) {
    int released = 0;
    for ( List<String> path : paths ) {
      if ( release(session, path) ) {
        released++;
      }
    }
    return released;
  }
}
