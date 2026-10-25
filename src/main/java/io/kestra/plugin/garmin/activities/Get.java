package io.kestra.plugin.garmin.activities;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.garmin.AbstractTimeRangeTask;
import io.kestra.plugin.garmin.GarminClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get the details of an activity",
    description = """
        Fetches the Garmin Activity API details (summary, samples and laps) of a single activity.
        The Garmin pull API has no lookup by identifier, so the activity is searched in the details uploaded in the
        given time range: use a range that contains the activity upload time, for example the one reported by the
        `activities.List` task or the `activities.Trigger`."""
)
@Plugin(
    examples = {
        @Example(
            title = "Get the details of an activity uploaded on a given day",
            full = true,
            code = """
                id: garmin_get_activity
                namespace: company.team

                tasks:
                  - id: get_activity
                    type: io.kestra.plugin.garmin.activities.Get
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                    activityId: "9480532130"
                    startTime: "2025-01-31T00:00:00Z"
                    endTime: "2025-01-31T23:59:59Z"
                """
        )
    }
)
public class Get extends AbstractTimeRangeTask implements RunnableTask<Get.Output> {
    private static final String PATH = "/wellness-api/rest/activityDetails";

    @Schema(
        title = "Activity identifier",
        description = "The `summaryId` (or `activityId`) of the activity, as returned by the Garmin API."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> activityId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rActivityId = runContext.render(activityId).as(String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("'activityId' is required"));
        var query = timeRangeQuery(runContext);

        try (var client = GarminClient.create(runContext, this)) {
            var activity = client.getList(PATH, query).stream()
                .filter(a -> rActivityId.equals(String.valueOf(a.get("summaryId"))) || rActivityId.equals(String.valueOf(a.get("activityId"))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Activity '" + rActivityId + "' was not found in the details uploaded in the requested time range. Widen 'startTime'/'endTime' to include its upload time."));
            return Output.builder().activity(activity).build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Activity details",
            description = "The activity details as returned by the Garmin API."
        )
        private final Map<String, Object> activity;
    }
}
