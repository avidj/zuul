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

import com.google.common.base.Preconditions;

import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockTreeNode;
import org.avidj.zuul.core.LockType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

@Path("/")
public class LockManagerRs {
  private final LockManager lm;
  
  public LockManagerRs(LockManager lm) {
    Preconditions.checkNotNull(lm, "Lock manager must not be null");
    this.lm = lm;
  }

  @GET
  @Path("/p/{id}")
  public void ping(@PathParam("id") String id) {
    lm.heartbeat(id);
  }

  @PUT
  @Path("/s/{id}/{path:.*}")
  public boolean lock(
      @PathParam("id") String session, 
      @PathParam("path") String pathParam,
      @QueryParam("t") String type,
      @QueryParam("s") String scope) {
    final List<String> path = Collections.unmodifiableList(Arrays.asList(pathParam.split("/")));
    final LockType lockType = ( "r".equals(type) ) ? LockType.READ : LockType.WRITE;
    final LockScope lockScope = ( "s".equals(scope) ) ? LockScope.SHALLOW : LockScope.DEEP;
    return lm.lock(session, path, lockType, lockScope);
  }

  @DELETE
  @Path("/s/{id}/{path:.*}")
  public boolean lock(
      @PathParam("id") String session, 
      @PathParam("path") String pathParam) {
    final List<String> path = Collections.unmodifiableList(Arrays.asList(pathParam.split("/")));
    return lm.release(session, path);
  }

  @GET
  @Path("/s/{id}")
  public Session getSession(@PathParam("id") String id) {
//    // TODO: The result should be represented as a collections of lock trees rooted under session
//    return lm.getSession(id);
    return null;
  }

  @GET
  @Path("/l/{id}")
  public LockPathComponent getLockTreeNode(@PathParam("id") String id) {
    // TODO: The result should be single lock tree rooted under id
    LockTreeNode node = lm.getRoot().getChild(id); // Find a session object
    if ( node == null ) {
      return null;
    }
    return new LockPathComponent(lm, node);
  }
}
