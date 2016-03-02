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

import static com.jayway.restassured.module.mockmvc.RestAssuredMockMvc.*;
import static org.hamcrest.CoreMatchers.*;

import java.util.Arrays;
import java.util.Collections;

import org.avidj.zuul.core.DefaultEmbeddedLockManager;
import org.junit.Test;
import org.springframework.http.HttpStatus;

public class ZuulTest {

  @Test
  public void itShallCreateShallowReadLockOnRoot() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "r").param("s", "s")
        .when().put("/s/1/")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Collections.emptyList()))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("READ"))
        .and().body("scope", hasItem("SHALLOW"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallCreateShallowWriteLockOnRoot() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "w").param("s", "s")
        .when().put("/s/1/")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Collections.emptyList()))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("WRITE"))
        .and().body("scope", hasItem("SHALLOW"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallCreateDeepReadLockOnRoot() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "r").param("s", "d")
        .when().put("/s/1/")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Collections.emptyList()))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("READ"))
        .and().body("scope", hasItem("DEEP"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallCreateDeepWriteLockOnRoot() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "w").param("s", "d")
        .when().put("/s/1/")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Collections.emptyList()))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("WRITE"))
        .and().body("scope", hasItem("DEEP"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallCreateShallowReadLock() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "r").param("s", "s")
        .when().put("/s/1/a")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/a")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Arrays.asList("a")))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("READ"))
        .and().body("scope", hasItem("SHALLOW"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallCreateShallowWriteLock() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "w").param("s", "s")
        .when().put("/s/1/a/b")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Arrays.asList("a", "b")))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("WRITE"))
        .and().body("scope", hasItem("SHALLOW"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallCreateDeepReadLock() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "r").param("s", "d")
        .when().put("/s/1/a/b/c")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/a/b/c")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Arrays.asList("a", "b", "c")))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("READ"))
        .and().body("scope", hasItem("DEEP"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallCreateDeepWriteLock() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "w").param("s", "d")
        .when().put("/s/1/a/b/c")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul)
        .when().get("/s/1/a/b/c")
        .then().statusCode(HttpStatus.OK.value())
        .and().body("key", hasItem(Arrays.asList("a", "b", "c")))
        .and().body("session", hasItem("1"))
        .and().body("type", hasItem("WRITE"))
        .and().body("scope", hasItem("DEEP"))
        .and().body("count", hasItem(1));
  }

  @Test
  public void itShallRejectLockNestedIntoDeepLockOnRoot() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "w").param("s", "d")
        .when().put("/s/1/")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul).param("t", "r")
        .when().put("/s/2/foo/bar")
        .then().statusCode(HttpStatus.FORBIDDEN.value());
  }

  @Test
  public void itShallRejectLockNestedIntoDeepLock() {
    final Zuul zuul = createZuul();
    given()
        .standaloneSetup(zuul).param("t", "w").param("s", "d")
        .when().put("/s/1/foo")
        .then().statusCode(HttpStatus.CREATED.value());
    given()
        .standaloneSetup(zuul).param("t", "r")
        .when().put("/s/2/foo/bar")
        .then().statusCode(HttpStatus.FORBIDDEN.value());
  }

  private static Zuul createZuul() {
    Zuul zuul = new Zuul();
    zuul.setLockManager(new DefaultEmbeddedLockManager());
    return zuul;
  }
}
