package com.google.cloud.trace.zipkin;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.trace.grpc.v1.GrpcTraceConsumer;
import com.google.cloud.trace.v1.TraceServiceClient;
import com.google.cloud.trace.v1.TraceServiceSettings;
import com.google.cloud.trace.v1.consumer.TraceConsumer;
import com.google.cloud.trace.zipkin.autoconfigure.ZipkinStackdriverStorageProperties;
import com.google.common.collect.Lists;
import com.google.devtools.cloudtrace.v1.PatchTracesRequest;
import com.google.devtools.cloudtrace.v1.TraceServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.SocketUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import zipkin.Codec;
import zipkin.Span;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.cloud.trace.zipkin.StackdriverMockServer.CLIENT_SSL_CONTEXT;
import static com.google.cloud.trace.zipkin.StackdriverZipkinCollector.ZIPKIN_CONFIG_NAMES;
import static com.google.devtools.cloudtrace.v1.TraceServiceGrpc.TraceServiceBlockingStub;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static zipkin.TestObjects.LOTS_OF_SPANS;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {
    StackdriverZipkinCollector.class,
    ZipkinCollectorIntegrationTest.TestConfiguration.class
  },
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    ZIPKIN_CONFIG_NAMES,
    "zipkin.storage.stackdriver.project-id=test_project",
    "zipkin.storage.stackdriver.api-host=localhost:${mock.stackdriver.port}"
  }
)
@ContextConfiguration(initializers = ZipkinCollectorIntegrationTest.RandomPortInitializer.class)
public class ZipkinCollectorIntegrationTest {
  // LOTS_OF_SPANS is too much! I need at most 30!
  private static final List<Span> TEST_SPANS =
      unmodifiableList(asList(Arrays.copyOfRange(LOTS_OF_SPANS, 0, min(30, LOTS_OF_SPANS.length))));

  @Autowired private StackdriverMockServer mockServer;

  @Autowired private ZipkinStackdriverStorageProperties storageProperties;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private StackdriverStorageComponent storageComponent;

  @Autowired ConfigurableWebApplicationContext context;
  MockMvc mockMvc;

  @Before
  public void init() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @After
  @Before
  public void cleanup() {
    this.mockServer.reset();
    this.storageComponent.resetMetrics();
  }

  @Test
  public void openSSLAvailable() throws InterruptedException {
    assertThat(
        "OpenSsl unavailable:" + OpenSsl.unavailabilityCause(),
        OpenSsl.isAvailable(),
        equalTo(true));
    assertThat(
        "OpenSsl suppose to be default",
        SslContext.defaultServerProvider(),
        equalTo(SslProvider.OPENSSL));
    assertThat(
        "OpenSsl suppose to be default",
        SslContext.defaultClientProvider(),
        equalTo(SslProvider.OPENSSL));
  }

  @Test
  public void mockGrpcServerServesOverSSL() {
    final NettyChannelBuilder channelBuilder =
        NettyChannelBuilder.forTarget(storageProperties.getApiHost());

    final TraceServiceBlockingStub plainTraceService =
        TraceServiceGrpc.newBlockingStub(channelBuilder.build());
    final TraceServiceBlockingStub sslTraceService =
        TraceServiceGrpc.newBlockingStub(channelBuilder.sslContext(CLIENT_SSL_CONTEXT).build());

    try {
      plainTraceService.patchTraces(PatchTracesRequest.getDefaultInstance());
    } catch (StatusRuntimeException e) {
      assertThat(e.getMessage(), endsWith("Channel closed while performing protocol negotiation"));
    }

    sslTraceService.patchTraces(PatchTracesRequest.getDefaultInstance());
  }

