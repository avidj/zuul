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
import java.util.List;

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

  /**
   * Obtain, upgrade, or downgrade a lock for the given {@code sesion}. Upgrades and downgrades are
   * possible along two dimensions: type and scope. Lock types are read ({@literal aka.} shared) and
   * write ({@literal aka.} exlusive). Lock scopes are shallow and deep. A shallow lock is only with
   * respect to the specified lock path, a deep lock also locks the whole subtree below that path.
   * 
   * @param session the session to lock 
   * @param pathParam the lock path
   * @param type the type of lock to obtain, possible values are ({@code r})ead and 
   *     ({@code w})rite, default is ({@code w})write  
   * @param scope the scope of lock to obtain, possible values are ({@code s})shallow and 
   *     ({@code d})eep, default is ({@code d})eep  
   * @return {@code true}, iff the operation was successful
   */
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

  /**
   * Release the given lock if it is held by the given {@code session}.
   * @param session the session to release the lock for
   * @param pathParam the lock path
   * @return {@code true}, iff the lock was released
   */
  @DELETE
  @Path("/s/{id}/{path:.*}")
  public boolean lock(
      @PathParam("id") String session, 
      @PathParam("path") String pathParam) {
    final List<String> path = Collections.unmodifiableList(Arrays.asList(pathParam.split("/")));
    return lm.release(session, path);
  }

  /**
   * Select information on all locks held by the given {@code session}.
   * @param session the session to retrieve lock information about
   * @return all locks held by the session
   */
  @GET
  @Path("/s/{id}")
  public Session getSession(@PathParam("id") String session) {
//    // TODO: The result should be represented as a collections of lock trees rooted under session
//    return lm.getSession(id);
    return null;
  }

  /**
   * Select information on the locks on the given node and the subtree beneath it. This will 
   * <em>not</em> include information on deep ancestor locks.
   * 
   * @param pathParam the lock path
   * @return lock information on the node and all nested nodes
   */
  @GET
  @Path("/l/{path:.*}")
  public LockPathComponent getLockTreeNode(@PathParam("path") String pathParam) {
    // TODO: The result should be single lock tree rooted under id
    final List<String> path = Collections.unmodifiableList(Arrays.asList(pathParam.split("/")));
    LockTreeNode node = lm.getRoot().getChild(pathParam); // Find a session object
    if ( node == null ) {
      return null;
    }
    return new LockPathComponent(lm, node);
  }
}
