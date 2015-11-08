package org.avidj.zuul.client;

import static org.avidj.zuul.core.Lock.newLock;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.avidj.zuul.core.DefaultLockManager;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class AutoClosableLockTest {
  private static final String SESSION_1 = "test-session 1";
  private static final String SESSION_2 = "test-session 2";
  private LockManager underlyingLm;
  private AutoCloseableLockManager lm1;
  private AutoCloseableLockManager lm2;
  
  /**
   * @return the lock manager to be tested
   */
  protected AutoCloseableLockManager lockManager(String session) {
    return new AutoCloseableLockManager(session, underlyingLm);
  }

  @Before
  public void before() {
    underlyingLm = new DefaultLockManager();

    lm1 = lockManager(SESSION_1);
    lm1.setSessionTimeout(100000000);
    
    lm2 = lockManager(SESSION_2);
    lm2.setSessionTimeout(100000000);
  }
  
  @Test
  public void testRR() {
    AutoCloseableLock lock1 = lm1.readLock(key("a"));
    assertThat(lock1.isReadLocked(), is(true));
    
    AutoCloseableLock lock2 = lm2.readLock(key("a"));
    assertThat(lock2.isReadLocked(), is(true));
    
    lock1.release();
    assertThat(lock1.isReadLocked(), is(false));
    
    lock2.release();
    assertThat(lock2.isReadLocked(), is(false));
  }

  @Test
  public void testRRAutoClose() {
    try ( AutoCloseableLock lock1 = lm1.readLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(false));
      try ( AutoCloseableLock lock2 = lm2.readLock(key("a"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(true));
        assertThat(underlyingLm.getLocks(SESSION_2), is(not(empty())));
      }
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
    }
    assertThat(underlyingLm.getLocks(SESSION_1), is(empty()));
  }

  @Test
  public void testRootR() {
    try ( AutoCloseableLock lock1 = lm1.readLock(Collections.emptyList(), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(false));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
    }
    assertThat(underlyingLm.getLocks(SESSION_1), is(empty()));
  }

  @Test
  public void testRootW() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(Collections.emptyList(), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
    }
    assertThat(underlyingLm.getLocks(SESSION_1), is(empty()));
  }

  @Test
  public void testRW() {
    try ( AutoCloseableLock lock1 = lm1.readLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(false));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      try ( AutoCloseableLock lock2 = lm2.writeLock(key("a"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(false));
        assertThat(lock2.isWriteLocked(), is(false));
        assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
      }
      assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
    }
    assertThat(underlyingLm.getLocks(SESSION_1), is(empty()));
  }

  @Test
  public void testWR() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      try ( AutoCloseableLock lock2 = lm2.readLock(key("a"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(false));
        assertThat(lock2.isWriteLocked(), is(false));
        assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
      }
      assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
    }
    assertThat(underlyingLm.getLocks(SESSION_1), is(empty()));
  }

  @Test
  public void testShallowWNestedR() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      try ( AutoCloseableLock lock2 = lm2.readLock(key("a", "b"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(true));
        assertThat(lock2.isWriteLocked(), is(false));
        assertThat(underlyingLm.getLocks(SESSION_2), is(not(empty())));
      }
    }
  }

  @Test
  public void testDeepWNestedR() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.DEEP) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      try ( AutoCloseableLock lock2 = lm2.readLock(key("a", "b"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(false));
        assertThat(lock2.isWriteLocked(), is(false));
        assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
      }
    }
  }
  
  @Test
  public void testWWSamePath() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      try ( AutoCloseableLock lock2 = lm2.writeLock(key("a", "b"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(true));
        assertThat(lock2.isWriteLocked(), is(true));
        assertThat(underlyingLm.getLocks(SESSION_2), is(not(empty())));
      }
    }
  }

  @Test
  public void testWWDiffPaths() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      try ( AutoCloseableLock lock2 = lm2.writeLock(key("b"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(true));
        assertThat(lock2.isWriteLocked(), is(true));
        assertThat(underlyingLm.getLocks(SESSION_2), is(not(empty())));
      }
    }
  }

  @Test
  public void testDeepWWSamePath() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.DEEP) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      try ( AutoCloseableLock lock2 = lm2.writeLock(key("a", "b"), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(false));
        assertThat(lock2.isWriteLocked(), is(false));
        assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
      }
    }
  }
  
  @Test
  public void testWUpscopeWWSamePath() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      assertThat(lock1.isDeepLocked(), is(false));
      try ( AutoCloseableLock lock1P = lock1.upScope() ) {
        assertThat(lock1P.isReadLocked(), is(true));
        assertThat(lock1P.isWriteLocked(), is(true));
        assertThat(lock1P.isDeepLocked(), is(true));
        assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
        try ( AutoCloseableLock lock2 = lm2.writeLock(key("a", "b"), LockScope.SHALLOW) ) {
          assertThat(lock2.isReadLocked(), is(false));
          assertThat(lock2.isWriteLocked(), is(false));
          assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
        }
      }
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      assertThat(lock1.isDeepLocked(), is(false));
    }
  }

  @Test
  public void testWWUpscopeSamePath() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key("a"), LockScope.SHALLOW) ) {
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
      assertThat(lock1.isDeepLocked(), is(false));
      try ( AutoCloseableLock lock2 = lm2.writeLock(key("a", "b"), LockScope.SHALLOW) ) {
        assertThat(lock2.isWriteLocked(), is(true));
        assertThat(underlyingLm.getLocks(SESSION_2), is(not(empty())));
        try ( AutoCloseableLock lock1P = lock1.upScope() ) {
          assertThat(lock1P.isWriteLocked(), is(true));
          assertThat(lock1P.isDeepLocked(), is(false));
        }
      }
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_1), is(not(empty())));
    }
  }

  @Test
  public void testDWriteReadRelReadSamePath() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key(1), LockScope.DEEP) ) {
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(lock1.isDeepLocked(), is(true));
      try ( AutoCloseableLock lock2 = lm2.writeLock(key(1, 2, 3), LockScope.SHALLOW) ) {
        assertThat(lock2.isReadLocked(), is(false));
        assertThat(underlyingLm.getLocks(SESSION_2), is(empty()));
      }
    }

    try ( AutoCloseableLock lock2 = lm2.writeLock(key(1, 2, 3), LockScope.SHALLOW) ) {
      assertThat(lock2.isReadLocked(), is(true));
      assertThat(underlyingLm.getLocks(SESSION_2), is(not(empty())));
    }
  }

