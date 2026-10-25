package io.kestra.plugin.garmin.health;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.garmin.AbstractListTask;
import io.kestra.plugin.garmin.MockGarminServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest
class HealthTasksTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void dailies() throws Exception {
        assertListsFrom("/wellness-api/rest/dailies", () -> Dailies.builder());
    }

    @Test
    void sleeps() throws Exception {
        assertListsFrom("/wellness-api/rest/sleeps", () -> Sleeps.builder());
    }

    @Test
    void stress() throws Exception {
        assertListsFrom("/wellness-api/rest/stressDetails", () -> Stress.builder());
    }

    @Test
    void bodyCompositions() throws Exception {
        assertListsFrom("/wellness-api/rest/bodyComps", () -> BodyCompositions.builder());
    }

    private void assertListsFrom(String path, Supplier<AbstractListTask.AbstractListTaskBuilder<?, ?>> builder) throws Exception {
        try (var server = new MockGarminServer().reply("GET", path, 200, "[{\"summaryId\":\"s1\"},{\"summaryId\":\"s2\"}]")) {
            var task = builder.get()
                .id("health")
                .type("io.kestra.plugin.garmin.health.Test")
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("consumer-key"))
                .consumerSecret(Property.ofValue("consumer-secret"))
                .accessToken(Property.ofValue("token"))
                .accessTokenSecret(Property.ofValue("token-secret"))
                .startTime(Property.ofValue(Instant.parse("2025-01-31T00:00:00Z")))
                .build();

            var output = task.run(runContextFactory.of(Map.of()));

            assertThat(output.getSize(), is(2L));
            assertThat(output.getRows(), hasSize(2));
            assertThat(server.lastRequest().uri().getPath(), is(path));
            assertThat(server.lastRequest().uri().getQuery().contains("uploadStartTimeInSeconds=1738281600"), is(true));
        }
    }
}
