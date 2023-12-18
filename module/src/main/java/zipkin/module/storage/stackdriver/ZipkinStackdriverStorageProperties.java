/*
 * Copyright 2016-2023 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.module.storage.stackdriver;

import java.io.Serializable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("zipkin.storage.stackdriver")
public class ZipkinStackdriverStorageProperties implements Serializable { // for Spark jobs
  private static final long serialVersionUID = 0L;

  /**
   * Sets the level of logging for HTTP requests made by the Stackdriver client. If not set or
   * none, logging will be disabled.
   */
  enum HttpLogging {
    NONE,
    BASIC,
    HEADERS
  }

  private String projectId;
  private String apiHost = "cloudtrace.googleapis.com:443";
  /** When set, controls the volume of HTTP logging of the Stackdriver Trace Api. */
  private HttpLogging httpLogging = HttpLogging.NONE;

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getApiHost() {
    return apiHost;
  }

  public void setApiHost(String apiHost) {
    this.apiHost = "".equals(apiHost) ? null : apiHost;
  }

  public HttpLogging getHttpLogging() {
    return httpLogging;
  }

  public void setHttpLogging(HttpLogging httpLogging) {
    this.httpLogging = httpLogging;
  }
}