//  @Test
//  public void testGetLocks() {
//    boolean success = lm.writeLock("1", key(1), LockScope.DEEP);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.DEEP)))));
//
//    success = lm.readLock("1", key(2, 1), LockScope.DEEP);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
//        newLock("1", key(2, 1), LockType.READ, LockScope.DEEP)))));
//
//    success = lm.readLock("2", key(2), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
//        newLock("1", key(2, 1), LockType.READ, LockScope.DEEP)))));
//
//    success = lm.writeLock("1", key(2, 2), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
//        newLock("1", key(2, 1), LockType.READ, LockScope.DEEP),
//        newLock("1", key(2, 2), LockType.WRITE, LockScope.SHALLOW)))));
//
//    // upgrade type
//    success = lm.writeLock("1", key(2, 1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.DEEP),
//        newLock("1", key(2, 1), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(2, 2), LockType.WRITE, LockScope.SHALLOW)))));
//
//    // downgrade scope and type
//    success = lm.readLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.READ, LockScope.SHALLOW),
//        newLock("1", key(2, 1), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(2, 2), LockType.WRITE, LockScope.SHALLOW)))));
//  }

  @Test
  public void testWRel() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key(1), LockScope.SHALLOW) ) {
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW)))));
    }
    assertThat(lm1.getLocks(), is(empty()));
  }

