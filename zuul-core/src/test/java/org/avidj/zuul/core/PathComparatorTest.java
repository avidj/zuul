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

import static org.avidj.zuul.core.LockManagerTest.key;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PathComparatorTest {
  
  @Test
  public void testEquals() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(1), key(1)), is(0));
  }

  @Test
  public void testLess() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(1), key(2)), is(-1));
  }

  @Test
  public void testGreater() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(2), key(1)), is(1));
  }

  @Test
  public void testPrefixLeft() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(1, 2), key(1)), is(1));
  }

  @Test
  public void testEntirelyDifferent() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(1, 2), key(3, 4)), is(lessThan(0)));
  }

  @Test
  public void testLessThird() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(1, 2, 3), key(1, 2, 4)), is(-1));
  }

  @Test
  public void testGreaterMiddle() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(1, 3, 3), key(1, 2, 3)), is(1));
  }

  @Test
  public void testLessSecond() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(2, 1), key(2, 3)), is(lessThan(0)));
  }

  @Test
  public void testEmptyLast() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(Collections.emptyList(), key(1, 2)), is(lessThan(0)));
  }

  @Test
  public void testEmptyFirst() {
    Comparator<List<String>> comparator = LockUtils.pathComparator();
    assertThat(comparator.compare(key(1, 2), Collections.emptyList()), is(greaterThan(0)));
  }
}
