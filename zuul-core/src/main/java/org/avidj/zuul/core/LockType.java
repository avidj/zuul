package org.avidj.zuul.core;

import org.avidj.zuul.core.DefaultEmbeddedLockManager.LockTreeNodeVisitor;

import java.util.List;

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

public enum LockType {
  READ( 
          (lm, session, path, scope) -> lm.readLock(session, path, scope),
          n -> { n.is--; n.shared++; },
          n -> n.is--,
          n -> n.shared--),
  WRITE( 
          (lm, session, path, scope) -> lm.writeLock(session, path, scope),
          n -> { n.ix--; n.exclusive++; },
          n -> n.ix--,
          n -> n.exclusive--);
  
  private final LockOperation lo;
  private final LockTreeNodeVisitor convert;
  private final LockTreeNodeVisitor decIntention;
  private final LockTreeNodeVisitor decLock;
  
  private LockType(
      LockOperation lo, 
      LockTreeNodeVisitor convert, 
      LockTreeNodeVisitor decIntention, 
      LockTreeNodeVisitor decLock) {
    this.lo = lo;
    this.convert = convert;
    this.decIntention = decIntention;
    this.decLock = decLock;
  }

  void convertCounts(LockTreeNode root, List<String> path) {
    DefaultEmbeddedLockManager.visit(root, path, convert);
  }

  void decIntention(LockTreeNode root, List<String> path) {
    DefaultEmbeddedLockManager.visit(root, path, decIntention);
  }

  void decLock(LockTreeNode root, List<String> path) {
    DefaultEmbeddedLockManager.visit(root, path, decLock);
  }
  
  boolean lock(LockManager lm, Session session, List<String> path, LockScope scope) {
    return lo.lock(lm, session.id, path, scope);
  }
  
  private interface LockOperation {
    boolean lock(LockManager lm, String session, List<String> path, LockScope scope);
  }  
}
