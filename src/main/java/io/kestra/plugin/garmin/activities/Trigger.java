package io.kestra.plugin.garmin.activities;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.garmin.AbstractTimeRangeTask;
import io.kestra.plugin.garmin.GarminClient;
import io.kestra.plugin.garmin.GarminConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow on new Garmin activities",
    description = """
        Polls the Garmin Activity API and starts an execution when activities were uploaded since the last poll.
        Polling is used instead of the Garmin Ping/Push notifications because those require a publicly reachable
        endpoint registered in the Garmin developer portal.

        The last polled time and the activities already delivered are stored in the namespace KV Store, so an
        activity is never delivered twice. Garmin limits a pull request to 24 hours: after a longer pause, only the
        last 24 hours are fetched. On the first poll, activities uploaded during `initialLookback` are fetched."""
)
@Plugin(
    examples = {
        @Example(
            title = "Run a flow for each batch of new activities",
            full = true,
            code = """
                id: garmin_new_activities
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ trigger.size }} new activity(ies), first: {{ trigger.activities[0].activityType }}"

                triggers:
                  - id: new_activities
                    type: io.kestra.plugin.garmin.activities.Trigger
                    interval: PT15M
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, GarminConnection, TriggerOutput<Trigger.Output> {
    private static final String PATH = "/wellness-api/rest/activities";
    private static final Duration MAX_RANGE = Duration.ofHours(24);

    @Schema(
        title = "Polling interval",
        description = "Time between two polls of the Garmin API. Defaults to 15 minutes."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private final Duration interval = Duration.ofMinutes(15);

    @Schema(
        title = "Initial lookback",
        description = "On the first poll, fetch the activities uploaded during this duration. Capped at 24 hours, defaults to 1 hour."
    )
    @Builder.Default
    @PluginProperty(group = "execution")
    private Property<Duration> initialLookback = Property.ofValue(Duration.ofHours(1));

    @Schema(
        title = "Garmin API base URL",
        description = """
            Base URL of the Garmin Connect Developer Program APIs. Override it only to target another environment,
            for example a proxy or a mock server."""
    )
    @Builder.Default
    @PluginProperty(group = "connection")
    private Property<String> baseUrl = Property.ofValue(DEFAULT_BASE_URL);

    @Schema(
        title = "Consumer key",
        description = "OAuth 1.0a consumer key of your Garmin Connect developer app."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> consumerKey;

    @Schema(
        title = "Consumer secret",
        description = "OAuth 1.0a consumer secret of your Garmin Connect developer app."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> consumerSecret;

    @Schema(
        title = "User access token",
        description = "OAuth 1.0a access token obtained when the Garmin user granted your app access to their data."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> accessToken;

    @Schema(
        title = "User access token secret",
        description = "OAuth 1.0a access token secret paired with the user access token."
    )
    @NotNull
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    private Property<String> accessTokenSecret;

    @Schema(
        title = "Request timeout",
        description = "Maximum time to wait when connecting to and reading from the Garmin API. Defaults to 30 seconds."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Duration> timeout = Property.ofValue(Duration.ofSeconds(30));

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();
        var kv = runContext.namespaceKv(context.getNamespace());
        var key = watermarkKey(context);

        var now = Instant.now();
        var state = readState(kv.getValue(key).map(v -> v.value()).orElse(null));
        var floor = now.minus(MAX_RANGE);
        var start = state.watermark() != null
            ? state.watermark()
            : now.minus(runContext.render(initialLookback).as(Duration.class).orElse(Duration.ofHours(1)));
        if (start.isBefore(floor)) {
            logger.warn("Last Garmin poll is older than 24 hours, only the activities of the last 24 hours are fetched");
            start = floor;
        }

        List<Map<String, Object>> fetched;
        try (var client = GarminClient.create(runContext, this)) {
            fetched = client.getList(PATH, AbstractTimeRangeTask.timeRangeQuery(start, now));
        }

        var activities = fetched.stream()
            .filter(a -> !state.deliveredIds().contains(activityKey(a)))
            .toList();

        var ids = new HashSet<String>();
        fetched.forEach(a -> ids.add(activityKey(a)));
        saveState(runContext, context, key, now, ids);

        if (activities.isEmpty()) {
            logger.debug("No new Garmin activity between {} and {}", start, now);
            return Optional.empty();
        }

        logger.info("Found {} new Garmin activity(ies)", activities.size());
        var output = Output.builder().size(activities.size()).activities(activities).build();
        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    private void saveState(RunContext runContext, TriggerContext context, String key, Instant watermark, Set<String> ids) throws IOException {
        var value = new HashMap<String, Object>();
        value.put("watermark", watermark.toString());
        value.put("ids", List.copyOf(ids));
        runContext.namespaceKv(context.getNamespace()).put(key, new KVValueAndMetadata(null, value));
    }

    @SuppressWarnings("unchecked")
    private static State readState(Object stored) {
        if (!(stored instanceof Map<?, ?> map)) {
            return new State(null, Set.of());
        }
        var watermark = map.get("watermark") != null ? Instant.parse(map.get("watermark").toString()) : null;
        var ids = map.get("ids") instanceof List<?> list ? new HashSet<>((List<String>) list) : new HashSet<String>();
        return new State(watermark, ids);
    }

    private static String activityKey(Map<String, Object> activity) {
        var id = activity.get("summaryId") != null ? activity.get("summaryId") : activity.get("activityId");
        return String.valueOf(id);
    }

    private static String watermarkKey(TriggerContext context) {
        var flowId = context.getFlowId();
        return "garmin_activities_" + flowId.length() + "_" + flowId + "_" + context.getTriggerId();
    }

    private record State(Instant watermark, Set<String> deliveredIds) {
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Number of new activities"
        )
        private final Integer size;

        @Schema(
            title = "New activities",
            description = "The activity summaries uploaded since the last poll, as returned by the Garmin API."
        )
        private final List<Map<String, Object>> activities;
    }
}
