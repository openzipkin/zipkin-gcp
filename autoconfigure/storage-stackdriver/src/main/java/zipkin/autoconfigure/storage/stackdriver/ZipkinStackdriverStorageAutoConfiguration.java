/*
 * Copyright 2016-2018 The OpenZipkin Authors
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
package zipkin.autoconfigure.storage.stackdriver;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.io.ByteStreams;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.netty.handler.ssl.OpenSsl;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.stackdriver.StackdriverStorage;

import static com.google.common.base.Preconditions.checkState;
import static io.grpc.CallOptions.DEFAULT;

@Configuration
@EnableConfigurationProperties(ZipkinStackdriverStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "stackdriver")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinStackdriverStorageAutoConfiguration {

  @Autowired ZipkinStackdriverStorageProperties storageProperties;

  @Bean
  @ConditionalOnMissingBean(Credentials.class)
  Credentials googleCredentials() throws IOException {
    return GoogleCredentials.getApplicationDefault()
        .createScoped(Collections.singletonList("https://www.googleapis.com/auth/trace.append"));
  }

  @Bean(name = "projectId")
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
    try (InputStream responseStream = connection.getInputStream()) {
      return new String(ByteStreams.toByteArray(responseStream), "UTF-8");
    }
  }

  @Bean(destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean
  ManagedChannel managedChannel(ZipkinStackdriverStorageProperties properties) {
    checkState(OpenSsl.isAvailable(), "OpenSsl required");
    return ManagedChannelBuilder.forTarget(properties.getApiHost()).build();
  }

  @Bean
  @ConditionalOnMissingBean
  StorageComponent storage(
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Qualifier("projectId") String projectId,
      ManagedChannel managedChannel,
      Credentials credentials) {
    return StackdriverStorage.newBuilder(managedChannel)
        .projectId(projectId)
        .strictTraceId(strictTraceId)
        .callOptions(DEFAULT.withCallCredentials(MoreCallCredentials.from(credentials)))
        .build();
  }
}
