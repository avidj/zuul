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

import static org.avidj.zuul.core.ConcurrentTestUtil.thread;
import static org.avidj.zuul.core.LockManagerTest.key;
import static org.avidj.zuul.core.Threads.threads;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;

import org.avidj.zuul.core.DefaultLockManager;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class ConcurrentLockManagerTest {
  
  private LockManager lm;

  @Before
  public void before() {
    lm = new DefaultLockManager();
  }
  
  @Test
  public void testReadRead() {
    Threads threads = threads(
        thread().block(() -> {
          boolean success = lm.lock("1", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().block(() -> {
          boolean success = lm.lock("2", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        })
    ).run();
    assertThat(threads.success(), is(true));
  }

  @Test
  public void testReadWrite() {
    Threads threads = threads(
        thread().block(() -> {
          boolean success = lm.lock("1", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().block(() -> {
          boolean success = lm.lock("2", Arrays.asList("a"), LockType.WRITE, LockScope.SHALLOW);
          assertThat(success, is(false));
        })
    ).run();
    assertThat(threads.success(), is(true));
  }

  @Test
  public void testWriteRead() {
    Threads threads = threads(
        thread().block(() -> {
          boolean success = lm.lock("1", Arrays.asList("a"), LockType.WRITE, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().block(() -> {
          boolean success = lm.lock("2", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(false));
        })
    ).run();
    assertThat(threads.success(), is(true));
  }

  @Test
  public void testShallowWriteNestedRead() {
    Threads threads = threads(
        thread().block(() -> {
          boolean success = lm.lock("1", key("a"), LockType.WRITE, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().block(() -> {
          boolean success = lm.lock("2", key("a", "b"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        })
    ).run();
    assertThat(threads.success(), is(true));
  }

  @Test
  public void testDeepWriteNestedRead() {
    Threads threads = threads(
        thread().block(() -> {
          boolean success = lm.lock("1", key("a"), LockType.WRITE, LockScope.DEEP);
          assertThat(success, is(true));
        }),
        thread().block(() -> {
          boolean success = lm.lock("2", key("a", "b"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(false));
        })
    ).run();
    assertThat(threads.success(), is(true));
  }
}
