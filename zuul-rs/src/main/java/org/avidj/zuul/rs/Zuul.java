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

import org.avidj.util.Strings;
import org.avidj.zuul.core.Lock;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

@Controller
@RequestMapping
public class Zuul {
  private static final Logger LOG = LoggerFactory.getLogger(Zuul.class);
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
   * Given a session id, this method returns all information about locks held by that session.
   * @param session the identifier of the session
   * @param request the HTTP request, provided by the REST framework
   * @param uriBuilder a URI builder, provided by the REST framework
   */
  @RequestMapping(value = "/s/{id}/**", method = RequestMethod.GET)
  @ResponseBody
  public Set<Lock> info(
      @PathVariable("id") String session,
      HttpServletRequest request,
      UriComponentsBuilder uriBuilder) {
    Set<Lock> locks = lm.getLocks(session);
    LOG.info("query info for session {}: {}", session, Strings.join(locks));
    return locks;
  }

  /**
   * Obtain, upgrade, or downgrade a lock for the given {@code session}. Upgrades and downgrades are
   * possible along two dimensions: type and scope. Lock types are read ({@literal aka.} shared) and
   * write ({@literal aka.} exclusive). Lock scopes are shallow and deep. A shallow lock is only 
   * with respect to the specified lock path, a deep lock also locks the whole subtree below that 
   * path.
   * 
   * @param session the session to obtain a lock for 
   * @param type the type of lock to obtain, possible values are ({@code r})ead and 
   *     ({@code w})rite, default is ({@code w})write  
   * @param scope the scope of lock to obtain, possible values are ({@code s})shallow and 
   *     ({@code d})eep, default is ({@code d})eep  
   * @param request the HTTP request, provided by the REST framework
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
    final List<String> path = getLockPath(request, session); 
    final LockType lockType = getLockType(type);
    final LockScope lockScope = getLockScope(scope);
    
    final boolean created = lm.lock(session, path, lockType, lockScope);
    HttpStatus httpStatus = created ? HttpStatus.CREATED : HttpStatus.FORBIDDEN;
    
    UriComponents uriComponents = 
        uriBuilder.path("/s/{id}/{lockPath}").buildAndExpand(session, Strings.join("/", path));
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
    final List<String> path = getLockPath(request, session); 
    
    final boolean deleted = lm.release(session, path);
    HttpStatus httpStatus = deleted ? HttpStatus.NO_CONTENT : HttpStatus.FORBIDDEN;
    
    UriComponents uriComponents = 
        uriBuilder.path("/s/{id}/{lockPath}").buildAndExpand(session, Strings.join("/", path));
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

  private static List<String> getLockPath(HttpServletRequest request, String session) {
    String matchedPath = 
        (String)request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    final int prefixLength = "/s/".length() + session.length();
    String lockPath = matchedPath.substring(prefixLength);
    if ( lockPath.startsWith("/") ) {
      lockPath = lockPath.substring(1);
    }
    return toLockPath(lockPath);
  }

  private static List<String> toLockPath(String lockPath) {
    if ( lockPath.isEmpty() ) { 
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(lockPath.split("/")));
  }
}
