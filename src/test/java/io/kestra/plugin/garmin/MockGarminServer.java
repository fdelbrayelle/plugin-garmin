package io.kestra.plugin.garmin;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockGarminServer implements AutoCloseable {
    public record Recorded(String method, URI uri, Headers headers, String body) {
    }

    public record Reply(int status, byte[] body, Map<String, String> headers) {
    }

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    public MockGarminServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI(), exchange.getRequestHeaders(), body));
            var reply = replies.getOrDefault(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath(), new Reply(404, "{\"error\":\"not mocked\"}".getBytes(StandardCharsets.UTF_8), Map.of()));
            reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            if (reply.body().length == 0) {
                exchange.sendResponseHeaders(reply.status(), -1);
            } else {
                exchange.sendResponseHeaders(reply.status(), reply.body().length);
                exchange.getResponseBody().write(reply.body());
            }
            exchange.close();
        });
        server.start();
    }

    public MockGarminServer reply(String method, String path, int status, String body) {
        return reply(method, path, status, body.getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/json"));
    }

    public MockGarminServer reply(String method, String path, int status, byte[] body, Map<String, String> headers) {
        replies.put(method + " " + path, new Reply(status, body, headers));
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Recorded> requests() {
        return requests;
    }

    public Recorded lastRequest() {
        return requests.getLast();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
