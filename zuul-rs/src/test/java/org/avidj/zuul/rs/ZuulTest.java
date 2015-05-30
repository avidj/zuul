package org.avidj.zuul.rs;

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
