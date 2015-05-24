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
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockTreeNode;

import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

public class LockPathComponent {
  private final LockTreeNode node;
  private final LockManager lm;
  
  LockPathComponent(LockManager lm, LockTreeNode node) {    
    assert ( lm != null );
    assert ( node != null );
    this.lm = lm;
    this.node = node;
  }

  @PUT
  @Path("/{id}")
  public void deepWriteLock(@PathParam("id") @Encoded String id) {
//    boolean success = lm.writeLock(id, node.path, LockScope.DEEP);
  }

  @GET
  @Path("/{id}")
  public LockPathComponent child(@PathParam("id") @Encoded String id) {
    LockTreeNode child = node.getChild(id);
    if ( child == null ) {
      return null;
    }
    return new LockPathComponent(lm, child);
  }
}
