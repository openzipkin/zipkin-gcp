/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.trace.zipkin;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * A drop-in replacement for the Zipkin HTTP Collector that writes to the Stackdriver Trace backend.
 * This does not support the entire set operations exposed by Zipkin server. Only write operations
 * are supported. The HTTP endpoints for the UI and read operations are not provided.
 */
@SpringBootApplication
@EnableStackdriverCollector
public class StackdriverZipkinCollector {
  /**
   * Default .yml/.yaml file names to scan
   */
  protected static final String ZIPKIN_CONFIG_NAMES = "spring.config.name=zipkin-server,stackdriver-zipkin-server";

  public static void main(String[] args) {
    System.setProperty("stackdriver.trace.zipkin.agent", "zipkin-java-collector");
    new SpringApplicationBuilder(StackdriverZipkinCollector.class)
        .properties(ZIPKIN_CONFIG_NAMES).run(args);
  }
}
