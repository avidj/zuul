package org.avidj.zuul.rs;

/*
 * #%L
 * zuul-rs
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
import org.avidj.zuul.core.LockTreeNode;

import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

public class Session {
  private final LockManager lm;
  final String id;

  
  // TODO: manage locks HERE!
  
  Session(LockManager lm, String id) {
    assert ( lm != null );
    assert ( id != null );
    this.lm = lm;
    this.id = id;
  }
  
  @DELETE
  public void delete() {
    lm.release(id);
  }

  @Path("/{id}")
  public LockPathComponent root(@PathParam("id") @Encoded String id) {
    // The lock tree node if it exists (else null)
    LockTreeNode node = lm.getRoot().getChild(id);
    if ( node == null ) {
      return null;
    }
    return new LockPathComponent(lm, node);
  }

}
