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

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientOptions;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.logging.LoggingClient;
import com.linecorp.armeria.client.logging.LoggingClientBuilder;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.logging.LogLevel;
import io.netty.handler.ssl.OpenSsl;
import io.netty.util.internal.PlatformDependent;
import java.io.IOException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.module.storage.stackdriver.ZipkinStackdriverStorageProperties.HttpLogging;
import zipkin2.Call;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.stackdriver.StackdriverStorage;

@Configuration
@EnableConfigurationProperties(ZipkinStackdriverStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "stackdriver")
@ConditionalOnMissingBean(StorageComponent.class)
public class ZipkinStackdriverStorageModule {

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
    } catch (Throwable t) {
      Call.propagateIfFatal(t);
      throw new IllegalArgumentException("Missing required property: projectId");
    }
  }

  String getDefaultProjectId() {
    WebClient client = WebClient.of("http://metadata.google.internal/");
    return client.execute(RequestHeaders.of(
            HttpMethod.GET, "/computeMetadata/v1/project/project-id",
            "Metadata-Flavor", "Google"))
        .aggregate()
        .join()
        .contentUtf8();
  }

  @Bean
  @ConditionalOnMissingBean
  ClientFactory clientFactory() {
    return ClientFactory.ofDefault();
  }

  @Bean
  @ConditionalOnMissingBean
  StorageComponent storage(
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Qualifier("projectId") String projectId,
      ClientFactory clientFactory,
      ZipkinStackdriverStorageProperties properties,
      Credentials credentials) {
    if (!OpenSsl.isAvailable() && !jettyAlpnAvailable()) {
      throw new IllegalStateException(
          "OpenSsl or ALPN is required. This usually requires either JDK9+, jetty-alpn, or "
              + "netty-tcnative-boringssl-static");
    }

    ClientOptionsBuilder options = ClientOptions.builder();

    HttpLogging httpLogging = properties.getHttpLogging();
    if (httpLogging != HttpLogging.NONE) {
      LoggingClientBuilder loggingBuilder = LoggingClient.builder()
          .requestLogLevel(LogLevel.INFO)
          .successfulResponseLogLevel(LogLevel.INFO);
      switch (httpLogging) {
        case HEADERS:
          loggingBuilder.contentSanitizer((unused1, unused2) -> "");
          break;
        case BASIC:
          loggingBuilder.contentSanitizer((unused1, unused2) -> "");
          loggingBuilder.headersSanitizer((unused1, unused2) -> HttpHeaders.of());
          break;
        default:
          break;
      }
      options.decorator(loggingBuilder.newDecorator());
    }

    return StackdriverStorage.newBuilder(properties.getApiHost())
        .projectId(projectId)
        .strictTraceId(strictTraceId)
        .clientFactory(clientFactory)
        .clientOptions(options
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
        return false;
      }
    }
    // Java 9+ don't need ALPN so we don't even check for it.
    return true;
  }
}
