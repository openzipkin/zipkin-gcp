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
package zipkin2.storage.stackdriver;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.module.storage.stackdriver.ZipkinStackdriverStorageModule;
import zipkin.module.storage.stackdriver.ZipkinStackdriverStorageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class ZipkinStackdriverStorageModuleTest {

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @AfterEach
  public void close() {
    context.close();
  }

  @Test void doesntProvideStorageComponent_whenStorageTypeNotStackdriver() {
    assertThrows(NoSuchBeanDefinitionException.class, () -> {
      TestPropertyValues.of("zipkin.storage.type:elasticsearch").applyTo(context);
      context.register(
              PropertyPlaceholderAutoConfiguration.class,
              ZipkinStackdriverStorageProperties.class,
              ZipkinStackdriverStorageModule.class,
              TestConfiguration.class);
      context.refresh();

      context.getBean(StackdriverStorage.class);
    });
  }

  @Test void providesStorageComponent_whenStorageTypeStackdriverAndProjectIdSet() {
    TestPropertyValues.of(
        "zipkin.storage.type:stackdriver",
        "zipkin.storage.stackdriver.project-id:zipkin",
        "zipkin.storage.type:stackdriver").applyTo(context);
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinStackdriverStorageModule.class,
        TestConfiguration.class);
    context.refresh();

    assertThat(context.getBean(StackdriverStorage.class)).isNotNull();
  }

  @Test void canOverrideProperty_apiHost() {
    TestPropertyValues.of(
        "zipkin.storage.type:stackdriver",
        "zipkin.storage.stackdriver.project-id:zipkin",
        "zipkin.storage.stackdriver.api-host:localhost").applyTo(context);
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinStackdriverStorageModule.class,
        TestConfiguration.class);
    context.refresh();

    assertThat(context.getBean(ZipkinStackdriverStorageProperties.class).getApiHost())
        .isEqualTo("localhost");
  }

  @Configuration
  static class TestConfiguration {
    @Bean("googleCredentials")
    public Credentials mockGoogleCredentials() throws IOException {
      return mock(GoogleCredentials.class);
    }
  }
}
