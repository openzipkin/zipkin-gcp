package zipkin.autoconfigure.storage.stackdriver;

import com.google.auth.Credentials;
import com.google.auth.RequestMetadataCallback;
import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.SimpleDecoratingClient;
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

class CredentialsDecoratingClient extends SimpleDecoratingClient<HttpRequest, HttpResponse>
    implements AutoCloseable {

  static Function<Client<HttpRequest, HttpResponse>, Client<HttpRequest, HttpResponse>>
  newDecorator(Credentials credentials) {
    return new Function<Client<HttpRequest, HttpResponse>, Client<HttpRequest, HttpResponse>>() {
      @Override public CredentialsDecoratingClient apply(Client<HttpRequest, HttpResponse> client) {
        return new CredentialsDecoratingClient(client, credentials);
      }
    };
  }

  final Credentials credentials;
  final ExecutorService executor;

  private CredentialsDecoratingClient(
      Client<HttpRequest, HttpResponse> delegate, Credentials credentials) {
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
        if (map != null) {
          for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            req.headers().add(HttpHeaderNames.of(entry.getKey()), entry.getValue());
          }
        }
        try {
          responseFuture.complete(delegate().execute(ctx, req));
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
