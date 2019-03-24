/*
 * Copyright 2016-2019 The OpenZipkin Authors
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
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import io.netty.handler.ssl.OpenSsl;
import io.netty.util.internal.PlatformDependent;
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

  @Bean
  @ConditionalOnMissingBean
  ClientFactory clientFactory() {
    return ClientFactory.DEFAULT;
  }

  @Bean
  @ConditionalOnMissingBean
  StorageComponent storage(
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Qualifier("projectId") String projectId,
      ClientFactory clientFactory,
      ZipkinStackdriverStorageProperties properties,
      Credentials credentials) {
    checkState(OpenSsl.isAvailable() || jettyAlpnAvailable(),
        "OpenSsl or ALPN is required. This usually requires either JDK9+, jetty-alpn, or netty-tcnative-boringssl-static");
    return StackdriverStorage.newBuilder(properties.getApiHost())
        .projectId(projectId)
        .strictTraceId(strictTraceId)
        .clientFactory(clientFactory)
        .clientOptions(new ClientOptionsBuilder()
            .decorator(CredentialsDecoratingClient.newDecorator(credentials))
            .build())
        .build();
  }

  // ALPN check from https://github.com/netty/netty/blob/1065e0f26e0d47a67c479b0fad81efab5d9438d9/handler/src/main/java/io/netty/handler/ssl/JettyAlpnSslEngine.java
  private static boolean jettyAlpnAvailable() {
    if (PlatformDependent.javaVersion() <= 8) {
      try {
        // Always use bootstrap class loader.
        Class.forName("sun.security.ssl.ALPNExtension", true, null);
        return true;
      } catch (Throwable ignore) {
        // alpn-boot was not loaded.
      }
    }
    return false;
  }
}
