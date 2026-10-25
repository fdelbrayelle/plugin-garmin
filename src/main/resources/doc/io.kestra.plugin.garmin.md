# How to use the Garmin plugin

Read activities and all-day health metrics, and publish workouts and courses, with the [Garmin Connect Developer Program](https://developer.garmin.com/gc-developer-program/) cloud APIs (Activity, Health, Training and Courses).

## Authentication

Every task and trigger signs its requests with OAuth 1.0a (HMAC-SHA1), using the credentials of your Garmin Connect developer app and of the Garmin user who granted access:

- `consumerKey`, `consumerSecret`: the app credentials (secrets).
- `accessToken`, `accessTokenSecret`: the user credentials obtained during the Garmin consent flow (secrets).
- `baseUrl`: optional, defaults to `https://apis.garmin.com`.
- `timeout`: optional, defaults to 30 seconds.

Store the four credentials as [secrets](https://kestra.io/docs/concepts/secret) and set them once for all tasks with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults). Access to the Garmin APIs is granted per developer app by Garmin; a `403` usually means the app is not enabled for the API or the user did not grant the permission.

## Tasks

### Activities (`io.kestra.plugin.garmin.activities`)

- `List`: activity summaries uploaded in a time range.
- `Get`: details of one activity (`activityId`), searched in the details uploaded in the time range.
- `Download`: the original activity file (FIT, GPX or TCX) stored in internal storage; output `uri` and `format`.

### Health (`io.kestra.plugin.garmin.health`)

`Dailies`, `Sleeps`, `Stress` and `BodyCompositions` list the matching Health API summaries uploaded in a time range.

### Training (`io.kestra.plugin.garmin.training`)

- `CreateWorkout`, `UpdateWorkout`, `DeleteWorkout`: manage workouts; `workout` is the JSON body defined by the Garmin Training API.
- `CreateCourse`: publish a course; `course` is the JSON body defined by the Garmin Courses API.

Time-range tasks (`List`, `Get`, `Dailies`, `Sleeps`, `Stress`, `BodyCompositions`) take `startTime` (required) and `endTime` (defaults to now). Garmin pull requests filter on the **upload time** to Garmin Connect, not on the activity time, and accept at most 24 hours per request. List-style tasks support `fetchType` (`FETCH`, `FETCH_ONE`, `STORE`, `NONE`).

## Triggers

- `activities.Trigger`: polls the Activity API every `interval` (default 15 minutes) and starts an execution when new activities were uploaded. It uses polling rather than Garmin Ping/Push, which needs a public endpoint registered in the Garmin developer portal. The last polled time and delivered activity identifiers are kept in the namespace KV Store so nothing is delivered twice. Outputs: `size` and `activities`.
