/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.stackdriver;

import com.google.devtools.cloudtrace.v2.BatchWriteSpansRequest;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientOptions;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.grpc.protocol.UnaryGrpcClient;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.grpc.protocol.ArmeriaStatusException;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.Traces;
import zipkin2.storage.stackdriver.StackdriverSpanConsumer.BatchWriteSpansCall;

/**
 * StackdriverStorage is a StorageComponent that consumes spans using the Stackdriver
 * TraceSpanConsumer.
 *
 * <p>No SpanStore methods are implemented because read operations are not yet supported.
 */
public final class StackdriverStorage extends StorageComponent {
  public static Builder newBuilder() {
    return new Builder("https://cloudtrace.googleapis.com/");
  }

  public static Builder newBuilder(String url) {  // visible for testing
    // Massage URL into one that Armeria supports, taking into account upstream gRPC
    // defaults.
    if (!url.startsWith("https://") && !url.startsWith("http://")) {
      // Default scheme to https for backwards compatibility with upstream gRPC.
      url = "https://" + url;
    }

    if (!url.endsWith("/")) url = url + "/";
    return new Builder(url);
  }

  public static final class Builder extends StorageComponent.Builder {
    final String url;
    String projectId;
    ClientFactory clientFactory = ClientFactory.ofDefault();
    ClientOptions clientOptions = ClientOptions.of();

    public Builder(String url) {
      if (url == null) throw new NullPointerException("url == null");
      this.url = url;
    }

    /** {@inheritDoc} */
    @Override public final Builder strictTraceId(boolean strictTraceId) {
      if (!strictTraceId) {
        throw new UnsupportedOperationException("strictTraceId cannot be disabled");
      }
      return this;
    }

    /** {@inheritDoc} */
    @Override public final Builder searchEnabled(boolean searchEnabled) {
      if (!searchEnabled) {
        throw new UnsupportedOperationException("searchEnabled cannot be disabled");
      }
      return this;
    }

    public Builder projectId(String projectId) {
      if (projectId == null) throw new NullPointerException("projectId == null");
      this.projectId = projectId;
      return this;
    }

    public Builder clientFactory(ClientFactory clientFactory) {
      if (clientFactory == null) throw new NullPointerException("clientFactory == null");
      this.clientFactory = clientFactory;
      return this;
    }

    public Builder clientOptions(ClientOptions clientOptions) {
      if (clientOptions == null) throw new NullPointerException("clientOptions == null");
      this.clientOptions = clientOptions;
      return this;
    }

    @Override public StackdriverStorage build() {
      if (projectId == null) throw new NullPointerException("projectId == null");
      return new StackdriverStorage(this);
    }
  }

  final ClientFactory clientFactory;
  final UnaryGrpcClient grpcClient;
  final String projectId;
  final BatchWriteSpansCall healthcheckCall;

  StackdriverStorage(Builder builder) {
    this.clientFactory = builder.clientFactory;
    this.grpcClient = new UnaryGrpcClient(WebClient.builder(builder.url)
        .decorator(SetGrpcContentType::new)
        .factory(builder.clientFactory)
        .options(builder.clientOptions)
        .build());
    projectId = builder.projectId;
    BatchWriteSpansRequest healthcheckRequest = BatchWriteSpansRequest.newBuilder()
        .setName("projects/" + builder.projectId)
        .build();
    healthcheckCall = new BatchWriteSpansCall(grpcClient, healthcheckRequest);
  }

  @Override public SpanStore spanStore() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override public Traces traces() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override public AutocompleteTags autocompleteTags() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override public ServiceAndSpanNames serviceAndSpanNames() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override public SpanConsumer spanConsumer() {
    return new StackdriverSpanConsumer(grpcClient, projectId);
  }

  /**
   * Sends a malformed call to Stackdriver Trace to validate service health.
   *
   * @return successful status if Stackdriver Trace API responds with expected validation error (or
   * happens to respond as success -- unexpected but okay); otherwise returns error status wrapping
   * the underlying exception.
   */
  // Same code as zipkin2.reporter.stackdriver.StackDriverSender.check() ported to armeria
  @Override public CheckResult check() {
    try {
      healthcheckCall.clone().execute();
    } catch (ArmeriaStatusException ase) {
      if (ase.getCode() == 3 /* INVALID_ARGUMENT */) {
        return CheckResult.OK;
      }
      return CheckResult.failed(ase);
    } catch (Throwable t) {
      Call.propagateIfFatal(t);
      return CheckResult.failed(t);
    }

    // Currently the rpc throws a validation exception on malformed input, which we handle above.
    // If we get here despite the known malformed input, the implementation changed and we need to
    // update this check. It's unlikely enough that we can wait and see.
    return CheckResult.OK;
  }

  @Override public void close() {
    clientFactory.close();
  }

  @Override public boolean isOverCapacity(Throwable e) {
    return super.isOverCapacity(e); // TODO
  }

  @Override public final String toString() {
    return "StackdriverStorage{" + projectId + "}";
  }

  // Many Google services do not support the standard application/grpc+proto header.
  static final class SetGrpcContentType extends SimpleDecoratingHttpClient {
    SetGrpcContentType(HttpClient client) {
      super(client);
    }

    @Override
    public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
      ctx.addAdditionalRequestHeader(HttpHeaderNames.CONTENT_TYPE, "application/grpc");
      return unwrap().execute(ctx, req);
    }
  }
}
