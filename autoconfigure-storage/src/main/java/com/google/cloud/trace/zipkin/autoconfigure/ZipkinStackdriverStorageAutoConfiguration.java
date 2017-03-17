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

package com.google.cloud.trace.zipkin.autoconfigure;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.trace.grpc.v1.GrpcTraceConsumer;
import com.google.cloud.trace.v1.consumer.TraceConsumer;
import com.google.cloud.trace.zipkin.StackdriverStorageComponent;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import io.netty.handler.ssl.OpenSsl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import zipkin.storage.StorageComponent;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;

/**
 * Auto-configuration to set the Stackdriver StorageComponent as the Zipkin storage backend.
 */
@Configuration
@EnableConfigurationProperties(ZipkinStackdriverStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "stackdriver")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinStackdriverStorageAutoConfiguration {
  private static final Logger log = LoggerFactory.getLogger(ZipkinStackdriverStorageAutoConfiguration.class);

  @Autowired
  ZipkinStackdriverStorageProperties storageProperties;

  @Bean(name = "stackdriverExecutor")
  @ConditionalOnMissingBean(ThreadPoolTaskExecutor.class) ThreadPoolTaskExecutor executor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ZipkinStackdriverStorage-");
    executor.setCorePoolSize(storageProperties.getExecutor().getCorePoolSize());
    executor.setMaxPoolSize(storageProperties.getExecutor().getMaxPoolSize());
    executor.setQueueCapacity(storageProperties.getExecutor().getQueueCapacity());
    executor.initialize();

    log.info("Configured Executor for ZipkinStackDriver Storage with: {}", storageProperties.getExecutor());
    return executor;
  }

  @Bean
  @ConditionalOnMissingBean(TraceConsumer.class)
  TraceConsumer traceConsumer(Credentials credentials) throws IOException {
    Preconditions.checkState(OpenSsl.isAvailable(), "OpenSsl required");
    return GrpcTraceConsumer.create(storageProperties.getApiHost(), credentials);
  }

  @Bean
  @ConditionalOnMissingBean(Credentials.class)
  Credentials googleCredentials() throws IOException
  {
    return GoogleCredentials.getApplicationDefault()
        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/trace.append"));
  }

  @Bean(name="projectId")
  String projectId() {
    String configuredProject = storageProperties.getProjectId();
    if (configuredProject != null && !configuredProject.isEmpty()) {
      return configuredProject;
    }
    try {
      return getDefaultProjectId();
    } catch (IOException exception) {
      throw new IllegalArgumentException("Missing required property: projectId");
    }
  }

  String getDefaultProjectId() throws IOException {
    URL url = new URL("http://metadata.google.internal/computeMetadata/v1/project/project-id");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestProperty("Metadata-Flavor", "Google");
    connection.setRequestMethod("GET");
    try (InputStream responseStream = connection.getInputStream())
    {
      String projectId = new String(ByteStreams.toByteArray(responseStream));
      return projectId;
    }
  }

  @Bean StorageComponent storage(@Qualifier("stackdriverExecutor") ThreadPoolTaskExecutor executor, TraceConsumer consumer, @Qualifier("projectId")
      String projectId) {
    return new StackdriverStorageComponent(projectId, consumer, executor);
  }
}
