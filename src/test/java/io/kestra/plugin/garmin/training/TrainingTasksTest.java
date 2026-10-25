package io.kestra.plugin.garmin.training;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.garmin.MockGarminServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class TrainingTasksTest {
    private static final Map<String, Object> WORKOUT = Map.of("workoutName", "Easy 5k", "sport", "RUNNING");

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void createWorkout() throws Exception {
        try (var server = new MockGarminServer().reply("POST", "/training-api/workout", 200, "{\"workoutId\":123456}")) {
            var output = CreateWorkout.builder()
                .id(IdUtils.create()).type(CreateWorkout.class.getName())
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("k")).consumerSecret(Property.ofValue("s"))
                .accessToken(Property.ofValue("t")).accessTokenSecret(Property.ofValue("ts"))
                .workout(Property.ofValue(WORKOUT))
                .build().run(runContextFactory.of(Map.of()));

            assertThat(output.getWorkoutId(), is("123456"));
            var request = server.lastRequest();
            assertThat(request.method(), is("POST"));
            assertThat(request.headers().getFirst("Content-Type"), containsString("application/json"));
            assertThat(request.body(), containsString("\"workoutName\":\"Easy 5k\""));
        }
    }

    @Test
    void createWorkoutFailsWithoutAnId() throws Exception {
        try (var server = new MockGarminServer().reply("POST", "/training-api/workout", 200, "{}")) {
            var task = CreateWorkout.builder()
                .id(IdUtils.create()).type(CreateWorkout.class.getName())
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("k")).consumerSecret(Property.ofValue("s"))
                .accessToken(Property.ofValue("t")).accessTokenSecret(Property.ofValue("ts"))
                .workout(Property.ofValue(WORKOUT))
                .build();

            var exception = assertThrows(IllegalStateException.class, () -> task.run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("workoutId"));
        }
    }

    @Test
    void createWorkoutReportsForbidden() throws Exception {
        try (var server = new MockGarminServer().reply("POST", "/training-api/workout", 403, "{\"errorMessage\":\"nope\"}")) {
            var task = CreateWorkout.builder()
                .id(IdUtils.create()).type(CreateWorkout.class.getName())
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("k")).consumerSecret(Property.ofValue("s"))
                .accessToken(Property.ofValue("t")).accessTokenSecret(Property.ofValue("ts"))
                .workout(Property.ofValue(WORKOUT))
                .build();

            var exception = assertThrows(Exception.class, () -> task.run(runContextFactory.of(Map.of())));

            assertThat(exception.getMessage(), containsString("HTTP 403"));
        }
    }

    @Test
    void updateWorkout() throws Exception {
        try (var server = new MockGarminServer().reply("PUT", "/training-api/workout/123456", 204, new byte[0], Map.of())) {
            var output = UpdateWorkout.builder()
                .id(IdUtils.create()).type(UpdateWorkout.class.getName())
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("k")).consumerSecret(Property.ofValue("s"))
                .accessToken(Property.ofValue("t")).accessTokenSecret(Property.ofValue("ts"))
                .workoutId(Property.ofValue("123456"))
                .workout(Property.ofValue(WORKOUT))
                .build().run(runContextFactory.of(Map.of()));

            assertThat(output, is(nullValue()));
            assertThat(server.lastRequest().method(), is("PUT"));
            assertThat(server.lastRequest().body(), containsString("\"sport\":\"RUNNING\""));
        }
    }

    @Test
    void deleteWorkout() throws Exception {
        try (var server = new MockGarminServer().reply("DELETE", "/training-api/workout/123456", 204, new byte[0], Map.of())) {
            var output = DeleteWorkout.builder()
                .id(IdUtils.create()).type(DeleteWorkout.class.getName())
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("k")).consumerSecret(Property.ofValue("s"))
                .accessToken(Property.ofValue("t")).accessTokenSecret(Property.ofValue("ts"))
                .workoutId(Property.ofValue("123456"))
                .build().run(runContextFactory.of(Map.of()));

            assertThat(output, is(nullValue()));
            assertThat(server.lastRequest().method(), is("DELETE"));
        }
    }

    @Test
    void deleteWorkoutEncodesTheIdentifierInThePath() throws Exception {
        try (var server = new MockGarminServer().reply("DELETE", "/training-api/workout/..%2Fcourses", 204, new byte[0], Map.of())) {
            DeleteWorkout.builder()
                .id(IdUtils.create()).type(DeleteWorkout.class.getName())
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("k")).consumerSecret(Property.ofValue("s"))
                .accessToken(Property.ofValue("t")).accessTokenSecret(Property.ofValue("ts"))
                .workoutId(Property.ofValue("../courses"))
                .build().run(runContextFactory.of(Map.of()));

            assertThat(server.lastRequest().uri().getRawPath(), is("/training-api/workout/..%2Fcourses"));
        }
    }

    @Test
    void createCourse() throws Exception {
        try (var server = new MockGarminServer().reply("POST", "/training-api/courses/v1/course", 200, "{\"courseId\":\"c-9\"}")) {
            var output = CreateCourse.builder()
                .id(IdUtils.create()).type(CreateCourse.class.getName())
                .baseUrl(Property.ofValue(server.url()))
                .consumerKey(Property.ofValue("k")).consumerSecret(Property.ofValue("s"))
                .accessToken(Property.ofValue("t")).accessTokenSecret(Property.ofValue("ts"))
                .course(Property.ofValue(Map.of("courseName", "Sunday loop", "activityType", "ROAD_CYCLING")))
                .build().run(runContextFactory.of(Map.of()));

            assertThat(output.getCourseId(), is("c-9"));
            assertThat(server.lastRequest().body(), containsString("\"courseName\":\"Sunday loop\""));
        }
    }
}
