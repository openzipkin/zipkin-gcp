/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.module.storage.stackdriver;

import com.google.auth.Credentials;
import com.google.auth.RequestMetadataCallback;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

class CredentialsDecoratingClient extends SimpleDecoratingHttpClient implements AutoCloseable {

  static Function<HttpClient, HttpClient> newDecorator(Credentials credentials) {
    return client -> new CredentialsDecoratingClient(client, credentials);
  }

  final Credentials credentials;
  final ExecutorService executor;

  private CredentialsDecoratingClient(HttpClient delegate, Credentials credentials) {
    super(delegate);
    this.credentials = credentials;
    executor = Executors.newSingleThreadExecutor();
  }

  @Override public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) {
    final URI uri;
    try {
      uri = new URI("https", req.authority(), req.path(), null, null);
    } catch (URISyntaxException e) {
      return HttpResponse.ofFailure(e);
    }

    CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

    credentials.getRequestMetadata(uri, executor, new RequestMetadataCallback() {
      @Override public void onSuccess(Map<String, List<String>> map) {
        HttpRequest newReq = req;
        if (map != null) {
          newReq = req.withHeaders(req.headers().withMutations(headers -> {
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
              headers.add(HttpHeaderNames.of(entry.getKey()), entry.getValue());
            }
          }));
          ctx.updateRequest(newReq);
        }
        try {
          responseFuture.complete(unwrap().execute(ctx, newReq));
        } catch (Exception e) {
          responseFuture.completeExceptionally(e);
        }
      }

      @Override public void onFailure(Throwable throwable) {
        responseFuture.completeExceptionally(throwable);
      }
    });

    return HttpResponse.from(responseFuture);
  }

  @Override public void close() {
    executor.shutdownNow();
  }
}
