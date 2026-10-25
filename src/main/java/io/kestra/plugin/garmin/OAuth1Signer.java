package io.kestra.plugin.garmin;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal OAuth 1.0a (RFC 5849) request signer using HMAC-SHA1. JSON request bodies are not part of the signature base string.
 */
public final class OAuth1Signer {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String consumerKey;
    private final String consumerSecret;
    private final String token;
    private final String tokenSecret;

    public OAuth1Signer(String consumerKey, String consumerSecret, String token, String tokenSecret) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.token = token;
        this.tokenSecret = tokenSecret;
    }

    public String authorizationHeader(String method, URI uri) {
        var nonce = HexFormat.of().formatHex(randomBytes());
        return authorizationHeader(method, uri, nonce, Instant.now().getEpochSecond());
    }

    String authorizationHeader(String method, URI uri, String nonce, long timestampSeconds) {
        var oauthParams = Map.of(
            "oauth_consumer_key", consumerKey,
            "oauth_nonce", nonce,
            "oauth_signature_method", "HMAC-SHA1",
            "oauth_timestamp", String.valueOf(timestampSeconds),
            "oauth_token", token,
            "oauth_version", "1.0"
        );
        var signature = sign(method, uri, oauthParams);

        var header = new ArrayList<String>();
        oauthParams.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> header.add(e.getKey() + "=\"" + encode(e.getValue()) + "\""));
        header.add("oauth_signature=\"" + encode(signature) + "\"");
        return "OAuth " + String.join(", ", header);
    }

    String sign(String method, URI uri, Map<String, String> oauthParams) {
        var params = new ArrayList<String[]>();
        oauthParams.forEach((k, v) -> params.add(new String[]{encode(k), encode(v)}));
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            for (var pair : uri.getRawQuery().split("&")) {
                var idx = pair.indexOf('=');
                var name = idx < 0 ? pair : pair.substring(0, idx);
                var value = idx < 0 ? "" : pair.substring(idx + 1);
                params.add(new String[]{encode(decode(name)), encode(decode(value))});
            }
        }
        var normalized = params.stream()
            .sorted(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]))
            .map(p -> p[0] + "=" + p[1])
            .collect(Collectors.joining("&"));

        var baseString = method.toUpperCase() + "&" + encode(baseUri(uri)) + "&" + encode(normalized);
        var signingKey = encode(consumerSecret) + "&" + encode(tokenSecret);
        return hmacSha1(signingKey, baseString);
    }

    private static String baseUri(URI uri) {
        var scheme = uri.getScheme().toLowerCase();
        var port = uri.getPort();
        var defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        var authority = uri.getHost().toLowerCase() + (port < 0 || defaultPort ? "" : ":" + port);
        var path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return scheme + "://" + authority + path;
    }

    private static String hmacSha1(String key, String data) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to compute the OAuth 1.0a HMAC-SHA1 signature", e);
        }
    }

    private static byte[] randomBytes() {
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~");
    }
}
