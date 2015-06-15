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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Utility for executing concurrent test with the purpose to reveal concurrency bugs such as 
 * deadlocks or missing mutual exclusion. All threads passing does not guarantee that there are
 * no concurrency bugs. An appropriate number of repetitions must be executed to reveal concurrency 
 * bugs, which cannot ever be guaranteed. The number of repetitions can easily be in the order of 
 * several thousands. 
 */
public class ConcurrentTest {
  private static final long SLEEP_INTERVAL = 5;
  private final ExecutorService pool = 
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r);
          threads.add(t);
          return t;
        }
      });
  private final List<TestThread> testThreads;
  private final List<Thread> threads;
  private final List<Throwable> throwables;
  private final CountDownLatch startFlag = new CountDownLatch(1);
  private final CountDownLatch finished;
  private final AtomicInteger successCount = new AtomicInteger();
  private final Object lock = new Object();
  private int tick = 0;
  private int repeat = 1;
  private int killAfter;
  private int nextIndex = 0;
  private boolean done = false;
  
  // Increments the tick counter when all threads are blocked, waiting, or terminated, so as to 
  // allow waiting threads to continue
  private final Runnable threadObserver = new Runnable() {
    @Override
    public void run() {
      while ( finished.getCount() > 0 ) {
        if ( noThreadsRunning() ) {
          synchronized ( lock ) {
            if ( noThreadsRunning() ) {
              tick++;
              lock.notifyAll();
            }
          }
        }
        try {
          Thread.sleep(SLEEP_INTERVAL );
        } catch (InterruptedException e) {
          // this cannot happen
        }
      }
    }
    private boolean noThreadsRunning() {
      for ( Thread t : threads ) {
        if ( t.getState() == Thread.State.RUNNABLE ) {
          return false;
        }
      }
      return true;
    }
  };

  private ConcurrentTest(int sessionCount) {
    testThreads = new ArrayList<>(sessionCount);
    threads = new ArrayList<>(sessionCount);
    finished = new CountDownLatch(sessionCount);
    throwables = new ArrayList<>(sessionCount);
    for ( int i = 0; i < sessionCount; i++ ) {
      throwables.add(null);
    }
  }

  /**
   * Create a concurrent test with any number of test threads. This method follows the builder 
   * pattern in that the returned test can be further configured by calls to the other public 
   * methods before running it.
   * 
   * @param t0 the first test thread
   * @param more any number of additional test threads
   * @return this
   */
  public static ConcurrentTest threads(TestThread t0, TestThread... more) {
    ConcurrentTest testUtil = new ConcurrentTest(more.length + 1);
    testUtil.add(t0);
    for ( TestThread t : more ) {
      testUtil.add(t);
    }
    return testUtil;
  }

  private void add(TestThread testThread) {
    testThread.setIndex(nextIndex++);
    testThread.setConcurrentTest(this);
    testThreads.add(testThread);
  }

  /**
   * In case of deadlocks, the test must be aborted, i.e., threads must be killed. 
   * @param killAfterMs the maximum number of milliseconds to wait for the test to end
   * @return this
   */
  public ConcurrentTest killAfter(int killAfterMs) {
    this.killAfter = killAfterMs;
    return this;
  }

  /**
   * The number of repetitions of the test. This must be sufficiently high for finding concurrency
   * bugs. The default of 1 is only sufficient for actually sequential tests.
   * @param repeat the number of repetitions to do
   * @return this
   */
  public ConcurrentTest repeat(int repeat) {
    this.repeat = repeat;
    return this;
  }

  /**
   * Execute the test.
   * @return this
   */
  public ConcurrentTest run() {
    if ( done ) {
      throw new IllegalStateException("This test was already executed.");
    }
    done = true;
    // Repetitions increase the probability to find erroneous interleavings of operations.
    for ( int i = 0; i < repeat; i++ ) {
      reset();
      runOnce();
//      assertThat(successCount(), is(successCount));
    }
    return this;
  }

  private void reset() {
    // TODO Auto-generated method stub
    
  }

  private void runOnce() {
    // start the threads, actually they will wait for the startflag
    for ( TestThread thread : testThreads ) {
      pool.execute(thread);
    }
    // give all test threads the start signal
    startFlag.countDown();
    new Thread(threadObserver).start();
    // TODO: defeat deadlocks
    try {
      finished.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Returns whether all test threads were successful.
   * 
   * @return true, iff all test threads succeeded
   */
  public boolean success() {
    return successCount.get() == testThreads.size();
  }

  /**
   * The number of successful test threads. That is, the number of threads that did not fail with
   * an exception or (assertion) error.
   * 
   * @return the number of successful test threads
   */
  public int successCount() {
    return successCount.get();
  }

  public List<Throwable> getThrowables() {
    return Collections.unmodifiableList(throwables);
  }

  public static TestThread thread() {
    return new TestThread();
  }
  
  public static class TestThread implements Runnable {
    private final List<Actions> blocks = new ArrayList<>();
    private int index;
    private ConcurrentTest test;

    private TestThread() {
    }
    
    private void setIndex(int index) {
      this.index = index;
    }
    
    private void setConcurrentTest(ConcurrentTest test) {
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
        // signal that this thread is done
        test.finished.countDown(); 
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
  
  @FunctionalInterface
  public interface Actions {
    /**
     * @param t the test thread, can be used for waiting for ticks within blocks
     */
    abstract void execute(TestThread t);
  }

  @FunctionalInterface
  public interface NoArgActions {
    abstract void execute();
  }
  
  // A wrapper to adapt the Actions interface to the NoArgActions interface
  private static class NoArgActionsWrapper implements Actions {
    private final NoArgActions actions;
    
    private NoArgActionsWrapper(NoArgActions actions) {
      this.actions = actions;
    }
    
    @Override
    public void execute(TestThread t) {
      actions.execute();
    }
  }
}
