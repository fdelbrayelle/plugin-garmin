package io.kestra.plugin.garmin.activities;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.garmin.MockGarminServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ListTest {
    private static final String BODY = """
        [{"summaryId":"a1","activityType":"RUNNING","durationInSeconds":1800},
         {"summaryId":"a2","activityType":"CYCLING","durationInSeconds":3600}]""";

    @Inject
    private RunContextFactory runContextFactory;

    private List.ListBuilder<?, ?> task(MockGarminServer server) {
        return List.builder()
            .id(IdUtils.create())
            .type(List.class.getName())
            .baseUrl(Property.ofValue(server.url()))
            .consumerKey(Property.ofValue("consumer-key"))
            .consumerSecret(Property.ofValue("consumer-secret"))
            .accessToken(Property.ofValue("token"))
            .accessTokenSecret(Property.ofValue("token-secret"))
            .startTime(Property.ofValue(Instant.parse("2025-01-31T00:00:00Z")))
            .endTime(Property.ofValue(Instant.parse("2025-01-31T12:00:00Z")));
    }

    @Test
    void fetchesActivitiesAndSignsTheRequest() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activities", 200, BODY)) {
            var output = task(server).build().run(runContextFactory.of(Map.of()));

            assertThat(output.getSize(), is(2L));
            assertThat(output.getRows(), hasSize(2));
            assertThat(((Map<?, ?>) output.getRows().getFirst()).get("summaryId"), is("a1"));

            var request = server.lastRequest();
            assertThat(request.method(), is("GET"));
            assertThat(request.uri().getQuery(), is("uploadEndTimeInSeconds=1738324800&uploadStartTimeInSeconds=1738281600"));
            assertThat(request.headers().getFirst("Authorization"), startsWith("OAuth oauth_consumer_key=\"consumer-key\""));
            assertThat(request.headers().getFirst("Authorization"), containsString("oauth_signature="));
        }
    }

    @Test
    void fetchOneReturnsTheFirstRecord() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activities", 200, BODY)) {
            var output = task(server).fetchType(Property.ofValue(FetchType.FETCH_ONE)).build().run(runContextFactory.of(Map.of()));

            assertThat(output.getRow().get("summaryId"), is("a1"));
            assertThat(output.getRows(), is(nullValue()));
        }
    }

    @Test
    void storeWritesRecordsToInternalStorage() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activities", 200, BODY)) {
            var output = task(server).fetchType(Property.ofValue(FetchType.STORE)).build().run(runContextFactory.of(Map.of()));

            assertThat(output.getUri(), is(notNullValue()));
            assertThat(output.getSize(), is(2L));
            assertThat(output.getRows(), is(nullValue()));
        }
    }

    @Test
    void emptyBodyYieldsNoRecords() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activities", 200, "")) {
            var output = task(server).build().run(runContextFactory.of(Map.of()));

            assertThat(output.getSize(), is(0L));
            assertThat(output.getRows(), hasSize(0));
        }
    }

    @Test
    void unauthorizedExplainsWhatToCheck() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activities", 401, "{\"errorMessage\":\"bad signature\"}")) {
            var exception = assertThrows(Exception.class, () -> task(server).build().run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("HTTP 401"));
            assertThat(exception.getMessage(), containsString("consumer key/secret"));
        }
    }

    @Test
    void rejectsAnEndBeforeTheStart() throws Exception {
        try (var server = new MockGarminServer()) {
            var task = task(server).endTime(Property.ofValue(Instant.parse("2025-01-30T00:00:00Z"))).build();

            var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("must be after"));
        }
    }

    @Test
    void requiresCredentials() throws Exception {
        try (var server = new MockGarminServer()) {
            var task = task(server).consumerKey(Property.ofValue("")).build();

            var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("consumerKey"));
        }
    }
}