  @Test
  public void traceConsumerGetsCalled() throws Exception {
    final Set<Long> spanIds = toIds(TEST_SPANS);
    assertThat("Expected to test at least one span", spanIds, hasSize(greaterThan(0)));
    assertThat("Unexpected traces in Stackdriver", mockServer.spanIds(), Matchers.<Long>empty());
    final CountDownLatch spanCountdown = new CountDownLatch(spanIds.size());
    mockServer.setSpanCountdown(spanCountdown);

    // set content-type as if unset, spring's rest template sets it to application/octet-stream
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    for (List<Span> partition : Lists.partition(TEST_SPANS, 3)) {
      HttpEntity<byte[]> entity = new HttpEntity<>(Codec.JSON.writeSpans(partition), headers);
      this.restTemplate.postForLocation("/api/v1/spans", entity);
    }
    spanCountdown.await(1, TimeUnit.SECONDS);

    assertThat(spanCountdown.getCount(), equalTo(0l));
    assertThat("Not all spans made it to Stackdriver", spanIds, equalTo(mockServer.spanIds()));

    assertMetrics(spanIds);
  }

  void assertMetrics(Set<Long> spanIds) throws Exception {
    mockMvc
        .perform(get("/metrics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.['counter.zipkin_collector.messages.http']").value(greaterThan(0)))
        .andExpect(jsonPath("$.['counter.zipkin_collector.bytes.http']").value(greaterThan(0)))
        .andExpect(
            jsonPath("$.['gauge.zipkin_collector.message_bytes.http']")
                .value(greaterThan(0d))) // most recent size
        .andExpect(jsonPath("$.['counter.zipkin_collector.spans.http']").value(greaterThan(0)))
        .andExpect(
            jsonPath("$.['gauge.zipkin_collector.message_spans.http']")
                .value(greaterThan(0d))) // most recent count
        .andExpect(jsonPath("$.['counter.zipkin_storage.stackdriver.sent']").value(spanIds.size()))
        .andExpect(
            jsonPath("$.['gauge.zipkin_storage.stackdriver.active_threads']")
                .value(greaterThanOrEqualTo(0)))
        .andExpect(
            jsonPath("$.['gauge.zipkin_storage.stackdriver.pool_size']")
                .value(greaterThanOrEqualTo(1)))
        .andExpect(
            jsonPath("$.['gauge.zipkin_storage.stackdriver.core_pool_size']")
                .value(greaterThanOrEqualTo(1)))
        .andExpect(
            jsonPath("$.['gauge.zipkin_storage.stackdriver.max_pool_size']")
                .value(both(greaterThan(1)).and(lessThan(20))))
        .andExpect(
            jsonPath("$.['gauge.zipkin_storage.stackdriver.queue_size']")
                .value(greaterThanOrEqualTo(0)));
  }

  private Set<Long> toIds(List<Span> spans) {
    final Set<Long> spanIds = new HashSet<>();
    for (Span span : spans) {
      spanIds.add(span.id);
    }
    return spanIds;
  }

  @Configuration
  public static class TestConfiguration {
    @Bean(name = "googleCredentials")
    @Primary
    public Credentials mockGoogleCredentials() throws IOException {
      return mock(GoogleCredentials.class);
    }

    @Bean(name = "traceConsumer")
    @Primary
    TraceConsumer traceConsumer(
        Credentials credentials, ZipkinStackdriverStorageProperties storageProperties)
        throws IOException {
      final ManagedChannel managedChannel =
          NettyChannelBuilder.forTarget(storageProperties.getApiHost())
              .sslContext(CLIENT_SSL_CONTEXT)
              .build();

      TransportChannelProvider channelProvider =
          FixedTransportChannelProvider.create(GrpcTransportChannel.create(managedChannel));

      //TODO(denyska): figure out how to request credentials in StackdriverMockServer
      //once done replace credentialsProvider below with FixedCredentialsProvider.create(credentials)
      CredentialsProvider credentialsProvider = NoCredentialsProvider.create();

      final TraceServiceSettings traceSettings = TraceServiceSettings.newBuilder().setTransportChannelProvider(channelProvider)
          .setCredentialsProvider(credentialsProvider)
          .build();

      final GrpcTraceConsumer traceConsumer = new GrpcTraceConsumer(TraceServiceClient.create(traceSettings));

      return traceConsumer;
    }

    @Bean
    public StackdriverMockServer stackdriverTraceServer(@Value("${mock.stackdriver.port}") int port)
        throws IOException {
      return new StackdriverMockServer(port);
    }
  }

  public static class RandomPortInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      int randomPort = SocketUtils.findAvailableTcpPort();
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
          applicationContext, "mock.stackdriver.port=" + randomPort);
    }
  }
}
