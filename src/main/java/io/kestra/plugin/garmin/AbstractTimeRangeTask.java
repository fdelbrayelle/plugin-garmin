package io.kestra.plugin.garmin;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractTimeRangeTask extends AbstractGarminTask {
    @Schema(
        title = "Start of the time range",
        description = """
            ISO-8601 instant, for example `2025-01-31T00:00:00Z`. Garmin pull requests filter on the time the data was
            uploaded to Garmin Connect and accept ranges of at most 24 hours."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Instant> startTime;

    @Schema(
        title = "End of the time range",
        description = "ISO-8601 instant. Defaults to the current time."
    )
    @PluginProperty(group = "main")
    private Property<Instant> endTime;

    protected Map<String, String> timeRangeQuery(RunContext runContext) throws IllegalVariableEvaluationException {
        var rStart = runContext.render(startTime).as(Instant.class)
            .orElseThrow(() -> new IllegalArgumentException("'startTime' is required, for example 2025-01-31T00:00:00Z"));
        var rEnd = runContext.render(endTime).as(Instant.class).orElseGet(Instant::now);
        if (!rEnd.isAfter(rStart)) {
            throw new IllegalArgumentException("'endTime' (" + rEnd + ") must be after 'startTime' (" + rStart + ")");
        }
        return timeRangeQuery(rStart, rEnd);
    }

    public static Map<String, String> timeRangeQuery(Instant start, Instant end) {
        return Map.of(
            "uploadStartTimeInSeconds", String.valueOf(start.getEpochSecond()),
            "uploadEndTimeInSeconds", String.valueOf(end.getEpochSecond())
        );
    }
}
