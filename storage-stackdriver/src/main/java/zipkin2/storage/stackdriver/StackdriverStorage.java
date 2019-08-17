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
package zipkin2.storage.stackdriver;

import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientOptions;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.HttpClientBuilder;
import com.linecorp.armeria.client.SimpleDecoratingClient;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import zipkin2.CheckResult;
import zipkin2.storage.AutocompleteTags;
import zipkin2.storage.ServiceAndSpanNames;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.SpanStore;
import zipkin2.storage.StorageComponent;

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
    return new Builder(url);
  }

  public static final class Builder extends StorageComponent.Builder {
    final String url;
    String projectId;
    ClientFactory clientFactory = ClientFactory.DEFAULT;
    ClientOptions clientOptions = ClientOptions.DEFAULT;

    public Builder(String url) {
      if (url == null) throw new NullPointerException("url == null");
      this.url = url;
    }

    /** {@inheritDoc} */
    @Override
    public final Builder strictTraceId(boolean strictTraceId) {
      if (!strictTraceId) {
        throw new UnsupportedOperationException("strictTraceId cannot be disabled");
      }
      return this;
    }

    /** {@inheritDoc} */
    @Override
    public final Builder searchEnabled(boolean searchEnabled) {
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

    @Override
    public StackdriverStorage build() {
      if (projectId == null) throw new NullPointerException("projectId == null");

      // Massage URL into one that armeria supports, taking into account upstream gRPC
      // defaults.
      String url = this.url;
      if (!url.startsWith("https://") && !url.startsWith("http://")) {
        // Default scheme to https for backwards compatibility with upstream gRPC.
        url = "https://" + url;
      }

      if (!url.endsWith("/")) {
        url = url + "/";
      }

      HttpClient httpClient = new HttpClientBuilder(url)
          .decorator(SetGrpcContentType::new)
          .factory(clientFactory)
          .options(clientOptions)
          .build();

      return new StackdriverStorage(this, new UnaryGrpcClient(httpClient));
    }
  }

  final UnaryGrpcClient grpcClient;
  final String projectId;

  StackdriverStorage(Builder builder, UnaryGrpcClient grpcClient) {
    this.grpcClient = grpcClient;
    projectId = builder.projectId;
  }

  @Override
  public SpanStore spanStore() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override
  public AutocompleteTags autocompleteTags() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override
  public ServiceAndSpanNames serviceAndSpanNames() {
    throw new UnsupportedOperationException("Read operations are not supported");
  }

  @Override
  public SpanConsumer spanConsumer() {
    return new StackdriverSpanConsumer(grpcClient, projectId);
  }

  @Override
  public CheckResult check() {
    return CheckResult.OK;
  }

  @Override
  public final String toString() {
    return "StackdriverSender{" + projectId + "}";
  }

  // Many Google services do not support the standard application/grpc+proto header.
  static final class SetGrpcContentType extends SimpleDecoratingClient<HttpRequest, HttpResponse> {
    SetGrpcContentType(Client<HttpRequest, HttpResponse> client) {
      super(client);
    }

    @Override
    public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
      req = HttpRequest.of(req, req.headers().toBuilder()
          .set(HttpHeaderNames.CONTENT_TYPE, "application/grpc")
          .build());
      return delegate().execute(ctx, req);
    }
  }
}
