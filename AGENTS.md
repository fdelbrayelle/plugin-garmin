# Kestra Garmin Plugin

## What

- Plugin for the Garmin Connect Developer Program cloud APIs (Activity, Health, Training, Courses), under `io.kestra.plugin.garmin`.

## Why

- Lets Kestra flows read Garmin activities and health metrics and publish workouts and courses.

## How

### Architecture

Single-module plugin, root package `io.kestra.plugin.garmin` holds only shared code; tasks and triggers live in sub-packages.

- `garmin`: `GarminConnection` (connection properties contract), `AbstractGarminTask`, `AbstractTimeRangeTask`, `AbstractListTask` (time-range list tasks with `fetchType`), `GarminClient` (Kestra HTTP client + signing, created per run) and `OAuth1Signer` (OAuth 1.0a HMAC-SHA1).
- `garmin.activities`: `List`, `Get`, `Download`, `Trigger` (polling, watermark and delivered ids in the namespace KV Store).
- `garmin.health`: `Dailies`, `Sleeps`, `Stress`, `BodyCompositions`.
- `garmin.training`: `CreateWorkout`, `UpdateWorkout`, `DeleteWorkout`, `CreateCourse`.

The task and trigger connection properties are declared twice (`AbstractGarminTask` and `activities.Trigger`) because a trigger cannot extend a task; keep them in sync.

### Project Structure

```
plugin-garmin/
├── src/main/java/io/kestra/plugin/garmin/{activities,health,training}/
├── src/test/java/io/kestra/plugin/garmin/   (MockGarminServer: local JDK HTTP server, no Garmin sandbox exists)
├── build.gradle
└── README.md
```

## Local rules

- Tests run against `MockGarminServer`; endpoint paths and payloads are based on Garmin's public descriptions and could not be verified against the gated API specification.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://developer.garmin.com/gc-developer-program/
