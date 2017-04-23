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

import org.avidj.util.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Representation of a lock referenced by a lock tree node. The lock is associated with an 
 * immutable composite key that represents the path to that lock in the lock tree. Also,
 * the lock is either read or a write lock and orthogonally it may be a shallow lock 
 * affecting only this node, or a deep lock affecting  the whole subtree rooted at this node.
 * Each lock is associated to the session owning it. Now, as this class is immutable, any change
 * in the state of the lock results in a new lock node to be created.
 */
public class Lock {
  private final int hashCode;
  public int count;
  public String session;
  public List<String> key;
  public LockType type;
  public LockScope scope;
  
  /**
   * @param session the session owning this lock
   * @param path the path to this node, i.e., the lock identifier
   * @param type the type of lock, read or write
   * @param scope the scope of the lock, shallow or deep
   * @return the new lock object
   */
  public static Lock newLock(String session, List<String> path, LockType type, LockScope scope) {
    return new Lock(session, path, type, scope, 1);
  }
  
  private Lock(String session, List<String> key, LockType type, LockScope scope, int count) {
    assert ( count >= 0 );
    this.session = session.intern();
    this.key = intern(key);
    this.type = type;
    this.scope = scope;
    this.count = count;
    hashCode = Objects.hash(session, type, key);
  }
  
  private static List<String> intern(List<String> key) {
    List<String> intern = new ArrayList<>(key.size());
    for ( String k : key ) {
      intern.add(k.intern());
    }
    return Collections.unmodifiableList(intern);
  }

  /**
   * Obtain a write lock of the desired scope.
   * 
   * @param scope the desired lock scope
   * @return a write lock of the given scope corresponding to this lock's path
   */
  Lock writeLock(LockScope scope) {
    return new Lock(session, key, LockType.WRITE, scope, count + 1);
  }

  /**
   * Obtain a read lock of the desired scope.
   * 
   * @param scope the desired lock scope
   * @return a read lock of the given scope corresponding to this lock's path
   */
  Lock readLock(LockScope scope) {
    return new Lock(session, key, LockType.READ, scope, count + 1);
  }

  /**
   * Change the scope of this lock according to the input.
   * 
   * @param scope the desired scope
   * @return a lock corresponding to this lock but with the desired scope
   */
  Lock scope(LockScope scope) {
    return new Lock(session, key, type, scope, count);
  }

  /**
   * Make this lock a deep lock.
   * 
   * @return a deep lock otherwise corresponding to this lock 
   */
  Lock deepLock() {
    if ( scope == LockScope.DEEP ) {
      return this;
    }
    return new Lock(session, key, type, LockScope.DEEP, count);
  }
  
  /**
   * Make this lock a shallow lock.
   * 
   * @return a shallow lock otherwise corresponding to this lock 
   */
  Lock shallowLock() {
    if ( scope == LockScope.SHALLOW ) {
      return this;
    }
    return new Lock(session, key, type, LockScope.SHALLOW, count);
  }

  /**
   * Release this lock.
   * @return this 
   */
  Lock release() {
    count = count - 1;
    return this;
  }
  
  @Override
  public final boolean equals(Object other) {
    if ( this == other ) {
      return true;
    }
    if ( !( other instanceof Lock ) ) {
      return false;
    }
    Lock that = (Lock)other;
    if ( this.hashCode != that.hashCode ) {
      return false;
    }
    return Objects.equals(this.session, that.session)
        && this.type == that.type
        && Objects.equals(this.key, that.key);
  }
  
  @Override
  public final int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return new StringBuilder("Lock(")
        .append("session = ").append(session)
        .append(", type = ").append(type)
        .append(", scope = ").append(scope)
        .append(", count = ").append(count)
        .append(", key = (").append(Strings.join(key))
        .append(") )")
        .toString();
  }  
}
