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
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

@EnableWebMvc
public class ZuulInitializer implements WebApplicationInitializer {

  @Override
  public void onStartup(ServletContext container) {
    XmlWebApplicationContext ctx = new XmlWebApplicationContext();
    ctx.setConfigLocation("WEB-INF/zuul-context.xml");
    
//    AnnotationConfigWebApplicationContext ctx =
//        new AnnotationConfigWebApplicationContext();    
//    ctx.register(ContextConfiguration.class);
//    ctx.refresh();
    
    // Manage the lifecycle of the root application context
    container.addListener(new org.springframework.web.context.ContextLoaderListener(ctx));
    
 // Create the dispatcher servlet's Spring application context
//    AnnotationConfigWebApplicationContext dispatcherContext =
//        new AnnotationConfigWebApplicationContext();
//    dispatcherContext.register(DispatcherConfig.class);
    
 // Register and map the dispatcher servlet
    ServletRegistration.Dynamic dispatcher =
        container.addServlet("dispatcher", new DispatcherServlet(ctx));
    dispatcher.setLoadOnStartup(1);
    dispatcher.addMapping("/");
    
//    //XmlWebApplicationContext appContext = new XmlWebApplicationContext();
//    //appContext.setConfigLocation("/WEB-INF/spring/dispatcher-config.xml");
////    WebApplicationContext context = getContext();
//    //servletContext.addListener(new ContextLoaderListener(context));
//    ServletRegistration.Dynamic dispatcher =
//        container.addServlet("dispatcher", new DispatcherServlet(ctx));
//    dispatcher.setLoadOnStartup(1);
//    dispatcher.addMapping("/*");
  }
  
  @Configuration
  @ComponentScan ( basePackages = "org.avidj.zuul.rs" )
  public static class ContextConfiguration {
    
  }

}
