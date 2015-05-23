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

import java.util.ArrayList;
import java.util.List;

import org.avidj.zuul.core.ConcurrentTestUtil.Session;

public class Threads {
  private List<Session> sessions = new ArrayList<>();

  public static Threads threads(Session s0, Session... more) {
    Threads threads = new Threads();
    threads.add(s0);
    for ( Session s : more ) {
      threads.add(s);
    }
    return threads;
  }

  private void add(Session session) {
    sessions.add(session);
  }

  public Threads run() {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean success() {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean success(int i) {
    // TODO Auto-generated method stub
    return false;
  }
}