//  @Test
//  public void testWWRelRel() {
//    boolean success = lm.writeLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));
//
//    success = lm.writeLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));
//
//    success = lm.release("1", key(1));
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));
//
//    success = lm.release("1", key(1));
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testRRel() {
//    boolean success = lm.readLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));
//    
//    success = lm.release("1", key(1));
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }

  @Test
  public void testRRRelRel() {
    try ( AutoCloseableLock lock1 = lm1.readLock(key(1), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.READ, LockScope.SHALLOW)))));
      try ( AutoCloseableLock lock1P = lock1.readLock() ) {
        assertThat(lock1.isReadLocked(), is(true));
        assertThat(lm1.getLocks(), is(equalTo(
            ImmutableSet.of(newLock(SESSION_1, key(1), LockType.READ, LockScope.SHALLOW)))));
      }
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.READ, LockScope.SHALLOW)))));
    }
    assertThat(lm1.getLocks(), is(empty()));
  }

  // TODO: probably, this is how it should behave, how about the DefaultLockManagerTest?
  @Test
  public void testRWRelRel() {
    try ( AutoCloseableLock lock1 = lm1.readLock(key(1), LockScope.SHALLOW) ) {
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.READ, LockScope.SHALLOW)))));
      try ( AutoCloseableLock lock1P = lock1.writeLock() ) {
        assertThat(lock1.isWriteLocked(), is(true));
        assertThat(lm1.getLocks(), is(equalTo(
            ImmutableSet.of(newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW)))));
      }
      assertThat(lock1.isReadLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.READ, LockScope.SHALLOW)))));
    }
    assertThat(lm1.getLocks(), is(empty()));
  }

  @Test
  public void testWRRelRel() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key(1), LockScope.SHALLOW) ) {
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW)))));
      try ( AutoCloseableLock lock1P = lock1.readLock() ) {
        assertThat(lock1.isReadLocked(), is(true));
        assertThat(lm1.getLocks(), is(equalTo(
            ImmutableSet.of(newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW)))));
      }
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW)))));
    }
    assertThat(lm1.getLocks(), is(empty()));
  }

  @Test
  public void testWWMultR() {
    try ( AutoCloseableLock lock1 = lm1.writeLock(key(1), LockScope.SHALLOW) ) {
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(
          ImmutableSet.of(newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW)))));
      try ( AutoCloseableLock lock1P = lm1.writeLock(key(1, 2, 3), LockScope.DEEP) ) {
        assertThat(lock1P.isWriteLocked(), is(true));
        assertThat(lm1.getLocks(), is(equalTo(ImmutableSet.of(
            newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW),
            newLock(SESSION_1, key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
      }
      assertThat(lock1.isWriteLocked(), is(true));
      assertThat(lm1.getLocks(), is(equalTo(ImmutableSet.of(
          newLock(SESSION_1, key(1), LockType.WRITE, LockScope.SHALLOW)))));
    }
    assertThat(lm1.getLocks(), is(empty()));
  }

//  @Test
//  public void testWReWMultRel() {
//    boolean success = lm.writeLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));
//
//    success = lm.writeLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));
//
//    success = lm.writeLock("1", key(1, 2, 3), LockScope.DEEP);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
//
//    int released = lm.release("1", Arrays.asList(key(1), key(1, 2, 3)));
//    assertThat(released, is(2));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW)))));
//
//    released = lm.release("1", Arrays.asList(key(1)));
//    assertThat(released, is(1));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testRReRMultRel() {
//    boolean success = lm.readLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));
//
//    success = lm.readLock("1", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));
//
//    success = lm.readLock("1", key(1, 2, 3), LockScope.DEEP);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.READ, LockScope.SHALLOW),
//        newLock("1", key(1, 2, 3), LockType.READ, LockScope.SHALLOW)))));
//
//    int released = lm.release("1", Arrays.asList(key(1), key(1, 2, 3)));
//    assertThat(released, is(2));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.READ, LockScope.SHALLOW)))));
//
//    released = lm.release("1", Arrays.asList(key(1)));
//    assertThat(released, is(1));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testMultiLockSuccess() {
//    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
//  }
//
//  @Test
//  public void testMultiLockFail1() {
//    boolean success = lm.writeLock("2", key(1), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    
//    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(false));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testMultiLockFail2() {
//    boolean success = lm.writeLock("2", key(2), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    
//    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(false));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testMultiLockFail3() {
//    boolean success = lm.readLock("2", key(1, 2, 3), LockScope.SHALLOW);
//    assertThat(success, is(true));
//    
//    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(false));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testMultiLockFailDeepSes2() {
//    boolean success = 
//        lm.multiLock("2", Arrays.asList(key(1, 2)), LockType.READ, LockScope.DEEP);
//    assertThat(success, is(true));
//    
//    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(false));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testMultiLockFailDeepSes1() {
//    boolean success = 
//        lm.multiLock("2", Arrays.asList(key(1, 2, 3, 4, 5)), LockType.READ, LockScope.SHALLOW);
//    assertThat(success, is(true));
//    
//    success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.DEEP);
//    assertThat(success, is(false));
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testReleaseSession() {
//    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
//    
//    lm.release("1");
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testHeartbeatTimeout() throws InterruptedException {
//    lm.setSessionTimeout(10);
//    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
//    
//    Thread.sleep(20);
//    assertThat(lm.getLocks("1"), is(equalTo(Collections.emptySet())));
//  }
//
//  @Test
//  public void testHeartbeat() throws InterruptedException {
//    lm.setSessionTimeout(10);
//    boolean success = lm.multiLock("1", Arrays.asList(key(1), key(2), key(1, 2, 3)), 
//        LockType.WRITE, LockScope.SHALLOW);
//    assertThat(success, is(true));
//    assertThat(lm.getLocks("1"), is(equalTo(ImmutableSet.of(
//        newLock("1", key(1), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(2), LockType.WRITE, LockScope.SHALLOW),
//        newLock("1", key(1, 2, 3), LockType.WRITE, LockScope.SHALLOW)))));
//    
//    Thread.sleep(5);
//    assertThat(lm.getLocks("1").size(), is(3));
//    lm.heartbeat("1");
//
//    Thread.sleep(5);
//    assertThat(lm.getLocks("1").size(), is(3));
//    lm.heartbeat("1");
//
//    Thread.sleep(5);
//    assertThat(lm.getLocks("1").size(), is(3));
//    lm.heartbeat("1");
//
//    Thread.sleep(5);
//    assertThat(lm.getLocks("1").size(), is(3));
//  }

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
