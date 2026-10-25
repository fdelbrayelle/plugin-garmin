package io.kestra.plugin.garmin.health;

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
    title = "List sleep summaries",
    description = """
        Fetches the Garmin Health API sleep summaries (duration, sleep levels, scores) uploaded in a time range."""
)
@Plugin(
    examples = {
        @Example(
            title = "List sleep summaries",
            full = true,
            code = """
                id: garmin_sleeps
                namespace: company.team

                tasks:
                  - id: sleeps
                    type: io.kestra.plugin.garmin.health.Sleeps
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
public class Sleeps extends AbstractListTask {
    @Override
    protected String path() {
        return "/wellness-api/rest/sleeps";
    }
}
