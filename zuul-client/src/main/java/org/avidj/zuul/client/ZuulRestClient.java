package org.avidj.zuul.client;

/*
 * #%L
 * zuul-client
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
import com.google.common.collect.ImmutableMap;

import org.avidj.zuul.core.Lock;
import org.avidj.zuul.core.LockManager;
import org.avidj.zuul.core.LockScope;
import org.avidj.zuul.core.LockType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ZuulRestClient implements LockManager {
  private static final Logger LOG = LoggerFactory.getLogger(ZuulRestClient.class);
  private static final Map<LockScope, String> SCOPE_TO_PARAM = ImmutableMap.of(
      LockScope.DEEP, "d",
      LockScope.SHALLOW, "s");
  private static final Map<LockType, String> TYPE_TO_PARAM = ImmutableMap.of(
      LockType.READ, "r",
      LockType.WRITE, "w");
  private final String serviceUrl;
  
  /**
   * Create a new REST client for Zuul.
   * @param serviceUrl the URL for accessing the Zuul lock service. 
   */
  public ZuulRestClient(String serviceUrl) {
    Preconditions.checkNotNull(serviceUrl);
    checkIsUrl(serviceUrl);
    if ( !serviceUrl.endsWith("/") ) {
      serviceUrl += "/";
    }
    this.serviceUrl = serviceUrl;
  }
  
  @Override
  public void setSessionTimeout(long timeoutMillis) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Set<Lock> getLocks(String session) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean readLock(String session, List<String> path, LockScope scope) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean writeLock(String session, List<String> path, LockScope scope) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean lock(String sessionId, List<String> path, LockType type, LockScope scope) {
    RestTemplate restTemplate = new RestTemplate();

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);
    
    Map<String, String> parameters = new HashMap<>();
    parameters.put("id", sessionId); // set the session id
    UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(serviceUrl + lockPath(path))
        .queryParam("t", type(type))
        .queryParam("s", scope(scope));
            
    ResponseEntity<String> result = restTemplate.exchange(
        uriBuilder.build().encode().toUri(), 
        HttpMethod.PUT, 
        entity, 
        String.class);

    LOG.info(result.toString());
    HttpStatus code = result.getStatusCode();
    return code.equals(HttpStatus.CREATED);
  }

  private static String scope(LockScope scope) {
    String result = SCOPE_TO_PARAM.get(scope);
    if ( result == null ) {
      throw new IllegalArgumentException("unknown lock scope: " + scope);
    }
    return result; 
  }

  private static String type(LockType type) {
    String result = TYPE_TO_PARAM.get(type);
    if ( result == null ) {
      throw new IllegalArgumentException("unknown lock type: " + type);
    }
    return result; 
  }

  @Override
  public boolean multiLock(String sessionId, List<List<String>> paths, LockType type,
      LockScope scope) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean release(String session, List<String> path) throws IllegalStateException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public int release(String session, Collection<List<String>> paths) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void release(String session) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean upScope(String session, List<String> path, LockType type) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public boolean downScope(String session, List<String> path, LockType type, LockScope shallow) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public void heartbeat(String session) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  private static String lockPath(List<String> path) {
    if ( path.isEmpty() ) {
      return "";
    }    
    StringBuilder string = new StringBuilder(path.get(0));
    for ( int i = 1, n = path.size(); i < n; i++ ) {
      string.append("/").append(path.get(i));
    }
    return string.toString();
  }

  private static void checkIsUrl(String serviceUrl) {
    try {
      new URL(serviceUrl);
    } catch ( MalformedURLException e ) {
      throw new IllegalArgumentException("Not a valid URL.", e);
    }
  }
}
