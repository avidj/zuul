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
import org.avidj.zuul.core.LockType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping
public class Zuul {
  private static final String ACK = "ack";
  
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private LockManager lm;
  
  void setLockManager(LockManager lm) {
    this.lm = lm;
  }

  @RequestMapping(value = "/p/{id}", method = RequestMethod.GET)
  @ResponseBody
  public String ping(@PathVariable("id") String id) {
    lm.heartbeat(id);
    return ACK;
  }

  /**
   * Obtain, upgrade, or downgrade a lock for the given {@code session}. Upgrades and downgrades are
   * possible along two dimensions: type and scope. Lock types are read ({@literal aka.} shared) and
   * write ({@literal aka.} exlusive). Lock scopes are shallow and deep. A shallow lock is only with
   * respect to the specified lock path, a deep lock also locks the whole subtree below that path.
   * 
   * @param session the session to obtain a lock for 
   * @param type the type of lock to obtain, possible values are ({@code r})ead and 
   *     ({@code w})rite, default is ({@code w})write  
   * @param scope the scope of lock to obtain, possible values are ({@code s})shallow and 
   *     ({@code d})eep, default is ({@code d})eep  
   * @param uriBuilder builder for the result location URI
   * @return {@code true}, iff the operation was successful
   */
  @RequestMapping(value = "/s/{id}/**", method = RequestMethod.PUT)
  public ResponseEntity<String> lock(
      @PathVariable("id") String session, 
      @RequestParam(value = "t", defaultValue = "w") String type,
      @RequestParam(value = "s", defaultValue = "s") String scope,
      HttpServletRequest request,
      UriComponentsBuilder uriBuilder) {
    final String lockPathParam = getLockPathParam(request, 4 + session.length()); 
    final List<String> path = getLockPath(lockPathParam);
    final LockType lockType = getLockType(type);
    final LockScope lockScope = getLockScope(scope);
    
    final boolean created = lm.lock(session, path, lockType, lockScope);
    HttpStatus httpStatus = created ? HttpStatus.CREATED : HttpStatus.FORBIDDEN;
    
    UriComponents uriComponents = 
        uriBuilder.path("/s/{id}/{lockPath}").buildAndExpand(session, lockPathParam);
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(uriComponents.toUri());
    return new ResponseEntity<String>(headers, httpStatus);
  }

  /**
   * Release the given lock if it is held by the given {@code session}.
   * @param session the session id to release the lock for
   * @param request the request
   * @param uriBuilder a builder for the response location header URI
   * @return {@code true}, iff the lock was released
   */
  @RequestMapping(value = "/s/{id}/**", method = RequestMethod.DELETE)
  public ResponseEntity<String> release(
      @PathVariable("id") String session, 
      HttpServletRequest request,
      UriComponentsBuilder uriBuilder) {
    final String lockPathParam = getLockPathParam(request, 4 + session.length()); 
    final List<String> path = getLockPath(lockPathParam);
    
    final boolean deleted = lm.release(session, path);
    HttpStatus httpStatus = deleted ? HttpStatus.NO_CONTENT : HttpStatus.FORBIDDEN;
    
    UriComponents uriComponents = 
        uriBuilder.path("/s/{id}/{lockPath}").buildAndExpand(session, lockPathParam);
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(uriComponents.toUri());
    return new ResponseEntity<String>(headers, httpStatus);
  }

  private static LockScope getLockScope(String scope) {
    return ( "s".equals(scope) ) ? LockScope.SHALLOW : LockScope.DEEP;
  }

  private static LockType getLockType(String type) {
    return ( "r".equals(type) ) ? LockType.READ : LockType.WRITE;
  }

  private static String getLockPathParam(HttpServletRequest request, int prefixLength) {
    String matchedPath = 
        (String)request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    final String lockPath = matchedPath.substring(prefixLength);
    return lockPath;
  }

  private static List<String> getLockPath(String lockPath) {
    if ( lockPath.isEmpty() ) { 
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(lockPath.split("/")));
  }

  //  @RequestMapping(value = "/s/{id}/{path}", method = RequestMethod.GET)
//  public String getLocks(
//      @PathVariable("id") String session, 
//      @PathVariable("path") String pathParam) {
//    final List<String> path = Collections.unmodifiableList(Arrays.asList(pathParam.split("/")));
//    return Boolean.toString(lm.release(session, path));
//  }
//
//  /**
//   * Select information on all locks held by the given {@code session}.
//   * @param session the session to retrieve lock information about
//   * @return all locks held by the session
//   */
//  @RequestMapping(value = "/s/{id}", method = RequestMethod.GET)
//  public Session getSession(@PathVariable("id") String session) {
////    // TODO: The result should be represented as a collections of lock trees rooted under 
////    // session
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
