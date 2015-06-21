package org.avidj.zuul.core;

import org.avidj.zuul.core.ConcurrentTest.Actions;
import org.avidj.zuul.core.ConcurrentTest.NoArgActions;
import org.avidj.zuul.core.ConcurrentTest.NoArgActionsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

class TestRun {
  static final long SLEEP_INTERVAL = 5;
  
  private final List<Throwable> throwables;
  private final CountDownLatch startFlag = new CountDownLatch(1);
  private final AtomicInteger successCount = new AtomicInteger();
  private final AtomicInteger finishedCount = new AtomicInteger();

  final Object lock = new Object();
  final ConcurrentTest concurrentTest;

  // TODO: only use existing ticks and stop if none are left (starvation, missed signals, etc)
  int tick = 0;

  // Increments the tick counter when all threads are blocked, waiting, or terminated, so as to 
  // allow waiting threads to continue. Also discovers deadlocks.
  private final TestThreadObserver threadObserver = new TestThreadObserver(this);
  
  public List<Throwable> getThrowables() {
    return Collections.unmodifiableList(throwables);
  }

  TestRun(ConcurrentTest concurrentTest) {
    this.concurrentTest = concurrentTest;
    throwables = new ArrayList<>(concurrentTest.sessionCount);
    for ( int i = 0; i < concurrentTest.sessionCount; i++ ) {
      throwables.add(null);
    }
  }

  void runOnce() {
    // start the threads, actually they will wait for the startflag
    for ( TestThread thread : concurrentTest.testThreads ) {
      thread.setTestRun(this);
      concurrentTest.pool.execute(thread);
    }
    // give all test threads the start signal
    startFlag.countDown();
    new Thread(threadObserver).start();
    try {
      awaitFinished();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void awaitFinished() throws InterruptedException {
    synchronized ( lock ) {
      while ( finishedCount.get() < concurrentTest.sessionCount 
          && threadObserver.getDeadlock() == null ) {
        lock.wait();
      }
    }
  }

  /**
   * Returns whether all test threads were successful.
   * 
   * @return true, iff all test threads succeeded
   */
  boolean success() {
    return successCount.get() == concurrentTest.testThreads.size();
  }
  
  boolean finished() {
    return finishedCount.get() == concurrentTest.testThreads.size();
  }
  
  /**
   * The number of successful test threads. That is, the number of threads that did not fail with
   * an exception or (assertion) error.
   * 
   * @return the number of successful test threads
   */
  int successCount() {
    return successCount.get();
  }

  public List<ThreadInfo> getDeadlock() {    
    return threadObserver.getDeadlock();
  }

  static class TestThread implements Runnable {
    private final List<Actions> blocks = new ArrayList<>();
    private int index;
    private TestRun test;

    TestThread() {
    }
    
    void setIndex(int index) {
      this.index = index;
    }
    
    private void setTestRun(TestRun test) {
      this.test = test;
    }
    
    public TestThread exec(Actions block) {
      this.blocks.add(block);
      return this;
    }

    public TestThread exec(NoArgActions block) {
      this.blocks.add(new NoArgActionsWrapper(block));
      return this;
    }

    @Override
    public void run() {
      try {
        test.startFlag.await();
        for ( Actions block : blocks ) {
          block.execute(this);
        }
        test.successCount.getAndIncrement();
      } catch ( Throwable t ) {
        test.throwables.set(index, t);
      } finally {
        test.finishedCount.getAndIncrement();
        test.lock.notify();
      }
    }

    public void waitFor(int tick) {
      try {
        synchronized ( test.lock ) {
          while ( test.tick < tick ) {
            test.lock.wait();
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

}
