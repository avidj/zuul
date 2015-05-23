package org.avidj.zuul.core;

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
  READ  ( 
      (lm, session, path, scope) -> lm.readLock(session, path, scope),
      (root, path) -> DefaultLockManager.visit(root, path, DefaultLockManager.DEC_READ_COUNTS) ),
  WRITE ( (lm, session, path, scope) -> lm.writeLock(session, path, scope),
      (root, path) -> DefaultLockManager.visit(root, path, DefaultLockManager.DEC_WRITE_COUNTS) );
  
  private final LockOperation lo;
  private DecrementPath dec;
  
  private LockType(LockOperation lo, DecrementPath dec) {
    this.lo = lo;
    this.dec = dec;
  }
  
  void decCounts(LockTreeNode root, List<String> path) {
    dec.dec(root, path);
  }
  
  boolean lock(LockManager lm, String session, List<String> path, LockScope scope) {
    return lo.lock(lm, session, path, scope);
  }
  
  private interface DecrementPath {
    void dec(LockTreeNode root, List<String> path);
  }
  
  private interface LockOperation {
    boolean lock(LockManager lm, String session, List<String> path, LockScope scope);
  }  
}
