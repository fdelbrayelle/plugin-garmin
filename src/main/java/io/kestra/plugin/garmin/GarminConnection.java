package io.kestra.plugin.garmin;

import io.kestra.core.models.property.Property;

import java.time.Duration;

public interface GarminConnection {
    String DEFAULT_BASE_URL = "https://apis.garmin.com";

    Property<String> getBaseUrl();

    Property<String> getConsumerKey();

    Property<String> getConsumerSecret();

    Property<String> getAccessToken();

    Property<String> getAccessTokenSecret();

    Property<Duration> getTimeout();
}
