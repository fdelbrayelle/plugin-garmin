package io.kestra.plugin.garmin;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

class OAuth1SignerTest {
    private static final OAuth1Signer SIGNER = new OAuth1Signer(
        "xvz1evFS4wEEPTGEFPHBog",
        "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw",
        "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb",
        "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE"
    );

    @Test
    void signsLikeTheReferenceVector() {
        // Reference request from the Twitter OAuth 1.0a documentation, with the form body moved to the query string.
        var uri = URI.create("https://api.twitter.com/1/statuses/update.json?include_entities=true&status=Hello%20Ladies%20%2B%20Gentlemen%2C%20a%20signed%20OAuth%20request%21");
        var params = Map.of(
            "oauth_consumer_key", "xvz1evFS4wEEPTGEFPHBog",
            "oauth_nonce", "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg",
            "oauth_signature_method", "HMAC-SHA1",
            "oauth_timestamp", "1318622958",
            "oauth_token", "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb",
            "oauth_version", "1.0"
        );

        assertThat(SIGNER.sign("POST", uri, params), is("tnnArxj06cWHq44gCs1OSKk/jLY="));
    }

    @Test
    void buildsTheAuthorizationHeader() {
        var header = SIGNER.authorizationHeader("GET", URI.create("https://apis.garmin.com/wellness-api/rest/dailies?uploadStartTimeInSeconds=1&uploadEndTimeInSeconds=2"), "abc", 1700000000L);

        assertThat(header, startsWith("OAuth oauth_consumer_key=\"xvz1evFS4wEEPTGEFPHBog\""));
        assertThat(header, containsString("oauth_nonce=\"abc\""));
        assertThat(header, containsString("oauth_signature_method=\"HMAC-SHA1\""));
        assertThat(header, containsString("oauth_timestamp=\"1700000000\""));
        assertThat(header, containsString("oauth_signature=\""));
    }

    @Test
    void queryOrderDoesNotChangeTheSignature() {
        var params = Map.of("oauth_consumer_key", "k", "oauth_nonce", "n", "oauth_signature_method", "HMAC-SHA1", "oauth_timestamp", "1", "oauth_token", "t", "oauth_version", "1.0");

        var first = SIGNER.sign("GET", URI.create("https://h.example/p?a=1&b=2"), params);
        var second = SIGNER.sign("GET", URI.create("https://h.example/p?b=2&a=1"), params);

        assertThat(first, is(second));
    }
}
