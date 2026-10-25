package io.kestra.plugin.garmin.activities;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.garmin.GarminClient;
import io.kestra.plugin.garmin.MockGarminServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class GetTest {
    private static final String BODY = """
        [{"summaryId":"a1","activityId":111,"summary":{"distanceInMeters":5000}},
         {"summaryId":"a2","activityId":222,"summary":{"distanceInMeters":42000}}]""";

    @Inject
    private RunContextFactory runContextFactory;

    private Get.GetBuilder<?, ?> task(MockGarminServer server, String activityId) {
        return Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(Property.ofValue(server.url()))
            .consumerKey(Property.ofValue("consumer-key"))
            .consumerSecret(Property.ofValue("consumer-secret"))
            .accessToken(Property.ofValue("token"))
            .accessTokenSecret(Property.ofValue("token-secret"))
            .activityId(Property.ofValue(activityId))
            .startTime(Property.ofValue(Instant.parse("2025-01-31T00:00:00Z")))
            .endTime(Property.ofValue(Instant.parse("2025-01-31T12:00:00Z")));
    }

    @Test
    void findsTheActivityBySummaryId() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activityDetails", 200, BODY)) {
            var output = task(server, "a2").build().run(runContextFactory.of(Map.of()));

            assertThat(output.getActivity().get("summaryId"), is("a2"));
        }
    }

    @Test
    void findsTheActivityByActivityId() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activityDetails", 200, BODY)) {
            var output = task(server, "111").build().run(runContextFactory.of(Map.of()));

            assertThat(output.getActivity().get("summaryId"), is("a1"));
        }
    }

    @Test
    void failsWhenTheActivityIsNotInTheRange() throws Exception {
        try (var server = new MockGarminServer().reply("GET", "/wellness-api/rest/activityDetails", 200, BODY)) {
            var exception = assertThrows(IllegalStateException.class, () -> task(server, "missing").build().run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("Widen"));
        }
    }

    @Test
    void rejectsABaseUrlWithoutSchemeOrHost() throws Exception {
        try (var server = new MockGarminServer()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> task(server, "a1").baseUrl(Property.ofValue("apis.garmin.com")).build().run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("baseUrl"));
        }
    }

    @Test
    void rejectsDotSegmentsInPaths() {
        assertThrows(IllegalArgumentException.class, () -> GarminClient.pathSegment(".."));
        assertThrows(IllegalArgumentException.class, () -> GarminClient.pathSegment("."));
        assertThat(GarminClient.pathSegment("a b"), is("a%20b"));
    }
}
