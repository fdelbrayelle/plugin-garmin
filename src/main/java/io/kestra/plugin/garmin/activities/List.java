package io.kestra.plugin.garmin.activities;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.plugin.garmin.AbstractListTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List activity summaries",
    description = """
        Fetches the Garmin Activity API summaries (type, duration, distance, calories, heart rate) uploaded in a time range."""
)
@Plugin(
    examples = {
        @Example(
            title = "List activity summaries",
            full = true,
            code = """
                id: garmin_list_activities
                namespace: company.team

                tasks:
                  - id: list_activities
                    type: io.kestra.plugin.garmin.activities.List
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                    startTime: "2025-01-31T00:00:00Z"
                    endTime: "2025-01-31T23:59:59Z"
                """
        )
    }
)
public class List extends AbstractListTask {
    @Override
    protected String path() {
        return "/wellness-api/rest/activities";
    }
}
