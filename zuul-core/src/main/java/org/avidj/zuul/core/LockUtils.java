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

import java.util.Comparator;
import java.util.List;

final class LockUtils {
  private static final Comparator<List<String>> PATH_COMPARATOR = new Comparator<List<String>>() {
    @Override
    public int compare(List<String> o1, List<String> o2) {
      if ( o1 == o2 ) {
        return 0;
      }
      final int n = Math.min(o1.size(), o2.size()); 
      for ( int i = 0; i < n; i++ ) {
        int comp = o1.get(i).compareTo(o2.get(i));
        if ( comp != 0 ) {
          return comp;
        }
      }
      return ( o1.size() - o2.size() );
    }
  };

  private LockUtils() { /* hidden utility class constructor */ }
  
  static Comparator<List<String>> pathComparator() {
    return PATH_COMPARATOR;
  }

}
