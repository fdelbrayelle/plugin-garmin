package io.kestra.plugin.garmin;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractGarminTask extends Task implements GarminConnection {
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
}
