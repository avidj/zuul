package org.avidj.zuul.core;

/*
 * #%L
 * zuul-core
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;

/**
 * A timer that upon timeout will release all locks held by the associated session. 
 */
final class SessionTimeoutTask extends TimerTask {
  private static final Logger LOG = LoggerFactory.getLogger(SessionTimeoutTask.class);
  private final LockManager lm;
  private final Session session;

  SessionTimeoutTask(LockManager lm, Session session) {
    this.lm = lm;
    this.session = session;
  }

  @Override
  public void run() {
    LOG.info("RELEASE BY TIMEOUT: " + session.id);
    lm.release(session.id);
  }
}
