package io.kestra.plugin.garmin.activities;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.garmin.MockGarminServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DownloadTest {
    @Inject
    private RunContextFactory runContextFactory;

    private Download.DownloadBuilder<?, ?> task(MockGarminServer server, String activityId) {
        return Download.builder()
            .id(IdUtils.create())
            .type(Download.class.getName())
            .baseUrl(Property.ofValue(server.url()))
            .consumerKey(Property.ofValue("consumer-key"))
            .consumerSecret(Property.ofValue("consumer-secret"))
            .accessToken(Property.ofValue("token"))
            .accessTokenSecret(Property.ofValue("token-secret"))
            .activityId(Property.ofValue(activityId));
    }

    @Test
    void storesTheFitFile() throws Exception {
        var content = "FIT-BINARY-CONTENT".getBytes(StandardCharsets.UTF_8);
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activityFile", 200, content, Map.of("Content-Type", "application/octet-stream"))) {
            var runContext = runContextFactory.of(Map.of());

            var output = task(server, "a1").build().run(runContext);

            assertThat(output.getFormat(), is("FIT"));
            assertThat(output.getUri().getPath(), endsWith(".fit"));
            assertThat(runContext.storage().getFile(output.getUri()).readAllBytes(), is(content));
            assertThat(server.lastRequest().uri().getQuery(), is("id=a1"));
        }
    }

    @Test
    void detectsTheFormatFromTheContentDisposition() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activityFile", 200, "<gpx/>".getBytes(StandardCharsets.UTF_8), Map.of("Content-Disposition", "attachment; filename=\"activity.gpx\""))) {
            var output = task(server, "a1").build().run(runContextFactory.of(Map.of()));

            assertThat(output.getFormat(), is("GPX"));
            assertThat(output.getUri().getPath(), endsWith(".gpx"));
        }
    }

    @Test
    void reportsHttpErrors() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activityFile", 404, "{}")) {
            var exception = assertThrows(Exception.class, () -> task(server, "a1").build().run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("HTTP 404"));
        }
    }
}
