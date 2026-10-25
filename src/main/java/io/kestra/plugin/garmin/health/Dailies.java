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
    title = "List daily health summaries",
    description = """
        Fetches the Garmin Health API daily summaries (steps, distance, calories, heart rate, intensity minutes) uploaded in a time range."""
)
@Plugin(
    examples = {
        @Example(
            title = "List daily health summaries",
            full = true,
            code = """
                id: garmin_dailies
                namespace: company.team

                tasks:
                  - id: dailies
                    type: io.kestra.plugin.garmin.health.Dailies
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
public class Dailies extends AbstractListTask {
    @Override
    protected String path() {
        return "/wellness-api/rest/dailies";
    }
}
