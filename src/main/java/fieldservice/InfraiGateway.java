package fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small REST boundary shared by storage and release controls. */
public final class InfraiGateway {
  private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
  private static final Pattern URL = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
  private final HttpClient client;
  private final String baseUrl;
  private final String apiKey;

  public InfraiGateway(RolloutSettings settings) {
    this(HttpClient.newHttpClient(), settings.baseUrl(), settings.apiKey());
  }

  InfraiGateway(HttpClient client, String baseUrl, String apiKey) {
    this.client = client;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  public void createPhotoBucket(String bucket) throws IOException, InterruptedException {
    call("POST", "/v1/storage/bucket/create", "{\"name\":\"" + json(bucket) + "\"}");
  }

  public String presignPhotoPut(String bucket, String key, String idempotencyKey)
      throws IOException, InterruptedException {
    String body = "{\"op\":\"put\",\"expires_seconds\":900,\"content_type\":\"image/jpeg\","
        + "\"max_bytes\":8388608,\"idempotency_key\":\"" + json(idempotencyKey) + "\"}";
    String envelope = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + segment(key), body);
    Matcher url = URL.matcher(envelope);
    if (!url.find()) throw new IOException("The presign response did not contain a URL");
    return url.group(1);
  }

  public void togglePhotoSchema(String flagKey) throws IOException, InterruptedException {
    String body = "{\"key\":\"" + json(flagKey) + "\",\"enabled\":true,\"version\":1}";
    call("POST", "/v1/flags/toggle/" + segment(flagKey), body);
  }

  private String call(String method, String path, String body) throws IOException, InterruptedException {
    for (int attempt = 0; attempt < 3; attempt++) {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
          .timeout(Duration.ofSeconds(20))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json");
      if (body.isEmpty()) request.method(method, HttpRequest.BodyPublishers.noBody());
      else request.method(method, HttpRequest.BodyPublishers.ofString(body));
      HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      String envelope = response.body();
      Matcher ok = OK.matcher(envelope);
      if (ok.find() && "true".equals(ok.group(1))) return envelope;
      if (response.statusCode() == 429 && attempt < 2) {
        Thread.sleep(retryDelay(response, attempt));
        continue;
      }
      if (ok.find(0)) throw new InfraiException(envelope, response.statusCode());
      if (response.statusCode() >= 400) throw new IOException("HTTP " + response.statusCode());
      throw new IOException("Invalid response envelope");
    }
    throw new IOException("Request attempts exhausted");
  }

  private long retryDelay(HttpResponse<String> response, int attempt) {
    Optional<String> retryAfter = response.headers().firstValue("Retry-After");
    if (retryAfter.isPresent()) {
      try { return Long.parseLong(retryAfter.get()) * 1000L; }
      catch (NumberFormatException ignored) { }
    }
    return 250L * (1L << attempt);
  }

  private static String segment(String value) {
    return value.replace("/", "%2F").replace(" ", "%20");
  }

  private static String json(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public static final class InfraiException extends IOException {
    InfraiException(String envelope, int status) { super("Infrai request rejected (HTTP " + status + "): " + envelope); }
  }
}
