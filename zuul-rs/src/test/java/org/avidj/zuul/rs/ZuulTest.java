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

import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.post;
import static org.hamcrest.CoreMatchers.equalTo;

import org.junit.Test;

public class ZuulTest {
  
  @Test
  public void testWDRoot() {
    post("/").then().assertThat().body("lotto.lottoId", equalTo(5));
  }

  @Test
  public void testRDRoot() {
    post("/").then().assertThat().body("lotto.lottoId", equalTo(5));
  }
  
  @Test
  public void testWSRoot() {
    post("/").then().assertThat().body("lotto.lottoId", equalTo(5));
  }

  @Test
  public void testRSRoot() {
    post("/").then().assertThat().body("lotto.lottoId", equalTo(5));
  }
}
