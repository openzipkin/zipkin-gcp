/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
