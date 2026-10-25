package io.kestra.plugin.garmin.activities;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.garmin.AbstractGarminTask;
import io.kestra.plugin.garmin.GarminClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download an activity file",
    description = """
        Downloads the original activity file (FIT, GPX or TCX, depending on what Garmin stores for the activity)
        and stores it in Kestra internal storage."""
)
@Plugin(
    examples = {
        @Example(
            title = "Download the file of an activity",
            full = true,
            code = """
                id: garmin_download_activity
                namespace: company.team

                tasks:
                  - id: download_activity
                    type: io.kestra.plugin.garmin.activities.Download
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                    activityId: "9480532130"
                """
        )
    }
)
public class Download extends AbstractGarminTask implements RunnableTask<Download.Output> {
    private static final String PATH = "/wellness-api/rest/activityFile";
    private static final Pattern EXTENSION = Pattern.compile("\\.(fit|gpx|tcx)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9_-]");

    @Schema(
        title = "Activity identifier",
        description = "The `summaryId` of the activity, as returned by the Garmin API."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> activityId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rActivityId = runContext.render(activityId).as(String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("'activityId' is required"));
        var result = new AtomicReference<Output>();

        try (var client = GarminClient.create(runContext, this)) {
            client.download(PATH, Map.of("id", rActivityId), response -> {
                try {
                    var format = detectFormat(response);
                    var fileName = UNSAFE_CHARS.matcher(rActivityId).replaceAll("_") + "." + format.toLowerCase(Locale.ROOT);
                    var uri = runContext.storage().putFile(response.getBody(), fileName);
                    result.set(Output.builder().uri(uri).format(format).build());
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to store the downloaded Garmin activity file", e);
                }
            });
        }

        var output = result.get();
        if (output == null) {
            throw new IllegalStateException("Garmin returned no file for activity '" + rActivityId + "'");
        }
        runContext.logger().info("Stored Garmin {} file for activity '{}' at {}", output.getFormat(), rActivityId, output.getUri());
        return output;
    }

    private static String detectFormat(HttpResponse<InputStream> response) {
        var disposition = response.getHeaders().firstValue("Content-Disposition").orElse("");
        var matcher = EXTENSION.matcher(disposition);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        var contentType = response.getHeaders().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (contentType.contains("gpx")) {
            return "GPX";
        }
        if (contentType.contains("tcx")) {
            return "TCX";
        }
        return "FIT";
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Activity file URI",
            description = "The Kestra internal storage URI of the downloaded file."
        )
        private final URI uri;

        @Schema(
            title = "File format",
            description = "The detected format of the file: `FIT`, `GPX` or `TCX`. Defaults to `FIT` when it cannot be detected from the response."
        )
        private final String format;
    }
}
