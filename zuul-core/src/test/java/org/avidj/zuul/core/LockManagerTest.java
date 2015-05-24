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

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.avidj.zuul.core.Lock.newLock;

import com.google.common.collect.ImmutableSet;

import org.avidj.zuul.core.DefaultLockManager;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LockManagerTest {
  
  private LockManager lm;

  @Before
  public void before() {
    lm = new DefaultLockManager();
    lm.setSessionTimeout(100000000);
  }
  
  @Test
  public void testRR() {
    boolean success = lm.lock("1", key("a"), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(true));

    success = lm.lock("2", key("a"), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(true));
  }

  @Test
  public void testRootR() {
    boolean success = lm.lock("1", Collections.emptyList(), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(true));
  }

  @Test
  public void testRootW() {
    boolean success = lm.lock("1", Collections.emptyList(), LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(true));
  }

  @Test
  public void testRW() {
    boolean success = lm.lock("1", key("a"), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(true));

    success = lm.lock("2", key("a"), LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(false));
  }

  @Test
  public void testWR() {
    boolean success = lm.lock("1", key("a"), LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(true));

    success = lm.lock("2", key("a"), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(false));
  }

  @Test
  public void testShallowWNestedR() {
    boolean success = lm.lock("1", key("a"), LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(true));

    success = lm.lock("2", key("a", "b"), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(true));
  }

  @Test
  public void testDeepWNestedR() {
    boolean success = lm.lock("1", key("a"), LockType.WRITE, LockScope.DEEP);
    assertThat(success, is(true));

    success = lm.lock("2", key("a", "b"), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(false));
  }
  
  @Test
  public void testWWSamePath() {
    boolean success = lm.writeLock("1", key("a"), LockScope.SHALLOW);
    assertThat(success, is(true));

    success = lm.writeLock("2", key("a", "b"), LockScope.SHALLOW);
    assertThat(success, is(true));
  }

  @Test
  public void testWWDiffPaths() {
    boolean success = lm.writeLock("1", key("a"), LockScope.SHALLOW);
    assertThat(success, is(true));

    success = lm.writeLock("2", key("b"), LockScope.SHALLOW);
    assertThat(success, is(true));
  }

  @Test
  public void testDeepWWSamePath() {
    boolean success = lm.writeLock("1", key("a"), LockScope.DEEP);
    assertThat(success, is(true));

    success = lm.writeLock("2", key("a", "b"), LockScope.SHALLOW);
    assertThat(success, is(false));
  }

  @Test
  public void testWUpscopeWWSamePath() {
    boolean success = lm.writeLock("1", key("a"), LockScope.SHALLOW);
    assertThat(success, is(true));
    
    success = lm.writeLock("1", key("a"), LockScope.DEEP);
    assertThat(success, is(true));

    success = lm.writeLock("2", key("a", "b"), LockScope.SHALLOW);
    assertThat(success, is(false));
  }

  @Test
  public void testWWUpscopeSamePath() {
    boolean success = lm.writeLock("1", key("a"), LockScope.SHALLOW);
    assertThat(success, is(true));
    
    success = lm.writeLock("2", key("a", "b"), LockScope.SHALLOW);
    assertThat(success, is(true));

    success = lm.writeLock("1", key("a"), LockScope.DEEP);
    assertThat(success, is(false));
  }

  @Test
  public void testDWriteReadRelReadSamePath() {
    boolean success = lm.writeLock("1", key(1), LockScope.DEEP);
    assertThat(success, is(true));
    
    success = lm.readLock("2", key(1, 2, 3), LockScope.SHALLOW);
    assertThat(success, is(false));

    success = lm.release("1", key(1));
    assertThat(success, is(true));

    success = lm.readLock("2", key(1, 2, 3), LockScope.SHALLOW);
    assertThat(success, is(true));
  }

  @Test
  public void testGetLocks() {
    boolean success = lm.writeLock("1", key(1), LockScope.DEEP);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.DEEP)))));

    success = lm.readLock("1", key(2, 1), LockScope.DEEP);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
        newLock("1", key(2, 1), LockType.READ, LockScope.DEEP)))));

    success = lm.readLock("2", key(2), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
        newLock("1", key(2, 1), LockType.READ, LockScope.DEEP)))));

    success = lm.writeLock("1", key(2, 2), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
        newLock("1", key(2, 1), LockType.READ, LockScope.DEEP),
        newLock("1", key(2, 2), LockType.WRITE, LockScope.SHALLOW)))));

    // upgrade type
    success = lm.writeLock("1", key(2, 1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
        newLock("1", key(2, 1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(2, 2), LockType.WRITE, LockScope.SHALLOW)))));

    // downgrade scope and type
    success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW),
        newLock("1", key(2, 1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(2, 2), LockType.WRITE, LockScope.SHALLOW)))));
  }

  @Test
  public void testWRel() {
    boolean success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));
    
    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testWWRelRel() {
    boolean success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testRRel() {
    boolean success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));
    
    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testRel() {
    boolean success = lm.release("1", key(1));
    assertThat(success, is(false));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testRRRelRel() {
    boolean success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testRWRelRel() {
    boolean success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testWRRelRel() {
    boolean success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.release("1", key(1));
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testWWMultR() {
    boolean success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.writeLock("1", key(1, 2, 3), LockScope.DEEP);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));

    int released = lm.release("1", Arrays.asList(key(1), key(1, 2, 3)));
    assertThat(released, is(2));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testWReWMultRel() {
    boolean success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.writeLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    success = lm.writeLock("1", key(1, 2, 3), LockScope.DEEP);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));

    int released = lm.release("1", Arrays.asList(key(1), key(1, 2, 3)));
    assertThat(released, is(2));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));

    released = lm.release("1", Arrays.asList(key(1)));
    assertThat(released, is(1));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testRReRMultRel() {
    boolean success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.readLock("1", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    success = lm.readLock("1", key(1, 2, 3), LockScope.DEEP);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW),
        newLock("1", key(1, 2, 3), LockType.READ, LockScope.SHALLOW)))));

    int released = lm.release("1", Arrays.asList(key(1), key(1, 2, 3)));
    assertThat(released, is(2));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));

    released = lm.release("1", Arrays.asList(key(1)));
    assertThat(released, is(1));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testMultiLockSuccess() {
    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
  }

  @Test
  public void testMultiLockFail1() {
    boolean success = lm.writeLock("2", key(1), LockScope.SHALLOW);
    assertThat(success, is(true));
    
    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(false));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testMultiLockFail2() {
    boolean success = lm.writeLock("2", key(2), LockScope.SHALLOW);
    assertThat(success, is(true));
    
    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(false));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testMultiLockFail3() {
    boolean success = lm.readLock("2", key(1, 2, 3), LockScope.SHALLOW);
    assertThat(success, is(true));
    
    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(false));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testMultiLockFailDeepSes2() {
    boolean success = 
        lm.multiLock("2", Arrays.asList(key(1, 2)), LockType.READ, LockScope.DEEP);
    assertThat(success, is(true));
    
    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(false));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testMultiLockFailDeepSes1() {
    boolean success = 
        lm.multiLock("2", Arrays.asList(key(1, 2, 3, 4, 5)), LockType.READ, LockScope.SHALLOW);
    assertThat(success, is(true));
    
    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.DEEP);
    assertThat(success, is(false));
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testReleaseSession() {
    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
    
    lm.release("1");
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testHeartbeatTimeout() throws InterruptedException {
    lm.setSessionTimeout(10);
    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
    
    Thread.sleep(20);
    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
  }

  @Test
  public void testHeartbeat() throws InterruptedException {
    lm.setSessionTimeout(10);
    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
        LockType.WRITE, LockScope.SHALLOW);
    assertThat(success, is(true));
    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
    
    Thread.sleep(5);
    assertThat(lm.getLocks("1").size(), is(3));
    lm.heartbeat("1");

    Thread.sleep(5);
    assertThat(lm.getLocks("1").size(), is(3));
    lm.heartbeat("1");

    Thread.sleep(5);
    assertThat(lm.getLocks("1").size(), is(3));
    lm.heartbeat("1");

    Thread.sleep(5);
    assertThat(lm.getLocks("1").size(), is(3));
  }

  static List<String> key(String first, String... more) {
    List<String> key = new ArrayList<>(more.length + 1);
    key.add(first);
    key.addAll(Arrays.asList(more));
    return key;
  }

  static List<String> key(int first, int... more) {
    List<String> key = new ArrayList<>(more.length + 1);
    key.add(Integer.toString(first));
    for ( int i : more ) {
      key.add(Integer.toString(i));
    }
    return key;
  }
}
