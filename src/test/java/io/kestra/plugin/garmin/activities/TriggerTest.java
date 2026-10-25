package io.kestra.plugin.garmin.activities;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.garmin.MockGarminServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@KestraTest
class TriggerTest {
    private static final String PATH = "/wellness-api/rest/activities";

    @Inject
    private RunContextFactory runContextFactory;

    private Trigger trigger(MockGarminServer server) {
        return Trigger.builder()
            .id("trigger-" + UUID.randomUUID())
            .type(Trigger.class.getName())
            .baseUrl(Property.ofValue(server.url()))
            .consumerKey(Property.ofValue("consumer-key"))
            .consumerSecret(Property.ofValue("consumer-secret"))
            .accessToken(Property.ofValue("token"))
            .accessTokenSecret(Property.ofValue("token-secret"))
            .initialLookback(Property.ofValue(Duration.ofHours(2)))
            .build();
    }

    @Test
    void firesOnlyOnceForEachActivity() throws Exception {
        try (var server = new MockGarminServer().reply("GET", PATH, 200, "[{\"summaryId\":\"a1\"}]")) {
            var trigger = trigger(server);
            var context = TestsUtils.mockTrigger(runContextFactory, trigger);

            var first = trigger.evaluate(context.getKey(), context.getValue());
            assertThat(first.isPresent(), is(true));
            assertThat(first.get().getTrigger().getVariables().get("size"), is(1));

            var second = trigger.evaluate(context.getKey(), context.getValue());
            assertThat(second.isPresent(), is(false));

            server.reply("GET", PATH, 200, "[{\"summaryId\":\"a1\"},{\"summaryId\":\"a2\"}]");
            var third = trigger.evaluate(context.getKey(), context.getValue());
            assertThat(third.isPresent(), is(true));
            assertThat(third.get().getTrigger().getVariables().get("size"), is(1));
            assertThat(third.get().getTrigger().getVariables().get("activities").toString(), containsString("a2"));
        }
    }

    @Test
    void doesNotFireWithoutActivities() throws Exception {
        try (var server = new MockGarminServer().reply("GET", PATH, 200, "[]")) {
            var trigger = trigger(server);
            var context = TestsUtils.mockTrigger(runContextFactory, trigger);

            assertThat(trigger.evaluate(context.getKey(), context.getValue()).isPresent(), is(false));
            assertThat(server.requests().size(), is(1));
        }
    }

    @Test
    void firstPollUsesTheInitialLookback() throws Exception {
        try (var server = new MockGarminServer().reply("GET", PATH, 200, "[]")) {
            var trigger = trigger(server);
            var context = TestsUtils.mockTrigger(runContextFactory, trigger);

            trigger.evaluate(context.getKey(), context.getValue());

            var query = server.lastRequest().uri().getQuery();
            var params = java.util.Arrays.stream(query.split("&")).map(p -> p.split("=")).collect(java.util.stream.Collectors.toMap(p -> p[0], p -> Long.parseLong(p[1])));
            assertThat(params.get("uploadEndTimeInSeconds") - params.get("uploadStartTimeInSeconds"), is(7200L));
        }
    }
}
