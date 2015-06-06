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

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

/**
 * This web application initializer creates the components and wires them together. Then it adds a
 * servlet to handle requests and adds a generic URL mapping.
 */
public class ZuulInitializer implements WebApplicationInitializer {

  @Override
  public void onStartup(ServletContext container) {
    // Create the 'root' Spring application context
    AnnotationConfigWebApplicationContext rootContext =
        new AnnotationConfigWebApplicationContext();
    rootContext.register(RootContextConfiguration.class);

    // Manage the lifecycle of the root application context
    container.addListener(new ContextLoaderListener(rootContext));

    // Create the dispatcher servlet's Spring application context
    AnnotationConfigWebApplicationContext dispatcherContext =
        new AnnotationConfigWebApplicationContext();
    dispatcherContext.register(MvcContextConfiguration.class);

    // Register and map the dispatcher servlet
    ServletRegistration.Dynamic dispatcher =
        container.addServlet("dispatcher", new DispatcherServlet(dispatcherContext));
    dispatcher.setLoadOnStartup(1);
    dispatcher.addMapping("/");
  }

  /**
   * The root application context, i.e., the components implementing the functionality.
   */
  @Configuration
  @ComponentScan ( basePackages = "org.avidj.zuul.core" )
  public static class RootContextConfiguration {
  }

  /**
   * The web application context, i.e., making the functionality available as a RESTful service.
   */
  @Configuration
  @EnableWebMvc
  @ComponentScan ( basePackages = "org.avidj.zuul.rs" )
  public static class MvcContextConfiguration {
  }
}
