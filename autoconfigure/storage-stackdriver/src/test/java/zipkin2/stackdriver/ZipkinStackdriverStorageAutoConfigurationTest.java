/**
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
package zipkin2.stackdriver;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.autoconfigure.stackdriver.ZipkinStackdriverStorageAutoConfiguration;
import zipkin.autoconfigure.stackdriver.ZipkinStackdriverStorageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;

public class ZipkinStackdriverStorageAutoConfigurationTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @After public void close() {
    context.close();
  }

  @Test
  public void doesntProvideStorageComponent_whenStorageTypeNotStackdriver() {
    addEnvironment(context, "zipkin.storage.type:elasticsearch");
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinStackdriverStorageProperties.class,
        ZipkinStackdriverStorageAutoConfiguration.class,
        TestConfiguration.class);
    context.refresh();

    thrown.expect(NoSuchBeanDefinitionException.class);
    context.getBean(StackdriverStorage.class);
  }

  @Test
  public void providesStorageComponent_whenStorageTypeStackdriverAndProjectIdSet() {
    addEnvironment(
        context,
        "zipkin.storage.type:stackdriver",
        "zipkin.storage.stackdriver.project-id:zipkin");
    addEnvironment(context, "zipkin.storage.type:stackdriver");
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinStackdriverStorageAutoConfiguration.class,
        TestConfiguration.class);
    context.refresh();

    assertThat(context.getBean(StackdriverStorage.class)).isNotNull();
  }

  @Test
  public void canOverrideProperty_apiHost() {
    addEnvironment(
        context,
        "zipkin.storage.type:stackdriver",
        "zipkin.storage.stackdriver.project-id:zipkin",
        "zipkin.storage.stackdriver.api-host:localhost");
    context.register(
        PropertyPlaceholderAutoConfiguration.class,
        ZipkinStackdriverStorageAutoConfiguration.class,
        TestConfiguration.class);
    context.refresh();

    assertThat(context.getBean(ZipkinStackdriverStorageProperties.class).getApiHost())
        .isEqualTo("localhost");
  }

  @Configuration static class TestConfiguration {
    @Bean("googleCredentials") public Credentials mockGoogleCredentials() throws IOException {
      return mock(GoogleCredentials.class);
    }

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel managedChannel(ZipkinStackdriverStorageProperties properties) {
      return ManagedChannelBuilder.forTarget(properties.getApiHost()).usePlaintext(true).build();
    }
  }
}
