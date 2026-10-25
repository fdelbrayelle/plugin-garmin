package io.kestra.plugin.garmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Short-lived Garmin Connect API client: built inside a task run, signs each request with OAuth 1.0a and is closed with it.
 */
public final class GarminClient implements Closeable {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ERROR_BODY_LENGTH = 500;
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final String baseUrl;
    private final OAuth1Signer signer;

    private GarminClient(HttpClient httpClient, String baseUrl, OAuth1Signer signer) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.signer = signer;
    }

    public static GarminClient create(RunContext runContext, GarminConnection connection) throws IllegalVariableEvaluationException {
        var rBaseUrl = render(runContext, connection.getBaseUrl(), GarminConnection.DEFAULT_BASE_URL);
        var rTimeout = runContext.render(connection.getTimeout()).as(Duration.class).orElse(DEFAULT_TIMEOUT);
        var signer = new OAuth1Signer(
            required(runContext, connection.getConsumerKey(), "consumerKey"),
            required(runContext, connection.getConsumerSecret(), "consumerSecret"),
            required(runContext, connection.getAccessToken(), "accessToken"),
            required(runContext, connection.getAccessTokenSecret(), "accessTokenSecret")
        );

        var configuration = HttpConfiguration.builder()
            .timeout(TimeoutConfiguration.builder()
                .connectTimeout(Property.ofValue(rTimeout))
                .readIdleTimeout(Property.ofValue(rTimeout))
                .build())
            .build();
        var httpClient = HttpClient.builder().runContext(runContext).configuration(configuration).build();
        return new GarminClient(httpClient, rBaseUrl.replaceAll("/+$", ""), signer);
    }

    public static String pathSegment(String value) {
        return OAuth1Signer.encode(value);
    }

    public List<Map<String, Object>> getList(String path, Map<String, String> query) throws IOException {
        var body = exchange("GET", path, query, null);
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            return JacksonMapper.ofJson().readValue(body, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unexpected response from Garmin: expected a JSON array for '" + path + "'. Check that the base URL points to the Garmin Connect API.", e);
        }
    }

    public Map<String, Object> send(String method, String path, String jsonBody) throws IOException {
        var body = exchange(method, path, Map.of(), jsonBody);
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return JacksonMapper.ofJson().readValue(body, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unexpected response from Garmin: expected a JSON object for '" + path + "'.", e);
        }
    }

    public void download(String path, Map<String, String> query, Consumer<HttpResponse<InputStream>> consumer) throws IOException {
        var request = request("GET", path, query, null);
        try {
            httpClient.request(request, consumer);
        } catch (HttpClientException | IllegalVariableEvaluationException e) {
            throw translate(e, path);
        }
    }

    private String exchange(String method, String path, Map<String, String> query, String jsonBody) throws IOException {
        var request = request(method, path, query, jsonBody);
        try {
            HttpResponse<String> response = httpClient.request(request, String.class);
            return response.getBody();
        } catch (HttpClientException | IllegalVariableEvaluationException e) {
            throw translate(e, path);
        }
    }

    private HttpRequest request(String method, String path, Map<String, String> query, String jsonBody) {
        var queryString = query.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> OAuth1Signer.encode(e.getKey()) + "=" + OAuth1Signer.encode(e.getValue()))
            .collect(Collectors.joining("&"));
        var uri = URI.create(baseUrl + path + (queryString.isEmpty() ? "" : "?" + queryString));

        var builder = HttpRequest.builder()
            .uri(uri)
            .method(method)
            .addHeader("Authorization", signer.authorizationHeader(method, uri))
            .addHeader("Accept", "application/json");
        if (jsonBody != null) {
            builder.addHeader("Content-Type", "application/json")
                .body(HttpRequest.StringRequestBody.builder().content(jsonBody).contentType("application/json").build());
        }
        return builder.build();
    }

    private static IOException translate(Exception e, String path) {
        if (e instanceof HttpClientResponseException responseException) {
            var status = responseException.getResponse().getStatus().getCode();
            var body = String.valueOf(responseException.getResponse().getBody());
            var detail = body.length() > MAX_ERROR_BODY_LENGTH ? body.substring(0, MAX_ERROR_BODY_LENGTH) + "..." : body;
            var hint = switch (status) {
                case 400 -> "check the request parameters (time ranges are limited to 24 hours for pull requests)";
                case 401 -> "check the consumer key/secret and the user access token/secret";
                case 403 -> "the Garmin developer app is not enabled for this API or the user did not grant the permission";
                case 404 -> "the resource does not exist or the base URL is wrong";
                case 409 -> "the data was already requested or already exists";
                case 429 -> "Garmin rate limit reached, retry later";
                default -> status >= 500 ? "Garmin service error, retry later" : "check the request";
            };
            return new IOException("Garmin API returned HTTP " + status + " for '" + path + "': " + hint + ". Response: " + detail, e);
        }
        return new IOException("Unable to call the Garmin API '" + path + "': " + e.getMessage(), e);
    }

    private static String render(RunContext runContext, Property<String> property, String defaultValue) throws IllegalVariableEvaluationException {
        return runContext.render(property).as(String.class).orElse(defaultValue);
    }

    private static String required(RunContext runContext, Property<String> property, String name) throws IllegalVariableEvaluationException {
        return runContext.render(property).as(String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("'" + name + "' is required. Provide the credential of your Garmin Connect developer app, preferably with a secret"));
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
