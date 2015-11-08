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

import org.avidj.zuul.core.LockManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

public class Session {
  private final LockManager lm;
  private final Map<List<String>, LockTreeNode> locks = new HashMap<>();
  private SessionTimeoutTask timeoutTask;
  final String id;

  Session(LockManager lm, String id) {
    assert ( lm != null );
    assert ( id != null );
    this.lm = lm;
    this.id = id;
  }
  
  public void invalidate() {
    lm.release(id);
  }
  
  public TimerTask newTimeoutTask() {
    timeoutTask = new SessionTimeoutTask(lm, this);
    return timeoutTask;
  }

  public void cancelTimeout() {
    timeoutTask.cancel();
  }

  public void addLock(LockTreeNode node) {
    locks.put(node.getLock(id).key, node);
  }

  public void removeLock(List<String> key) {
    locks.remove(key);
  }

  public Set<LockTreeNode> getLocks() {
    return Collections.unmodifiableSet(new HashSet<LockTreeNode>(locks.values()));
  }
  
  @Override
  public String toString() {
    return new StringBuilder("Session( id = ").append(id).append(")").toString();
  }
}
