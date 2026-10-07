package fieldservice;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;

/** Request-contract regression test for the release flag boundary. */
public final class InfraiGatewayTest {
  public static void main(String[] args) throws Exception {
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      path.set(exchange.getRequestURI().getRawPath());
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
    try {
      InfraiGateway gateway = new InfraiGateway(HttpClient.newHttpClient(),
          "http://127.0.0.1:" + server.getAddress().getPort(), "test-key");
      gateway.togglePhotoSchema("work-order-photo-schema");
      if (!"/v1/flags/toggle/work-order-photo-schema".equals(path.get())) {
        throw new AssertionError("Unexpected toggle path: " + path.get());
      }
      String expected = "{\"key\":\"work-order-photo-schema\",\"enabled\":true,\"version\":1}";
      if (!expected.equals(body.get())) throw new AssertionError("Unexpected toggle body: " + body.get());
      System.out.println("PASS: release toggle sends every required request field.");
    } finally {
      server.stop(0);
    }
  }
}
