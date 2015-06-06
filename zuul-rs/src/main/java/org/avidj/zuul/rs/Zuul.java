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

import org.avidj.zuul.core.DefaultLockManager;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockTreeNode;
import org.avidj.zuul.core.LockType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

//import javax.ws.rs.DELETE;
//import javax.ws.rs.GET;
//import javax.ws.rs.PUT;
//import javax.ws.rs.Path;
//import javax.ws.rs.PathParam;
//import javax.ws.rs.QueryParam;

//@Path("/")
@Controller
@RequestMapping
//@ResponseStatus
//@ExceptionHandler
public class Zuul {
  private static final String ACK = "ack";
  private final LockManager lm;
  
  @Autowired
  private WebApplicationContext context;
  
  public Zuul() {
    this.lm = new DefaultLockManager();
    Preconditions.checkNotNull(lm, "Lock manager must not be null");
  }

//  @GET
//  @Path("/p/{id}")
//  @RequestMapping(value = "/p/{id}", method = RequestMethod.GET)
  @RequestMapping(value = "/", method = RequestMethod.GET)
  @ResponseBody
  public String ping(@PathVariable("id") String id) {
    lm.heartbeat(id);
    return ACK;
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
  @RequestMapping(value = "/s/{id}/**", method = RequestMethod.PUT)
  public String lock(
      @PathVariable("id") String session, 
      @RequestParam(value = "t", defaultValue = "w") String type,
      @RequestParam(value = "s", defaultValue = "s") String scope,
      HttpServletRequest request) {
    String matchedPath = 
        (String)request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    final String lockPath = matchedPath.substring(4 + session.length());
    final List<String> path = Collections.unmodifiableList(Arrays.asList(lockPath.split("/")));
    final LockType lockType = ( "r".equals(type) ) ? LockType.READ : LockType.WRITE;
    final LockScope lockScope = ( "s".equals(scope) ) ? LockScope.SHALLOW : LockScope.DEEP;
    return Boolean.toString(lm.lock(session, path, lockType, lockScope));
  }

//  /**
//   * Release the given lock if it is held by the given {@code session}.
//   * @param session the session to release the lock for
//   * @param pathParam the lock path
//   * @return {@code true}, iff the lock was released
//   */
  @RequestMapping(value = "/s/{id}/{path}", method = RequestMethod.GET)
  public String lock(
      @PathVariable("id") String session, 
      @PathVariable("path") String pathParam) {
    final List<String> path = Collections.unmodifiableList(Arrays.asList(pathParam.split("/")));
    return Boolean.toString(lm.release(session, path));
  }
//
//  /**
//   * Select information on all locks held by the given {@code session}.
//   * @param session the session to retrieve lock information about
//   * @return all locks held by the session
//   */
//  @RequestMapping(value = "/s/{id}", method = RequestMethod.GET)
//  public Session getSession(@PathVariable("id") String session) {
////    // TODO: The result should be represented as a collections of lock trees rooted under session
////    return lm.getSession(id);
//    return null;
//  }
//
//  /**
//   * Select information on the locks on the given node and the subtree beneath it. This will 
//   * <em>not</em> include information on deep ancestor locks.
//   * 
//   * @param pathParam the lock path
//   * @return lock information on the node and all nested nodes
//   */
//  @RequestMapping(value = "/l/{path:.*}", method = RequestMethod.GET)
//  public LockPathComponent getLockTreeNode(@PathVariable("path") String pathParam) {
//    // TODO: The result should be single lock tree rooted under id
//    final List<String> path = Collections.unmodifiableList(Arrays.asList(pathParam.split("/")));
//    LockTreeNode node = lm.getRoot().getChild(pathParam); // Find a session object
//    if ( node == null ) {
//      return null;
//    }
//    return new LockPathComponent(lm, node);
//  }
}
