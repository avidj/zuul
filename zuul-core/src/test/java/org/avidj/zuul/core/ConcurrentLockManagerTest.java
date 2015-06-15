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

import static org.avidj.zuul.core.ConcurrentTest.thread;
import static org.avidj.zuul.core.ConcurrentTest.threads;
import static org.avidj.zuul.core.LockManagerTest.key;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;

import org.avidj.zuul.core.DefaultLockManager;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcurrentLockManagerTest {
  private static final Logger LOG = LoggerFactory.getLogger(ConcurrentLockManagerTest.class);
  private LockManager lm;

  @Before
  public void before() {
    lm = new DefaultLockManager();
  }
  
  @Test
  public void testReadRead() {
    threads(
        thread().exec(() -> {
          boolean success = lm.lock("1", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().exec((t) -> {
          t.waitFor(1);
          boolean success = lm.lock("2", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        }))
        .repeat(10000)
        .assertSuccess();
  }

  @Test
  public void testReadWrite() {
    threads(
        thread().exec(() -> {
          boolean success = lm.lock("1", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().exec((t) -> {
          t.waitFor(1);
          boolean success = lm.lock("2", Arrays.asList("a"), LockType.WRITE, LockScope.SHALLOW);
          assertThat(success, is(false));
        }))
        .repeat(10000)
        .assertSuccess();
  }

  @Test
  public void testWriteRead() {
    threads(
        thread().exec(() -> {
          boolean success = lm.lock("1", Arrays.asList("a"), LockType.WRITE, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().exec((t) -> {
          t.waitFor(1);
          boolean success = lm.lock("2", Arrays.asList("a"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(false));
        }))
        .repeat(10000)
        .assertSuccess();
  }

  @Test
  public void testShallowWriteNestedRead() {
    threads(
        thread().exec(() -> {
          boolean success = lm.lock("1", key("a"), LockType.WRITE, LockScope.SHALLOW);
          assertThat(success, is(true));
        }),
        thread().exec((t) -> {
          t.waitFor(1);
          boolean success = lm.lock("2", key("a", "b"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(true));
        }))
        .repeat(10000)
        .assertSuccess();
  }

  @Test
  public void testDeepWriteNestedRead() {
    threads(
        thread().exec(() -> {
          boolean success = lm.lock("1", key("a"), LockType.WRITE, LockScope.DEEP);
          assertThat(success, is(true));
        }),
        thread().exec((t) -> {
          t.waitFor(1);
          boolean success = lm.lock("2", key("a", "b"), LockType.READ, LockScope.SHALLOW);
          assertThat(success, is(false));
        }))
        .repeat(10000)
        .assertSuccess();
  }

  @Test
  public void testForDeadlocks() {
    threads(
        thread().exec(() -> {
          boolean success = lm.readLock("1", key("a", "b", "c", "d", "e", "f"), LockScope.DEEP);
          assertThat(success, is(true));
        }),
        thread().exec(() -> {
          boolean success = lm.writeLock("2", key("a", "b", "c", "d", "e", "f"), LockScope.DEEP);
          assertThat(success, is(true));
        }))
      .repeat(10000)
      .killAfter(1000)
      .assertSuccessCount(1); // either one of the threads must fail
  }
  
//  @Test
//  public void testForDeadlocks() {
//    ConcurrentTest threads = threads(
//        thread().exec(() -> {
//          boolean success = lm.readLock("1", key("a", "b", "c", "d", "e", "f"), LockScope.DEEP);
//          assertThat(success, is(true));
//        }).replicate(100))
//      .repeat(100)
//      .killAfter(1000)
//      .run();
//    assertThat(threads.successCount(), is(1)); // either one of the threads must fail
//  }
}
